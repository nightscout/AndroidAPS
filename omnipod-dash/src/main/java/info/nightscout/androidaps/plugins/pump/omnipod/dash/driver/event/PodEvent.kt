package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response

sealed class PodEvent {

    /* BT connection events */
    class AlreadyConnected(val bluetoothAddress: String, val uniqueId: Long) : PodEvent()
    object Scanning : PodEvent()
    object BluetoothConnecting : PodEvent()
    class BluetoothConnected(val address: String) : PodEvent()
    object Pairing : PodEvent()
    object Paired : PodEvent()
    object EstablishingSession : PodEvent()
    class Connected(val uniqueId: Long) : PodEvent()

    /* Message exchange events */
    class CommandSending(val command: Command) : PodEvent()
    class CommandSent(val command: Command) : PodEvent()
    class ResponseReceived(val response: Response) : PodEvent()
}

