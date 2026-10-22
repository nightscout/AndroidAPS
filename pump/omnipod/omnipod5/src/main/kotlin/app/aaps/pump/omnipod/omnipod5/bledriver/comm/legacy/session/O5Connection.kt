package app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.session.SharedBleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5CertificateStore
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator

class O5Connection(
    podDevice: BluetoothDevice,
    aapsLogger: AAPSLogger,
    config: Config,
    context: Context,
    private val podState: O5PodStateManager,
    private val p256KeyGenerator: P256KeyGenerator,
    private val pairingControllerId: Long? = null
) : SharedBleConnection(podDevice, aapsLogger, config, context) {

    override val podType = PodType.OMNIPOD_5

    override fun recordConnectionAttempt() {
        podState.connectionAttempts++
    }

    override fun updateConnectionState(state: LifecycleState) {
        podState.bluetoothConnectionState = when (state) {
            LifecycleState.CONNECTING    -> O5PodStateManager.BluetoothConnectionState.CONNECTING
            LifecycleState.CONNECTED     -> O5PodStateManager.BluetoothConnectionState.CONNECTED
            LifecycleState.DISCONNECTED  -> O5PodStateManager.BluetoothConnectionState.DISCONNECTED
        }
    }

    override fun connectGatt(): BluetoothGatt? =
        podDevice.connectGatt(context, false, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)

    override fun prepareGatt(gatt: BluetoothGatt) {
        if (!gatt.requestMtu(REQUESTED_MTU)) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "O5 requestMtu($REQUESTED_MTU) call returned false - continuing with default MTU")
            return
        }
        val completed = bleCommCallbacks.waitForMtuChange(MTU_NEGOTIATION_TIMEOUT_MS)
        val mtu = bleCommCallbacks.negotiatedMtu
        if (!completed) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "O5 MTU negotiation timed out - continuing with MTU=$mtu")
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "O5 MTU negotiated: $mtu")
        }
        if (mtu < MIN_REQUIRED_MTU) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "O5 negotiated MTU=$mtu is below the required $MIN_REQUIRED_MTU")
        }
    }

    override fun hello(cmdBleIO: CmdBleIO) {
        val controllerId = pairingControllerId ?: podState.controllerId
        if (controllerId == null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "O5 hello handshake has no controllerId; using the default")
            cmdBleIO.hello()
        } else {
            cmdBleIO.hello(controllerId.toInt())
        }
    }

    override fun createSession(messageIO: MessageIO, ids: Ids, keys: SessionKeys, enDecrypt: EnDecrypt): Session {
        val controllerId = requireNotNull(podState.controllerId) {
            "Missing controllerId, cannot establish a signed O5 session"
        }
        return Session(
            aapsLogger,
            messageIO,
            ids,
            sessionKeys = keys,
            enDecrypt = enDecrypt,
            commandSigner = O5CertificateStore(aapsLogger, p256KeyGenerator, controllerId)
        )
    }

    companion object {
        const val REQUESTED_MTU = 512
        const val MIN_REQUIRED_MTU = 247
        const val MTU_NEGOTIATION_TIMEOUT_MS = 5_000L
    }
}
