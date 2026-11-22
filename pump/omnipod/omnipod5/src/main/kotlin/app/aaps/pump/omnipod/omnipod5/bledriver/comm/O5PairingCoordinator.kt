package app.aaps.pump.omnipod.omnipod5.bledriver.comm
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy.O5BleConnectionFactory
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5CertificateStore
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5LTKExchanger
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.PodTypeAwarePodScanner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The O5 pairing entry point: scans for a pairable Omnipod 5 pod, connects to it, runs
 * [O5LTKExchanger]'s pairing message sequence, then establishes the post-pairing encrypted
 * session - parallel to [OmnipodDashBleManagerImpl.pairNewPod] rather than a modification
 * of it.
 *
 * Requires real registration data to already be installed in [O5RegistrationData] (see
 * that class's doc comment) - without it, [pairNewPod] fails immediately with a
 * [PairingException] rather than attempting a pairing that could never succeed.
 */
@SingleIn(AppScope::class)
class O5PairingCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val context: Context,
    private val podState: O5PodStateManager,
    private val bleConnectionFactory: O5BleConnectionFactory,
    private val p256KeyGenerator: P256KeyGenerator
) {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    /**
     * Pairs a new Omnipod 5 pod and establishes the initial encrypted session with it.
     * Returns the negotiated [PairResult] on success.
     *
     * @throws PairingException if no O5 registration data is available, or if pairing fails.
     * @throws ConnectException if the BLE connection is lost partway through.
     */
    fun pairNewPod(): PairResult {
        aapsLogger.info(LTag.PUMPBTCOMM, "Starting new O5 pod activation")

        val adapter = bluetoothAdapter ?: throw ConnectException("Bluetooth not available")

        val controllerId = O5RegistrationData.pickControllerId
        if (controllerId == 0L) {
            throw PairingException(
                "No O5 registration data available - cannot pair an Omnipod 5 pod without " +
                    "real Insulet-issued controller credentials (see O5RegistrationData)"
            )
        }
        val certStore = O5CertificateStore(aapsLogger, p256KeyGenerator, controllerId)

        val scanner = PodTypeAwarePodScanner(aapsLogger, adapter)
        val discovered = scanner.scanForPod(PodType.OMNIPOD_5)
        podState.bluetoothAddress = discovered.address
        aapsLogger.info(LTag.PUMPBTCOMM, "Found O5 pod at ${discovered.address}, connecting")

        val conn = bleConnectionFactory.createConnection(discovered.address, controllerId)
        conn.connect(ConnectionWaitCondition(timeoutMs = BleConnection.DEFAULT_CONNECT_TIMEOUT_MS))

        val mIO = conn.msgIO ?: throw ConnectException("Connection lost before pairing could start")

        val myId = Id.fromLong(certStore.controllerId)
        val podId = myId.increment()

        aapsLogger.info(LTag.PUMPBTCOMM, "Pairing with predicted podId=$podId, myId=$myId")
        val ltkExchanger = O5LTKExchanger(aapsLogger, mIO, certStore, myId, podId)
        val pairResult = ltkExchanger.o5NegotiateLTK()

        podState.updateFromPairing(certStore.controllerId, podId.toLong(), pairResult)
        aapsLogger.info(LTag.PUMPBTCOMM, "O5 pairing succeeded, establishing session")

        val ids = Ids.forController(myId, podId)
        val eapSqn = podState.increaseEapAkaSequenceNumber()
        val resyncSqn = conn.establishSession(pairResult.ltk, pairResult.msgSeq, ids, eapSqn)
        if (resyncSqn != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "O5 EAP AKA resynchronization on first connect: $resyncSqn")
            podState.eapAkaSequenceNumber = resyncSqn.toLong()
            val secondAttemptSqn = conn.establishSession(
                pairResult.ltk, pairResult.msgSeq, ids, podState.increaseEapAkaSequenceNumber()
            )
            if (secondAttemptSqn != null) {
                throw PairingException("Received EAP AKA resynchronization for the second time")
            }
        }
        podState.commitEapAkaSequenceNumber()
        podState.successfulConnections++

        aapsLogger.info(LTag.PUMPBTCOMM, "O5 pod paired and session established")
        return pairResult
    }
}
