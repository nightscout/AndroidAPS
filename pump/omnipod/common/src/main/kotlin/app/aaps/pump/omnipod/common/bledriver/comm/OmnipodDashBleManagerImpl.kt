package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.pair.LTKExchanger
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import io.reactivex.rxjava3.core.Observable
import dev.zacsweers.metro.Inject

@Inject
class OmnipodDashBleManagerImpl(
    aapsLogger: AAPSLogger,
    private val podState: OmnipodDashPodStateManager,
    private val config: Config,
    private val bleConnectionFactory: BleConnectionFactory,
    bleDeviceManager: BleDeviceManager,
) : SharedBleManager(aapsLogger, bleDeviceManager) {

    override val bluetoothAddress: String?
        get() = podState.bluetoothAddress
    override val ltk: ByteArray?
        get() = podState.ltk
    override val ids = Ids(podState)

    override fun createConnection(podAddress: String): BleConnection =
        bleConnectionFactory.createConnection(podAddress)

    override fun increaseEapAkaSequenceNumber(): ByteArray = podState.increaseEapAkaSequenceNumber()

    override fun updateEapAkaSequenceNumber(sequenceNumber: Long) {
        podState.eapAkaSequenceNumber = sequenceNumber
    }

    override fun commitEapAkaSequenceNumber() {
        podState.commitEapAkaSequenceNumber()
    }

    override fun recordSuccessfulConnection() {
        podState.successfulConnections++
    }

    override fun pairNewPod(): Observable<PodEvent> = Observable.create { emitter ->
        acquireBusy()
        try {
            if (podState.ltk != null) {
                emitter.onNext(PodEvent.AlreadyPaired)
                emitter.onComplete()
                return@create
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "Starting new pod activation")
            emitter.onNext(PodEvent.Scanning)
            val podAddress = bleDeviceManager.createPodScanner().scanForPod(
                PodScanner.SCAN_FOR_SERVICE_UUID,
                PodScanner.POD_ID_NOT_ACTIVATED
            ).address
            podState.bluetoothAddress = podAddress

            emitter.onNext(PodEvent.BluetoothConnecting)
            val conn = createConnection(podAddress)
            connection = conn
            conn.connect(ConnectionWaitCondition(timeoutMs = BleConnection.DEFAULT_CONNECT_TIMEOUT_MS))
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.Pairing)
            val messageIO = conn.msgIO ?: throw ConnectException("Connection lost")
            val pairResult = LTKExchanger(aapsLogger, config, messageIO, ids).negotiateLTK()
            emitter.onNext(PodEvent.Paired(ids.podId))
            podState.updateFromPairing(ids.podId, pairResult)
            if (config.DEBUG) aapsLogger.info(LTag.PUMPCOMM, "Got LTK: ${pairResult.ltk.toHex()}")
            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(pairResult.msgSeq)
            podState.successfulConnections++
            emitter.onNext(PodEvent.Connected)
            emitter.onComplete()
        } catch (ex: Exception) {
            disconnect(false)
            emitter.tryOnError(ex)
        } finally {
            releaseBusy()
        }
    }

    companion object {
        const val CONTROLLER_ID = 4242
    }
}
