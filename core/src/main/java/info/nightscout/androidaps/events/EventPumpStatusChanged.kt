package info.nightscout.androidaps.events

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.resources.ResourceHelper

class EventPumpStatusChanged : EventStatus {

    enum class Status {
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        PERFORMING,
        DISCONNECTING,
        DISCONNECTED
    }

    var status: Status = Status.DISCONNECTED
    var secondsElapsed = 0
    private var performingAction = ""
    var error = ""

    constructor(status: Status) {
        this.status = status
        secondsElapsed = 0
        error = ""
    }

    constructor(status: Status, secondsElapsed: Int) {
        this.status = status
        this.secondsElapsed = secondsElapsed
        error = ""
    }

    constructor(status: Status, error: String) {
        this.status = status
        secondsElapsed = 0
        this.error = error
    }

    constructor(action: String) {
        status = Status.PERFORMING
        secondsElapsed = 0
        performingAction = action
    }

    // status for startup wizard
    override fun getStatus(resourceHelper: ResourceHelper): String {
        return when (status) {
            Status.CONNECTING    -> String.format(resourceHelper.gs(R.string.connectingfor), secondsElapsed)
            Status.HANDSHAKING   -> resourceHelper.gs(R.string.handshaking)
            Status.CONNECTED     -> resourceHelper.gs(R.string.connected)
            Status.PERFORMING    -> performingAction
            Status.DISCONNECTING -> resourceHelper.gs(R.string.disconnecting)
            Status.DISCONNECTED  -> ""
        }
    }
}
