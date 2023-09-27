package app.aaps.core.interfaces.rx.events

import android.content.Context
import app.aaps.core.interfaces.R

class EventPumpStatusChanged : EventStatus {

    enum class Status {
        CONNECTING,
        CONNECTED,
        HANDSHAKING,
        PERFORMING,
        WAITING_FOR_DISCONNECTION,
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
    override fun getStatus(context: Context): String {
        return when (status) {
            Status.CONNECTING                -> context.getString(R.string.connecting_for, secondsElapsed)
            Status.HANDSHAKING               -> context.getString(R.string.handshaking)
            Status.CONNECTED                 -> context.getString(R.string.connected)
            Status.PERFORMING                -> performingAction
            Status.WAITING_FOR_DISCONNECTION -> context.getString(R.string.waiting_for_disconnection)
            Status.DISCONNECTING             -> context.getString(R.string.disconnecting)
            Status.DISCONNECTED              -> ""
        }
    }
}