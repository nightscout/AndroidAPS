package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandHello
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.EnDecrypt
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.LTKExchanger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.PodScanner
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.Session
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.SessionEstablisher
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.SessionKeys
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.status.ConnectionStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.utils.extensions.toHex
import io.reactivex.Observable
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashBleManagerImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val podState: OmnipodDashPodStateManager
) : OmnipodDashBleManager {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var sessionKeys: SessionKeys? = null
    private var msgIO: MessageIO? = null
    private var gatt: BluetoothGatt? = null
    private var status: ConnectionStatus = ConnectionStatus.IDLE
    private val myId = Id.fromInt(CONTROLLER_ID)
    private val uniqueId = podState.uniqueId
    private val podId = uniqueId?.let(Id::fromLong)
        ?: myId.increment() // pod not activated

    @Throws(
        FailedToConnectException::class,
        CouldNotSendBleException::class,
        InterruptedException::class,
        BleIOBusyException::class,
        TimeoutException::class,
        CouldNotConfirmWriteException::class,
        CouldNotEnableNotifications::class,
        DescriptorNotFoundException::class,
        CouldNotConfirmDescriptorWriteException::class
    )

    private fun connect(podDevice: BluetoothDevice): BleIO {
        val incomingPackets: Map<CharacteristicType, BlockingQueue<ByteArray>> =
            mapOf(
                CharacteristicType.CMD to LinkedBlockingDeque(),
                CharacteristicType.DATA to LinkedBlockingDeque()
            )
        val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting to ${podDevice.address}")
        val autoConnect = false // TODO: check what to use here

        val gattConnection = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
        bleCommCallbacks.waitForConnection(CONNECT_TIMEOUT_MS)
        val connectionState = bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            throw FailedToConnectException(podDevice.address)
        }
        val discoverer = ServiceDiscoverer(aapsLogger, gattConnection, bleCommCallbacks)
        val chars = discoverer.discoverServices()
        val bleIO = BleIO(aapsLogger, chars, incomingPackets, gattConnection, bleCommCallbacks)
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandHello(CONTROLLER_ID).data)
        bleIO.readyToRead()
        gatt = gattConnection
        return bleIO
    }

    override fun sendCommand(cmd: Command): Observable<PodEvent> = Observable.create { emitter ->
        try {
            val keys = sessionKeys
            val mIO = msgIO
            if (keys == null || mIO == null) {
                throw Exception("Not connected")
            }
            emitter.onNext(PodEvent.CommandSending(cmd))
            // TODO switch to RX
            emitter.onNext(PodEvent.CommandSent(cmd))

            val enDecrypt = EnDecrypt(
                aapsLogger,
                keys.nonce,
                keys.ck
            )

            val session = Session(
                aapsLogger = aapsLogger,
                msgIO = mIO,
                myId = myId,
                podId = podId,
                sessionKeys = keys,
                enDecrypt = enDecrypt
            )
            val response = session.sendCommand(cmd)
            emitter.onNext(PodEvent.ResponseReceived(response))

            emitter.onComplete()
        } catch (ex: Exception) {
            emitter.tryOnError(ex)
        }
    }

    override fun getStatus(): ConnectionStatus {
        var s: ConnectionStatus
        synchronized(status) {
            s = status
        }
        return s
    }

    @Throws(
        InterruptedException::class,
        ScanFailException::class,
        FailedToConnectException::class,
        CouldNotSendBleException::class,
        BleIOBusyException::class,
        TimeoutException::class,
        CouldNotConfirmWriteException::class,
        CouldNotEnableNotifications::class,
        DescriptorNotFoundException::class,
        CouldNotConfirmDescriptorWriteException::class
    )

    override fun connect(): Observable<PodEvent> = Observable.create { emitter ->
        try {

            val podAddress =
                podState.bluetoothAddress
                    ?: throw FailedToConnectException("Missing bluetoothAddress, activate the pod first")
            // check if already connected
            val podDevice = bluetoothAdapter.getRemoteDevice(podAddress)
            val connectionState = bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT)
            aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
            if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                emitter.onNext(PodEvent.AlreadyConnected(podAddress))
                emitter.onComplete()
                return@create
            }

            emitter.onNext(PodEvent.BluetoothConnecting)
            if (msgIO != null) {
                disconnect()
            }
            val bleIO = connect(podDevice)
            val mIO = MessageIO(aapsLogger, bleIO)
            msgIO = mIO
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(1.toByte())
            emitter.onNext(PodEvent.Connected)

            emitter.onComplete()
        } catch (ex: Exception) {
            disconnect()
            emitter.tryOnError(ex)
        }
    }

    private fun establishSession(msgSeq: Byte) {
        val mIO = msgIO ?: throw FailedToConnectException("connection lost")
        val ltk: ByteArray = podState.ltk ?: throw FailedToConnectException("Missing LTK, activate the pod first")
        val uniqueId = podState.uniqueId
        val podId = uniqueId?.let { Id.fromLong(uniqueId) }
            ?: myId.increment() // pod not activated

        val eapSqn = podState.increaseEapAkaSequenceNumber()
        val eapAkaExchanger = SessionEstablisher(aapsLogger, mIO, ltk, eapSqn, myId, podId, msgSeq)
        val keys = eapAkaExchanger.negotiateSessionKeys()
        podState.commitEapAkaSequenceNumber()

        if (BuildConfig.DEBUG) {
            aapsLogger.info(LTag.PUMPCOMM, "CK: ${keys.ck.toHex()}")
            aapsLogger.info(LTag.PUMPCOMM, "msgSequenceNumber: ${keys.msgSequenceNumber}")
            aapsLogger.info(LTag.PUMPCOMM, "Nonce: ${keys.nonce}")
        }
        sessionKeys = keys
    }

    override fun pairNewPod(): Observable<PodEvent> = Observable.create { emitter ->
        try {

            if (podState.ltk != null) {
                emitter.onNext(PodEvent.AlreadyPaired)
                emitter.onComplete()
                return@create
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "starting new pod activation")

            emitter.onNext(PodEvent.Scanning)
            val podScanner = PodScanner(aapsLogger, bluetoothAdapter)
            val podAddress = podScanner.scanForPod(
                PodScanner.SCAN_FOR_SERVICE_UUID,
                PodScanner.POD_ID_NOT_ACTIVATED
            ).scanResult.device.address
            podState.bluetoothAddress = podAddress

            emitter.onNext(PodEvent.BluetoothConnecting)
            val podDevice = bluetoothAdapter.getRemoteDevice(podAddress)
            val bleIO = connect(podDevice)
            val mIO = MessageIO(aapsLogger, bleIO)
            msgIO = mIO
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.Pairing)
            val ltkExchanger = LTKExchanger(aapsLogger, mIO, myId, podId, Id.fromLong(PodScanner.POD_ID_NOT_ACTIVATED))
            val pairResult = ltkExchanger.negotiateLTK()
            emitter.onNext(PodEvent.Paired(podId))
            podState.updateFromPairing(podId, pairResult)
            if (BuildConfig.DEBUG) {
                aapsLogger.info(LTag.PUMPCOMM, "Got LTK: ${pairResult.ltk.toHex()}")
            }

            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(pairResult.msgSeq)
            emitter.onNext(PodEvent.Connected)
            emitter.onComplete()
        } catch (ex: Exception) {
            disconnect()
            emitter.tryOnError(ex)
        }
    }

    override fun disconnect() {
        val localGatt = gatt
        localGatt?.close() // TODO: use disconnect?
        gatt = null
        msgIO = null
        sessionKeys = null
    }

    companion object {

        private const val CONNECT_TIMEOUT_MS = 7000
        const val CONTROLLER_ID = 4242 // TODO read from preferences or somewhere else.
    }
}
