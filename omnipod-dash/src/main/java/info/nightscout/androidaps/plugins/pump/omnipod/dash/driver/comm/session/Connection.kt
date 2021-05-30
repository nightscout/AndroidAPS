package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import info.nightscout.androidaps.extensions.toHex
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Ids
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ServiceDiscoverer
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.EnDecrypt
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleSendSuccess
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CmdBleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.DataBleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.IncomingPackets
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager

sealed class ConnectionState

object Connected : ConnectionState()
object NotConnected : ConnectionState()

class Connection(
    private val podDevice: BluetoothDevice,
    private val aapsLogger: AAPSLogger,
    context: Context,
    private val podState: OmnipodDashPodStateManager
) : DisconnectHandler {

    private val incomingPackets = IncomingPackets()
    private val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets, this)
    private val gattConnection: BluetoothGatt

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    // The session is Synchronized because we can lose the connection right when establishing it
    var session: Session? = null
        @Synchronized get
        @Synchronized set
    private val cmdBleIO: CmdBleIO
    private val dataBleIO: DataBleIO

    init {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting to ${podDevice.address}")

        val autoConnect = false
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
        gattConnection = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
        // OnDisconnect can be called after this point!!!
        val state = waitForConnection()
        if (state !is Connected) {
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
            throw FailedToConnectException(podDevice.address)
        }
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED
        val discoverer = ServiceDiscoverer(aapsLogger, gattConnection, bleCommCallbacks)
        val discoveredCharacteristics = discoverer.discoverServices()
        cmdBleIO = CmdBleIO(
            aapsLogger,
            discoveredCharacteristics[CharacteristicType.CMD]!!,
            incomingPackets
                .cmdQueue,
            gattConnection,
            bleCommCallbacks
        )
        dataBleIO = DataBleIO(
            aapsLogger,
            discoveredCharacteristics[CharacteristicType.DATA]!!,
            incomingPackets
                .dataQueue,
            gattConnection,
            bleCommCallbacks
        )
        val sendResult = cmdBleIO.hello()
        if (sendResult !is BleSendSuccess) {
            throw FailedToConnectException("Could not send HELLO command to ${podDevice.address}")
        }
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
    }

    val msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)

    fun connect() {
        if (session != null) {
            disconnect()
        }
        aapsLogger.debug("Connecting")
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING

        if (!gattConnection.connect()) {
            throw FailedToConnectException("connect() returned false")
        }

        if (waitForConnection() !is Connected) {
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
            throw FailedToConnectException(podDevice.address)
        }
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED


        val discoverer = ServiceDiscoverer(aapsLogger, gattConnection, bleCommCallbacks)
        val discovered = discoverer.discoverServices()
        dataBleIO.characteristic = discovered[CharacteristicType.DATA]!!
        cmdBleIO.characteristic = discovered[CharacteristicType.CMD]!!

        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
    }

    fun disconnect() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnecting")
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED

        gattConnection.disconnect()
        bleCommCallbacks.resetConnection()
        session = null
    }

    private fun waitForConnection(): ConnectionState {
        try {
            bleCommCallbacks.waitForConnection(CONNECT_TIMEOUT_MS)
        } catch (e: InterruptedException) {
            // We are still going to check if connection was successful
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for connection")
        }
        return connectionState()
    }

    fun connectionState(): ConnectionState {
        val connectionState = bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return NotConnected
        }
        return Connected
    }

    fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
        val eapAkaExchanger = SessionEstablisher(aapsLogger, msgIO, ltk, eapSqn, ids, msgSeq)
        return when (val keys = eapAkaExchanger.negotiateSessionKeys()) {
            is SessionNegotiationResynchronization -> {
                if (BuildConfig.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "EAP AKA resynchronization: ${keys.synchronizedEapSqn}")
                }
                keys.synchronizedEapSqn
            }

            is SessionKeys -> {
                if (BuildConfig.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "CK: ${keys.ck.toHex()}")
                    aapsLogger.info(LTag.PUMPCOMM, "msgSequenceNumber: ${keys.msgSequenceNumber}")
                    aapsLogger.info(LTag.PUMPCOMM, "Nonce: ${keys.nonce}")
                }
                val enDecrypt = EnDecrypt(
                    aapsLogger,
                    keys.nonce,
                    keys.ck
                )
                session = Session(aapsLogger, msgIO, ids, sessionKeys = keys, enDecrypt = enDecrypt)
                null
            }
        }
    }

    // This will be called from a different thread !!!
    override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Lost connection with status: $status")
        disconnect()
    }

    companion object {

        private const val CONNECT_TIMEOUT_MS = 12000
    }
}
