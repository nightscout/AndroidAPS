package app.aaps.pump.omnipod.common.bledriver.comm.legacy.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.DataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.IncomingPackets
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.session.STOP_CONNECTING_CHECK_INTERVAL_MS
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.DisconnectHandler
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionEstablisher
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionNegotiationResynchronization
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import java.util.concurrent.CountDownLatch

class Connection(
    private val podDevice: BluetoothDevice,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val context: Context,
    private val podState: OmnipodDashPodStateManager
) : BleConnection, DisconnectHandler {

    private val incomingPackets = IncomingPackets()
    private val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets, this)
    private var gattConnection: BluetoothGatt? = null

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    private var _connectionWaitCond: ConnectionWaitCondition? = null

    @Volatile
    override var session: Session? = null

    @Volatile
    override var msgIO: MessageIO? = null

    @Synchronized
    override fun connect(connectionWaitCond: ConnectionWaitCondition) {
        aapsLogger.debug("Connecting connectionWaitCond=$connectionWaitCond")
        _connectionWaitCond = connectionWaitCond
        podState.connectionAttempts++
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
        val autoConnect = false
        var gatt = gattConnection
        if (gatt == null) {
            gatt = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                Thread.sleep(SLEEP_WHEN_FAILING_TO_CONNECT_GATT) // Do not retry too often
                throw FailedToConnectException("connectGatt() returned null")
            }
            gattConnection = gatt
        } else if (!gatt.connect()) {
            throw FailedToConnectException("connect() returned false")
        }
        val before = SystemClock.elapsedRealtime()
        if (waitForConnection(connectionWaitCond) !is Connected) {
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
            _connectionWaitCond = null
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
            discovered.getValue(CharacteristicType.CMD),
            incomingPackets.cmdQueue,
            gatt,
            bleCommCallbacks
        )
        val dataBleIO = DataBleIO(
            aapsLogger,
            discovered.getValue(CharacteristicType.DATA),
            incomingPackets.dataQueue,
            gatt,
            bleCommCallbacks
        )
        msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)
        //  val ret = gattConnection.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        // aapsLogger.info(LTag.PUMPBTCOMM, "requestConnectionPriority: $ret")
        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
        _connectionWaitCond = null
    }

    @Synchronized
    override fun disconnect(closeGatt: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnecting closeGatt=$closeGatt")
        if (!closeGatt && gattConnection != null) {
            // Disconnect first, then close gatt
            gattConnection?.disconnect()
            // Set connection state to DISCONNECTED immediately to prevent reconnection attempts
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        } else {
            // Call with closeGatt=true only when ble is already disconnected or there is no connection
            gattConnection?.close()
            bleCommCallbacks.resetConnection()
            gattConnection = null
            session = null
            msgIO = null
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        }
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
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for connection")
        }
        return connectionState()
    }

    override fun connectionState(): ConnectionState {
        val connectionState = bluetoothManager?.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return NotConnected
        }
        return Connected
    }

    override fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
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
                val enDecrypt = EnDecrypt(aapsLogger, keys.nonce, keys.ck)
                session = Session(aapsLogger, mIO, ids, sessionKeys = keys, enDecrypt = enDecrypt)
                null
            }
        }
    }

    // This will be called from a different thread !!!
    override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Lost connection with status: $status")
        // Check if waiting for connection, if so, stop waiting
        _connectionWaitCond?.stopConnection?.let {
            if (it.count > 0) {
                it.countDown()
            }
        }
        // BLE disconnected, so need to close gatt
        disconnect(true)
    }

    companion object {
        const val MIN_DISCOVERY_TIMEOUT_MS = 10000L
        const val MAX_WAIT_FOR_CONNECTION_SECONDS = Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS + 10
        const val SLEEP_WHEN_FAILING_TO_CONNECT_GATT = 10000L
    }
}
