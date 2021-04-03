package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ServiceDiscoverer
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.EnDecrypt
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.SessionEstablishmentException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleSendSuccess
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CmdBleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.DataBleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.IncomingPackets
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.utils.extensions.toHex

sealed class ConnectionState

object Connected : ConnectionState()
object NotConnected : ConnectionState()

class Connection(val podDevice: BluetoothDevice, private val aapsLogger: AAPSLogger, context: Context) {

    private val incomingPackets = IncomingPackets()
    private val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets)
    private val gattConnection: BluetoothGatt

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    init {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting to ${podDevice.address}")

        val autoConnect = false
        gattConnection = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
        val state = waitForConnection()
        if (state !is Connected) {
            throw FailedToConnectException(podDevice.address)
        }
    }

    private val discoverer = ServiceDiscoverer(aapsLogger, gattConnection, bleCommCallbacks)
    private val discoveredCharacteristics = discoverer.discoverServices()
    private val cmdBleIO = CmdBleIO(
        aapsLogger,
        discoveredCharacteristics[CharacteristicType.CMD]!!,
        incomingPackets
            .cmdQueue,
        gattConnection,
        bleCommCallbacks
    )
    private val dataBleIO = DataBleIO(
        aapsLogger,
        discoveredCharacteristics[CharacteristicType.DATA]!!,
        incomingPackets
            .dataQueue,
        gattConnection,
        bleCommCallbacks
    )
    val msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)
    var session: Session? = null

    init {
        val sendResult = cmdBleIO.hello()
        if (sendResult !is BleSendSuccess) {
            throw FailedToConnectException("Could not send HELLO command to ${podDevice.address}")
        }
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
    }

    fun connect() {
        // forces reconnection
        disconnect()

        if (!gattConnection.connect()) {
            throw FailedToConnectException("connect() returned false")
        }

        if (waitForConnection() is NotConnected) {
            throw FailedToConnectException(podDevice.address)
        }
        discoverer.discoverServices()
        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
    }

    fun disconnect() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnecting")
        bleCommCallbacks.resetConnection()
        gattConnection.disconnect()
        session = null
    }

    private fun waitForConnection(): ConnectionState {
        try {
            bleCommCallbacks.waitForConnection(CONNECT_TIMEOUT_MS)
        } catch (e: InterruptedException) {
            // We are still going to check if connection was successful
            aapsLogger.info(LTag.PUMPBTCOMM, "Interruped while waiting for connection")
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

    fun establishSession(ltk: ByteArray, msgSeq: Byte, myId: Id, podID: Id, eapSqn: ByteArray): EapSqn? {
        var eapAkaExchanger = SessionEstablisher(aapsLogger, msgIO, ltk, eapSqn, myId, podID, msgSeq)
        var keys = eapAkaExchanger.negotiateSessionKeys()
        return when (keys) {
            is SessionNegotiationResynchronization ->
                keys.syncronizedEapSqn
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
                session = Session(aapsLogger, msgIO, myId, podID, sessionKeys = keys, enDecrypt = enDecrypt)
                null
            }
        }
    }

    companion object {

        private const val CONNECT_TIMEOUT_MS = 7000
    }
}
