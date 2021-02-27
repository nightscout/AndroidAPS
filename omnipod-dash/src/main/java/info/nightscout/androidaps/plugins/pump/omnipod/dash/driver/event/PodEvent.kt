package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response

sealed class PodEvent {

    object Scanning : PodEvent()
    object Pairing : PodEvent()
    object Connecting : PodEvent()
    class Connected(val uniqueId: Int) : PodEvent()
    class CommandSending(val command: Command) : PodEvent()
    class CommandSent(val command: Command) : PodEvent()
    class ResponseReceived(val response: Response) : PodEvent()
}

