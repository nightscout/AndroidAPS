package app.aaps.pump.omnipod.common.bledriver.comm.legacy.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
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
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.DisconnectHandler
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.STOP_CONNECTING_CHECK_INTERVAL_MS
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionEstablisher
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionNegotiationResynchronization

abstract class SharedBleConnection(
    protected val podDevice: BluetoothDevice,
    protected val aapsLogger: AAPSLogger,
    protected val config: Config,
    protected val context: Context
) : BleConnection, DisconnectHandler {

    protected abstract val connectionName: String
    protected val incomingPackets = IncomingPackets()
    protected val bleCommCallbacks by lazy { BleCommCallbacks(aapsLogger, incomingPackets, this) }
    protected var gattConnection: BluetoothGatt? = null
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private var connectionWaitCondition: ConnectionWaitCondition? = null

    @Volatile
    final override var session: Session? = null

    @Volatile
    final override var msgIO: MessageIO? = null

    protected abstract fun recordConnectionAttempt()
    protected abstract fun updateConnectionState(state: LifecycleState)
    protected abstract fun connectGatt(): BluetoothGatt?
    protected open fun prepareGatt(gatt: BluetoothGatt) = Unit
    protected abstract fun hello(cmdBleIO: CmdBleIO)
    protected abstract fun createSession(messageIO: MessageIO, ids: Ids, keys: SessionKeys, enDecrypt: EnDecrypt): Session
    protected open fun discoverServices(
        gatt: BluetoothGatt,
        connectionWaitCond: ConnectionWaitCondition
    ): Map<CharacteristicType, BluetoothGattCharacteristic> =
        ServiceDiscoverer(aapsLogger, gatt, bleCommCallbacks, this).discoverServices(connectionWaitCond)

    protected open fun createMessageIO(
        gatt: BluetoothGatt,
        discovered: Map<CharacteristicType, BluetoothGattCharacteristic>
    ): Pair<CmdBleIO, DataBleIO> {
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
        return cmdBleIO to dataBleIO
    }

    @Synchronized
    final override fun connect(connectionWaitCond: ConnectionWaitCondition) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting $connectionName connectionWaitCond=$connectionWaitCond")
        connectionWaitCondition = connectionWaitCond
        recordConnectionAttempt()
        updateConnectionState(LifecycleState.CONNECTING)
        var gatt = gattConnection
        if (gatt == null) {
            gatt = connectGatt()
            if (gatt == null) {
                Thread.sleep(SLEEP_WHEN_FAILING_TO_CONNECT_GATT)
                throw FailedToConnectException("connectGatt() returned null")
            }
            gattConnection = gatt
        } else if (!gatt.connect()) {
            throw FailedToConnectException("connect() returned false")
        }

        val before = SystemClock.elapsedRealtime()
        if (waitForConnection(connectionWaitCond) !is Connected) {
            updateConnectionState(LifecycleState.DISCONNECTED)
            connectionWaitCondition = null
            throw FailedToConnectException(podDevice.address)
        }
        connectionWaitCond.timeoutMs?.let { timeoutMs ->
            connectionWaitCond.timeoutMs = (timeoutMs - (SystemClock.elapsedRealtime() - before))
                .coerceAtLeast(MIN_DISCOVERY_TIMEOUT_MS)
        }
        updateConnectionState(LifecycleState.CONNECTED)

        val discovered = discoverServices(gatt, connectionWaitCond)
        prepareGatt(gatt)
        val (cmdBleIO, dataBleIO) = createMessageIO(gatt, discovered)
        msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)
        hello(cmdBleIO)
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
        connectionWaitCondition = null
    }

    @Synchronized
    final override fun disconnect(closeGatt: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnecting $connectionName closeGatt=$closeGatt")
        if (!closeGatt && gattConnection != null) {
            gattConnection?.disconnect()
            updateConnectionState(LifecycleState.DISCONNECTED)
        } else {
            gattConnection?.close()
            bleCommCallbacks.resetConnection()
            gattConnection = null
            session = null
            msgIO = null
            updateConnectionState(LifecycleState.DISCONNECTED)
        }
    }

    private fun waitForConnection(connectionWaitCond: ConnectionWaitCondition): ConnectionState {
        aapsLogger.debug(LTag.PUMPBTCOMM, "waitForConnection connectionWaitCond=$connectionWaitCond")
        try {
            connectionWaitCond.timeoutMs?.let(bleCommCallbacks::waitForConnection)
            val startWaiting = System.currentTimeMillis()
            connectionWaitCond.stopConnection?.let { stopConnection ->
                while (!bleCommCallbacks.waitForConnection(STOP_CONNECTING_CHECK_INTERVAL_MS)) {
                    if (stopConnection.count == 0L) throw ConnectException("stopConnecting called")
                    if ((System.currentTimeMillis() - startWaiting) / 1000 > MAX_WAIT_FOR_CONNECTION_SECONDS) {
                        throw ConnectException("connection timeout")
                    }
                }
            }
        } catch (_: InterruptedException) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for $connectionName connection")
        }
        return connectionState()
    }

    final override fun connectionState(): ConnectionState {
        val connectionState = bluetoothManager?.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        return if (connectionState == BluetoothProfile.STATE_CONNECTED) {
            Connected
        } else {
            NotConnected
        }
    }

    final override fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
        val messageIO = msgIO ?: throw ConnectException("Connection lost")
        return when (val keys = SessionEstablisher(aapsLogger, config, messageIO, ltk, eapSqn, ids, msgSeq).negotiateSessionKeys()) {
            is SessionNegotiationResynchronization -> {
                if (config.DEBUG) aapsLogger.info(LTag.PUMPCOMM, "EAP AKA resynchronization: ${keys.synchronizedEapSqn}")
                keys.synchronizedEapSqn
            }

            is SessionKeys                         -> {
                if (config.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "CK: ${keys.ck.toHex()}")
                    aapsLogger.info(LTag.PUMPCOMM, "msgSequenceNumber: ${keys.msgSequenceNumber}")
                    aapsLogger.info(LTag.PUMPCOMM, "Nonce: ${keys.nonce}")
                }
                val enDecrypt = EnDecrypt(aapsLogger, keys.nonce, keys.ck)
                session = createSession(messageIO, ids, keys, enDecrypt)
                null
            }
        }
    }

    final override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Lost $connectionName connection with status: $status")
        connectionWaitCondition?.stopConnection?.let {
            if (it.count > 0) it.countDown()
        }
        disconnect(true)
    }

    companion object {
        const val MIN_DISCOVERY_TIMEOUT_MS = 10_000L
        const val MAX_WAIT_FOR_CONNECTION_SECONDS = Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS + 10
        const val SLEEP_WHEN_FAILING_TO_CONNECT_GATT = 10_000L
    }

    protected enum class LifecycleState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }
}
