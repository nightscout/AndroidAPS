package app.aaps.pump.omnipod.common.bledriver.comm.legacy.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.utils.extensions.connectGattCompat
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager

class Connection(
    podDevice: BluetoothDevice,
    aapsLogger: AAPSLogger,
    config: Config,
    context: Context,
    private val podState: OmnipodDashPodStateManager
) : SharedBleConnection(podDevice, aapsLogger, config, context) {

    override val podType = PodType.DASH

    override fun recordConnectionAttempt() {
        podState.connectionAttempts++
    }

    override fun updateConnectionState(state: LifecycleState) {
        podState.bluetoothConnectionState = when (state) {
            LifecycleState.CONNECTING    -> OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
            LifecycleState.CONNECTED     -> OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED
            LifecycleState.DISCONNECTED  -> OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        }
    }

    override fun connectGatt(): BluetoothGatt? =
        podDevice.connectGattCompat(context, false, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)

    override fun hello(cmdBleIO: CmdBleIO) {
        cmdBleIO.hello()
    }

    override fun createSession(messageIO: MessageIO, ids: Ids, keys: SessionKeys, enDecrypt: EnDecrypt): Session =
        Session(aapsLogger, messageIO, ids, sessionKeys = keys, enDecrypt = enDecrypt)
}
