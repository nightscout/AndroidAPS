package app.aaps.pump.omnipod.dash.driver.comm

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.BusyException
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.CouldNotSendCommandException
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.NotConnectedException
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.SessionEstablishmentException
import app.aaps.pump.omnipod.dash.driver.comm.pair.LTKExchanger
import app.aaps.pump.omnipod.dash.driver.comm.scan.PodScanner
import app.aaps.pump.omnipod.dash.driver.comm.session.CommandAckError
import app.aaps.pump.omnipod.dash.driver.comm.session.CommandReceiveError
import app.aaps.pump.omnipod.dash.driver.comm.session.CommandReceiveSuccess
import app.aaps.pump.omnipod.dash.driver.comm.session.CommandSendErrorConfirming
import app.aaps.pump.omnipod.dash.driver.comm.session.CommandSendErrorSending
import app.aaps.pump.omnipod.dash.driver.comm.session.CommandSendSuccess
import app.aaps.pump.omnipod.dash.driver.comm.session.Connected
import app.aaps.pump.omnipod.dash.driver.comm.session.Connection
import app.aaps.pump.omnipod.dash.driver.comm.session.ConnectionState
import app.aaps.pump.omnipod.dash.driver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.dash.driver.comm.session.NotConnected
import app.aaps.pump.omnipod.dash.driver.comm.session.Session
import app.aaps.pump.omnipod.dash.driver.event.PodEvent
import app.aaps.pump.omnipod.dash.driver.pod.command.base.Command
import app.aaps.pump.omnipod.dash.driver.pod.response.Response
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.keys.DashBooleanPreferenceKey
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class OmnipodDashBleManagerImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val podState: OmnipodDashPodStateManager,
    private val config: Config,
    private val preferences: Preferences,
) : OmnipodDashBleManager {

    private val busy = AtomicBoolean(false)
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var connection: Connection? = null
    private val ids = Ids(podState)

    override fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent> =
        Observable.create { emitter ->
            if (!busy.compareAndSet(false, true)) {
                throw BusyException()
            }
            try {
                val session = assertSessionEstablished()

                emitter.onNext(PodEvent.CommandSending(cmd))
                /*
                    if (Random.nextBoolean()) {
                        // XXX use this to test "failed to confirm" commands
                        emitter.onNext(PodEvent.CommandSendNotConfirmed(cmd))
                        emitter.tryOnError(MessageIOException("XXX random failure to test unconfirmed commands"))
                        return@create
                    }
    */
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
                /*
                if (Random.nextBoolean()) {
                    // XXX use this commands confirmed with success
                    emitter.tryOnError(MessageIOException("XXX random failure to test unconfirmed commands"))
                    return@create
                }*/
                when (val readResult = session.readAndAckResponse()) {
                    is CommandReceiveSuccess ->
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))

                    is CommandAckError       ->
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))

                    is CommandReceiveError   -> {
                        emitter.tryOnError(MessageIOException("Could not read response: $readResult"))
                        return@create
                    }
                }
                emitter.onComplete()
            } catch (ex: Exception) {
                disconnect(false)
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

    override fun getStatus(): ConnectionState {
        return connection?.connectionState()
            ?: NotConnected
    }

    // used for sync connections
    override fun connect(timeoutMs: Long): Observable<PodEvent> {
        return connect(ConnectionWaitCondition(timeoutMs = timeoutMs))
    }

    // used for async connections
    override fun connect(stopConnectionLatch: CountDownLatch): Observable<PodEvent> {
        return connect(ConnectionWaitCondition(stopConnection = stopConnectionLatch))
    }

    private fun connect(connectionWaitCond: ConnectionWaitCondition): Observable<PodEvent> = Observable
        .create { emitter ->
            if (!busy.compareAndSet(false, true)) {
                throw BusyException()
            }
            try {
                emitter.onNext(PodEvent.BluetoothConnecting)

                val podAddress =
                    podState.bluetoothAddress
                        ?: throw FailedToConnectException("Missing bluetoothAddress, activate the pod first")
                val podDevice = bluetoothAdapter?.getRemoteDevice(podAddress)
                    ?: throw ConnectException("Bluetooth not available")

                if (podDevice.bondState == BluetoothDevice.BOND_NONE && preferences.get(DashBooleanPreferenceKey.UseBonding)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        val result = podDevice.createBond()
                        aapsLogger.debug(LTag.PUMPBTCOMM, "Bonding with pod resulted $result")
                        Thread.sleep(10000)
                    }
                }

                val conn = connection
                    ?: Connection(podDevice, aapsLogger, config, context, podState)
                connection = conn
                if (conn.connectionState() is Connected && conn.session != null) {
                    emitter.onNext(PodEvent.AlreadyConnected(podAddress))
                    emitter.onComplete()
                    return@create
                }

                conn.connect(connectionWaitCond)

                emitter.onNext(PodEvent.BluetoothConnected(podAddress))
                emitter.onNext(PodEvent.EstablishingSession)
                establishSession(1.toByte())
                emitter.onNext(PodEvent.Connected)

                emitter.onComplete()
            } catch (ex: Exception) {
                disconnect(false)
                emitter.tryOnError(ex)
            } finally {
                busy.set(false)
            }
        }

    private fun establishSession(msgSeq: Byte) {
        val conn = assertConnected()

        val ltk = assertPaired()

        val eapSqn = podState.increaseEapAkaSequenceNumber()

        var newSqn = conn.establishSession(ltk, msgSeq, ids, eapSqn)

        if (newSqn != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Updating EAP SQN to: $newSqn")
            podState.eapAkaSequenceNumber = newSqn.toLong()
            newSqn = conn.establishSession(ltk, msgSeq, ids, podState.increaseEapAkaSequenceNumber())
            if (newSqn != null) {
                throw SessionEstablishmentException("Received resynchronization SQN for the second time")
            }
        }
        podState.successfulConnections++
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
            val adapter = bluetoothAdapter
                ?: throw ConnectException("Bluetooth not available")
            val podScanner = PodScanner(aapsLogger, adapter)
            val podAddress = podScanner.scanForPod(
                PodScanner.SCAN_FOR_SERVICE_UUID,
                PodScanner.POD_ID_NOT_ACTIVATED
            ).scanResult.device.address
            podState.bluetoothAddress = podAddress

            emitter.onNext(PodEvent.BluetoothConnecting)
            val podDevice = adapter.getRemoteDevice(podAddress)
            val conn = Connection(podDevice, aapsLogger, config, context, podState)
            connection = conn
            conn.connect(ConnectionWaitCondition(timeoutMs = 3 * Connection.BASE_CONNECT_TIMEOUT_MS))
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.Pairing)
            val mIO = conn.msgIO ?: throw ConnectException("Connection lost")
            val ltkExchanger = LTKExchanger(
                aapsLogger,
                config,
                mIO,
                ids,
            )
            val pairResult = ltkExchanger.negotiateLTK()
            emitter.onNext(PodEvent.Paired(ids.podId))
            podState.updateFromPairing(ids.podId, pairResult)
            if (config.DEBUG) {
                aapsLogger.info(LTag.PUMPCOMM, "Got LTK: ${pairResult.ltk.toHex()}")
            }
            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(pairResult.msgSeq)
            podState.successfulConnections++
            emitter.onNext(PodEvent.Connected)
            emitter.onComplete()
        } catch (ex: Exception) {
            disconnect(false)
            emitter.tryOnError(ex)
        } finally {
            busy.set(false)
        }
    }

    override fun disconnect(closeGatt: Boolean) {
        connection?.disconnect(closeGatt)
            ?: aapsLogger.info(LTag.PUMPBTCOMM, "Trying to disconnect a null connection")
    }

    override fun removeBond() {
        try {
            if (preferences.get(DashBooleanPreferenceKey.UseBonding) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            ) {
                val device = bluetoothAdapter?.getRemoteDevice(podState.bluetoothAddress) ?: throw IllegalStateException("MAC address not found")
                // At time of writing (2021-12-06), the removeBond method
                // is inexplicably still marked with @hide, so we must use
                // reflection to get to it and unpair this device.
                val removeBondMethod = device.javaClass.getMethod("removeBond")
                val result = removeBondMethod.invoke(device)
                aapsLogger.debug(LTag.PUMPBTCOMM, "Remove bond resulted $result")
            }
        } catch (t: Throwable) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Unpairing device with address ${podState.bluetoothAddress} failed with error $t")
        }
    }

    companion object {

        const val CONTROLLER_ID = 4242 // TODO read from preferences or somewhere else.
    }
}
