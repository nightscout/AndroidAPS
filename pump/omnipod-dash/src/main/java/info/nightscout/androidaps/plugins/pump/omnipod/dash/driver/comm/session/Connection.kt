package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Ids
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ServiceDiscoverer
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.EnDecrypt
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ConnectException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CmdBleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.DataBleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.IncomingPackets
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.core.utils.toHex
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.concurrent.CountDownLatch

sealed class ConnectionState

object Connecting : ConnectionState()
object Connected : ConnectionState()
object NotConnected : ConnectionState()

data class ConnectionWaitCondition(var timeoutMs: Long? = null, val stopConnection: CountDownLatch? = null) {
    init {
        if (timeoutMs == null && stopConnection == null) {
            throw IllegalArgumentException("One of timeoutMs or stopConnection has to be non null")
        }
        if (timeoutMs != null && stopConnection != null) {
            throw IllegalArgumentException("One of timeoutMs or stopConnection has to be null")
        }
    }
}

class Connection(
    private val podDevice: BluetoothDevice,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val context: Context,
    private val podState: OmnipodDashPodStateManager
) : DisconnectHandler {

    private val incomingPackets = IncomingPackets()
    private val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets, this)
    private var gattConnection: BluetoothGatt? = null

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    @Volatile
    var session: Session? = null

    @Volatile
    var msgIO: MessageIO? = null

    @Synchronized
    fun connect(connectionWaitCond: ConnectionWaitCondition) {
        aapsLogger.debug("Connecting connectionWaitCond=$connectionWaitCond")
        podState.connectionAttempts++
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
        val autoConnect = false
        val gatt = gattConnection ?: podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
        gattConnection = gatt
        if (gatt == null) {
            Thread.sleep(SLEEP_WHEN_FAILING_TO_CONNECT_GATT) // Do not retry too often
            throw FailedToConnectException("connectGatt() returned null")
        }
        if (!gatt.connect()) {
            throw FailedToConnectException("connect() returned false")
        }
        val before = SystemClock.elapsedRealtime()
        if (waitForConnection(connectionWaitCond) !is Connected) {
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
            throw FailedToConnectException(podDevice.address)
        }
        val waitedMs = SystemClock.elapsedRealtime() - before
        val timeoutMs = connectionWaitCond.timeoutMs
        if (timeoutMs != null) {
            var newTimeout = timeoutMs - waitedMs
            if (newTimeout < MIN_DISCOVERY_TIMEOUT_MS) {
                newTimeout = MIN_DISCOVERY_TIMEOUT_MS
            }
            connectionWaitCond.timeoutMs = newTimeout
        }
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED

        val discoverer = ServiceDiscoverer(aapsLogger, gatt, bleCommCallbacks, this)
        val discovered = discoverer.discoverServices(connectionWaitCond)
        val cmdBleIO = CmdBleIO(
            aapsLogger,
            discovered[CharacteristicType.CMD]!!,
            incomingPackets
                .cmdQueue,
            gatt,
            bleCommCallbacks
        )
        val dataBleIO = DataBleIO(
            aapsLogger,
            discovered[CharacteristicType.DATA]!!,
            incomingPackets
                .dataQueue,
            gatt,
            bleCommCallbacks
        )
        msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)
        //  val ret = gattConnection.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        // aapsLogger.info(LTag.PUMPBTCOMM, "requestConnectionPriority: $ret")
        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
    }

    @Synchronized
    fun disconnect(closeGatt: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnecting closeGatt=$closeGatt")
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        if (closeGatt) {
            gattConnection?.close()
            gattConnection = null
        } else {
            gattConnection?.disconnect()
        }
        bleCommCallbacks.resetConnection()
        session = null
        msgIO = null
    }

    private fun waitForConnection(connectionWaitCond: ConnectionWaitCondition): ConnectionState {
        aapsLogger.debug(LTag.PUMPBTCOMM, "waitForConnection connectionWaitCond=$connectionWaitCond")
        try {
            connectionWaitCond.timeoutMs?.let {
                bleCommCallbacks.waitForConnection(it)
            }
            val startWaiting = System.currentTimeMillis()
            connectionWaitCond.stopConnection?.let {
                while (!bleCommCallbacks.waitForConnection(STOP_CONNECTING_CHECK_INTERVAL_MS)) {
                    if (it.count == 0L) {
                        throw ConnectException("stopConnecting called")
                    }
                    val secondsElapsed = (System.currentTimeMillis() - startWaiting) / 1000
                    if (secondsElapsed > MAX_WAIT_FOR_CONNECTION_SECONDS) {
                        throw ConnectException("connection timeout")
                    }
                }
            }
        } catch (e: InterruptedException) {
            // We are still going to check if connection was successful
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for connection")
        }
        return connectionState()
    }

    fun connectionState(): ConnectionState {
        val connectionState = bluetoothManager?.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return NotConnected
        }
        return Connected
    }

    fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
        val mIO = msgIO ?: throw ConnectException("Connection lost")

        val eapAkaExchanger = SessionEstablisher(aapsLogger, config, mIO, ltk, eapSqn, ids, msgSeq)
        return when (val keys = eapAkaExchanger.negotiateSessionKeys()) {
            is SessionNegotiationResynchronization -> {
                if (config.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "EAP AKA resynchronization: ${keys.synchronizedEapSqn}")
                }
                keys.synchronizedEapSqn
            }

            is SessionKeys -> {
                if (config.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "CK: ${keys.ck.toHex()}")
                    aapsLogger.info(LTag.PUMPCOMM, "msgSequenceNumber: ${keys.msgSequenceNumber}")
                    aapsLogger.info(LTag.PUMPCOMM, "Nonce: ${keys.nonce}")
                }
                val enDecrypt = EnDecrypt(
                    aapsLogger,
                    keys.nonce,
                    keys.ck
                )
                session = Session(aapsLogger, mIO, ids, sessionKeys = keys, enDecrypt = enDecrypt)
                null
            }
        }
    }

    // This will be called from a different thread !!!
    override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Lost connection with status: $status")
        disconnect(false)
    }

    companion object {
        const val BASE_CONNECT_TIMEOUT_MS = 10000L
        const val MIN_DISCOVERY_TIMEOUT_MS = 10000L
        const val STOP_CONNECTING_CHECK_INTERVAL_MS = 500L
        const val MAX_WAIT_FOR_CONNECTION_SECONDS = Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS + 10
        const val SLEEP_WHEN_FAILING_TO_CONNECT_GATT = 10000L
    }
}
