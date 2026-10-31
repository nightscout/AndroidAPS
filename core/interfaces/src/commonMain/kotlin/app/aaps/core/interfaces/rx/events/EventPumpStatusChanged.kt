package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs

/**
 * Fired when the pump connection status changes.
 */
class EventPumpStatusChanged : EventStatus {

    /**
     * Represents the different states of the pump connection.
     */
    enum class Status {

        /** Pump is trying to connect. */
        CONNECTING,

        /** Pump is connected. */
        CONNECTED,

        /** Pump is performing initial communication handshake. */
        HANDSHAKING,

        /** Pump is performing an action. */
        PERFORMING,

        /** AAPS is waiting before disconnecting the pump. */
        WAITING_FOR_DISCONNECTION,

        /** Pump is disconnecting. */
        DISCONNECTING,

        /** Pump is disconnected. */
        DISCONNECTED
    }

    /** The current status of the pump connection. */
    var status: Status = Status.DISCONNECTED

    /** The number of seconds elapsed in the current state. */
    var secondsElapsed = 0
    private var performingAction = ""

    /** An error message, if any. */
    var error = ""

    /**
     * Creates a new event with the given status.
     * @param status The pump connection status.
     */
    constructor(status: Status) {
        this.status = status
        secondsElapsed = 0
        error = ""
    }

    /**
     * Creates a new event with the given status and elapsed time.
     * @param status The pump connection status.
     * @param secondsElapsed The number of seconds elapsed in this state.
     */
    constructor(status: Status, secondsElapsed: Int) {
        this.status = status
        this.secondsElapsed = secondsElapsed
        error = ""
    }

    /**
     * Creates a new event with the given status and error message.
     * @param status The pump connection status.
     * @param error The error message.
     */
    constructor(status: Status, error: String) {
        this.status = status
        secondsElapsed = 0
        this.error = error
    }

    /**
     * Creates a new event for a performing action. The status is automatically set to [Status.PERFORMING].
     * @param action The action being performed.
     */
    constructor(action: String) {
        status = Status.PERFORMING
        secondsElapsed = 0
        performingAction = action
    }

    /**
     * Gets a human-readable status message for the startup wizard.
     */
    override fun getStatus(): TextRef {
        return when (status) {
            Status.CONNECTING                -> InterfacesStrings.connecting_for.withArgs(secondsElapsed)
            Status.HANDSHAKING               -> InterfacesStrings.handshaking
            Status.CONNECTED                 -> InterfacesStrings.connected
            Status.PERFORMING                -> TextRef.Literal(performingAction)
            Status.WAITING_FOR_DISCONNECTION -> InterfacesStrings.waiting_for_disconnection
            Status.DISCONNECTING             -> InterfacesStrings.disconnecting
            Status.DISCONNECTED              -> TextRef.Literal("")
        }
    }
}