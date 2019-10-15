package info.nightscout.androidaps.events

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R

class EventPumpStatusChanged : EventStatus {

    enum class Status {
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        PERFORMING,
        DISCONNECTING,
        DISCONNECTED
    }

    var sStatus: Status = Status.DISCONNECTED
    var sSecondsElapsed = 0
    var sPerfomingAction = ""
    var error = ""

    constructor(status: Status) {
        sStatus = status
        sSecondsElapsed = 0
        error = ""
    }

    constructor(status: Status, secondsElapsed: Int) {
        sStatus = status
        sSecondsElapsed = secondsElapsed
        error = ""
    }

    constructor(status: Status, error: String) {
        sStatus = status
        sSecondsElapsed = 0
        this.error = error
    }

    constructor(action: String) {
        sStatus = Status.PERFORMING
        sSecondsElapsed = 0
        sPerfomingAction = action
    }

    // status for startup wizard
    override fun getStatus(): String {
        if (sStatus == Status.CONNECTING)
            return String.format(MainApp.gs(R.string.danar_history_connectingfor), sSecondsElapsed)
        else if (sStatus == Status.HANDSHAKING)
            return MainApp.gs(R.string.handshaking)
        else if (sStatus == Status.CONNECTED)
            return MainApp.gs(R.string.connected)
        else if (sStatus == Status.PERFORMING)
            return sPerfomingAction
        else if (sStatus == Status.DISCONNECTING)
            return MainApp.gs(R.string.disconnecting)
        else if (sStatus == Status.DISCONNECTED)
            return ""
        return ""
    }
}
