package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.LTKExchanger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.PodScanner
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.status.ConnectionStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.utils.extensions.toHex
import io.reactivex.Observable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class OmnipodDashBleManagerImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val podState: OmnipodDashPodStateManager
) : OmnipodDashBleManager {

    private val busy = AtomicBoolean(false)
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var connection: Connection? = null
    private var status: ConnectionStatus = ConnectionStatus.IDLE
    private val myId = Id.fromInt(CONTROLLER_ID)
    private val uniqueId = podState.uniqueId
    private val podId = uniqueId?.let(Id::fromLong)
        ?: myId.increment() // pod not activated

    override fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent> =
        Observable.create { emitter ->
            if (!busy.compareAndSet(false, true)) {
                throw BusyException()
            }
            try {
                val session = assertSessionEstablished()

                emitter.onNext(PodEvent.CommandSending(cmd))
                when (session.sendCommand(cmd)) {
                    is CommandSendErrorSending -> {
                        emitter.tryOnError(CouldNotSendCommandException())
                        return@create
                    }

                    is CommandSendSuccess ->
                        emitter.onNext(PodEvent.CommandSent(cmd))
                    is CommandSendErrorConfirming ->
                        emitter.onNext(PodEvent.CommandSendNotConfirmed(cmd))
                }

                when (val readResult = session.readAndAckResponse(responseType)) {
                    is CommandReceiveSuccess ->
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))

                    is CommandAckError ->
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))

                    is CommandReceiveError -> {
                        emitter.tryOnError(MessageIOException("Could not read response: $readResult"))
                        return@create
                    }
                }
                emitter.onComplete()
            } catch (ex: Exception) {
                disconnect()
                emitter.tryOnError(ex)
            } finally {
                busy.set(false)
            }
        }

    private fun assertSessionEstablished(): Session {
        val conn = assertConnected()
        return conn.session
            ?: throw NotConnectedException("Missing session")
   }

    override fun getStatus(): ConnectionStatus {
        // TODO is this used?
        var s: ConnectionStatus
        synchronized(status) {
            s = status
        }
        return s
    }

    override fun connect(): Observable<PodEvent> = Observable.create { emitter ->
        if (!busy.compareAndSet(false, true)) {
            throw BusyException()
        }
        try {
            emitter.onNext(PodEvent.BluetoothConnecting)

            val podAddress =
                podState.bluetoothAddress
                    ?: throw FailedToConnectException("Missing bluetoothAddress, activate the pod first")
            val podDevice = bluetoothAdapter.getRemoteDevice(podAddress)
            val conn = connection
                ?: Connection(podDevice, aapsLogger, context)
            connection = conn
            if (conn.connectionState() is Connected) {
                if (conn.session == null) {
                    emitter.onNext(PodEvent.EstablishingSession)
                    establishSession(1.toByte())
                    emitter.onNext(PodEvent.Connected)
                } else {
                    emitter.onNext(PodEvent.AlreadyConnected(podAddress))
                }
                emitter.onComplete()
                return@create
            }
            conn.connect()
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(1.toByte())
            emitter.onNext(PodEvent.Connected)

            emitter.onComplete()
        } catch (ex: Exception) {
            disconnect()
            emitter.tryOnError(ex)
        } finally {
            busy.set(false)
        }
    }

    private fun establishSession(msgSeq: Byte) {
        val conn = assertConnected()

        val ltk = assertPaired()

        val uniqueId = podState.uniqueId
        val podId = uniqueId?.let { Id.fromLong(uniqueId) }
            ?: myId.increment() // pod not activated

        var eapSqn = podState.increaseEapAkaSequenceNumber()

        var newSqn = conn.establishSession(ltk, msgSeq, myId, podId, eapSqn)

        if (newSqn != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Updating EAP SQN to: $newSqn")
            podState.eapAkaSequenceNumber = newSqn.toLong()
            newSqn = conn.establishSession(ltk, msgSeq, myId, podId, podState.increaseEapAkaSequenceNumber())
            if (newSqn != null) {
                throw SessionEstablishmentException("Received resynchronization SQN for the second time")
            }
        }

        podState.commitEapAkaSequenceNumber()
    }

    private fun assertPaired(): ByteArray {
        return podState.ltk
            ?: throw FailedToConnectException("Missing LTK, activate the pod first")
    }

    private fun assertConnected(): Connection {
        return connection
            ?: throw FailedToConnectException("connection lost")
    }

    override fun pairNewPod(): Observable<PodEvent> = Observable.create { emitter ->
        if (!busy.compareAndSet(false, true)) {
            throw BusyException()
        }
        try {

            if (podState.ltk != null) {
                emitter.onNext(PodEvent.AlreadyPaired)
                emitter.onComplete()
                return@create
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "Starting new pod activation")

            emitter.onNext(PodEvent.Scanning)
            val podScanner = PodScanner(aapsLogger, bluetoothAdapter)
            val podAddress = podScanner.scanForPod(
                PodScanner.SCAN_FOR_SERVICE_UUID,
                PodScanner.POD_ID_NOT_ACTIVATED
            ).scanResult.device.address
            podState.bluetoothAddress = podAddress

            emitter.onNext(PodEvent.BluetoothConnecting)
            val podDevice = bluetoothAdapter.getRemoteDevice(podAddress)
            val conn = Connection(podDevice, aapsLogger, context)
            connection = conn
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.Pairing)
            val ltkExchanger = LTKExchanger(
                aapsLogger,
                conn.msgIO,
                myId,
                podId,
                Id.fromLong(
                    PodScanner
                        .POD_ID_NOT_ACTIVATED
                )
            )
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
        } finally {
            busy.set(false)
        }
    }

    override fun disconnect() {
        connection?.disconnect()
            ?: aapsLogger.info(LTag.PUMPBTCOMM, "Trying to disconnect a null connection")
    }

    companion object {

        const val CONTROLLER_ID = 4242 // TODO read from preferences or somewhere else.
    }
}
