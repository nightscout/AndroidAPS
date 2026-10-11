package app.aaps.pump.omnipod.omnipod5.bledriver.comm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.SharedBleManager
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoActivationTimeResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoTriggeredAlertsResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy.O5BleConnectionFactory
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5CertificateStore
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5LTKExchanger
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.command.aid.O5AidSetupCommands
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.security.SecureO5RegistrationStorage
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.PodTypeAwarePodScanner
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@ContributesBinding(AppScope::class, binding = binding<O5BleManager>())
@SingleIn(AppScope::class)
class O5BleManagerImpl @Inject constructor(
    aapsLogger: AAPSLogger,
    private val podState: O5PodStateManager,
    private val config: Config,
    private val context: Context,
    private val bleConnectionFactory: O5BleConnectionFactory,
    bleDeviceManager: BleDeviceManager,
    secureO5RegistrationStorage: SecureO5RegistrationStorage,
    private val p256KeyGenerator: P256KeyGenerator
) : SharedBleManager(aapsLogger, bleDeviceManager), O5BleManager {

    init {
        secureO5RegistrationStorage.loadAndInstallAll()
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    override val bluetoothAddress: String?
        get() = podState.bluetoothAddress
    override val ltk: ByteArray?
        get() = podState.ltk
    override val ids: Ids
        get() {
            val controllerId = podState.controllerId
                ?: throw FailedToConnectException("Missing controllerId, activate the O5 pod first")
            val podId = podState.podId
                ?: throw FailedToConnectException("Missing podId, activate the O5 pod first")
            return Ids.forController(Id.fromLong(controllerId), Id.fromLong(podId))
        }
    @Volatile override var isActivationRunning: Boolean = false
    override val releaseBusyBeforeCompletion = true
    override val connectionName = "O5 pod"

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

    override fun onCommandSent() {
        podState.increaseMessageSequenceNumber()
    }

    override fun onResponseRead() {
        podState.increaseMessageSequenceNumber()
    }

    override fun onResponse(response: Response) {
        when (response) {
            is VersionResponse                -> podState.updateFromVersionResponse(response)
            is DefaultStatusResponse           -> podState.updateFromDefaultStatusResponse(response)
            is AlarmStatusResponse             -> podState.updateFromAlarmStatusResponse(response)
            is SetUniqueIdResponse             -> podState.updateFromSetUniqueIdResponse(response)
            is PodInfoActivationTimeResponse   -> podState.updateFromActivationTimeResponse(response)
            is PodInfoTriggeredAlertsResponse  -> podState.updateFromTriggeredAlertsResponse(response)
            else                               -> Unit
        }
    }

    override fun pairNewPod(): Observable<PodEvent> = Observable.create { emitter ->
        acquireBusy()
        try {
            if (podState.ltk == null && podState.bluetoothAddress != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, "Forgetting saved O5 address ${podState.bluetoothAddress} of an unpaired pod")
                podState.bluetoothAddress = null
            }
            var lastError: Exception? = null
            for (attempt in 0..1) {
                try {
                    pairNewPodAttempt(emitter)
                    emitter.onComplete()
                    return@create
                } catch (ex: Exception) {
                    lastError = ex
                    disconnect(true)
                    if (attempt == 0 && podState.podId != null && !podState.isPodKaput) Thread.sleep(3_000) else break
                }
            }
            throw requireNotNull(lastError)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "O5 pod activation failed", ex)
            disconnect(true)
            emitter.tryOnError(ex)
        } finally {
            releaseBusy()
        }
    }

    private fun pairNewPodAttempt(emitter: ObservableEmitter<PodEvent>) {
        if (podState.ltk != null) {
            emitter.onNext(PodEvent.AlreadyPaired)
            val address = podState.bluetoothAddress
                ?: throw FailedToConnectException("Missing bluetoothAddress, activate the pod first")
            emitter.onNext(PodEvent.BluetoothConnecting)
            val conn = createConnection(address)
            connection = conn
            conn.connect(ConnectionWaitCondition(timeoutMs = BleConnection.DEFAULT_CONNECT_TIMEOUT_MS))
            emitter.onNext(PodEvent.BluetoothConnected(address))
            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(podState.msgSequenceNumber)
            emitter.onNext(PodEvent.Connected)
            return
        }

        val controllerId = podState.controllerId
            ?.takeIf(O5RegistrationData::contains)
            ?: O5RegistrationData.pickControllerId
        if (controllerId == 0L) {
            throw PairingException("No O5 registration data available")
        }
        val certStore = O5CertificateStore(aapsLogger, p256KeyGenerator, controllerId)
        val address = podState.bluetoothAddress ?: run {
            val adapter = bluetoothAdapter ?: throw ConnectException("Bluetooth not available")
            emitter.onNext(PodEvent.Scanning)
            PodTypeAwarePodScanner(aapsLogger, adapter).scanForPod(PodType.OMNIPOD_5).address
                .also { podState.bluetoothAddress = it }
        }
        val podIdLong = podState.podId
            ?.takeIf { podState.controllerId == controllerId }
            ?: podState.nextPodId
                ?.takeIf { O5IdRotation.controllerIdForPodId(it) == O5IdRotation.controllerIdForPodId(controllerId) }
            ?: O5IdRotation.firstPodId(controllerId)
        podState.controllerId = controllerId
        podState.podId = podIdLong
        podState.nextPodId = null

        emitter.onNext(PodEvent.BluetoothConnecting)
        val conn = bleConnectionFactory.createConnection(address, controllerId)
        connection = conn
        conn.connect(ConnectionWaitCondition(timeoutMs = BleConnection.DEFAULT_CONNECT_TIMEOUT_MS))
        emitter.onNext(PodEvent.BluetoothConnected(address))
        emitter.onNext(PodEvent.Pairing)
        val messageIO = conn.msgIO ?: throw ConnectException("Connection lost")
        val myId = Id.fromLong(certStore.controllerId)
        val podId = Id.fromLong(podIdLong)
        val pairResult = O5LTKExchanger(aapsLogger, messageIO, certStore, myId, podId).o5NegotiateLTK()
        emitter.onNext(PodEvent.Paired(podId))
        podState.updateFromPairing(certStore.controllerId, podId.toLong(), pairResult)
        if (config.DEBUG) aapsLogger.info(LTag.PUMPCOMM, "Got O5 LTK: ${pairResult.ltk.toHex()}")
        emitter.onNext(PodEvent.EstablishingSession)
        establishSession(pairResult.msgSeq)
        emitter.onNext(PodEvent.Connected)
    }

    override fun sendAidSetupCommands(): Completable = Completable.fromAction {
        acquireBusy()
        try {
            O5AidSetupCommands.send(assertSessionEstablished())
        } catch (ex: Exception) {
            disconnect(false)
            throw ex
        } finally {
            releaseBusy()
        }
    }
}
