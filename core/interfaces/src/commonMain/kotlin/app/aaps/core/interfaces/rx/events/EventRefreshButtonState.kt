package app.aaps.core.interfaces.rx.events

/**
 * Fired to refresh the state of a button.
 *
 * @param newState The new state of the button (e.g., enabled/disabled).
 */
data class EventRefreshButtonState(val newState: Boolean) : Event()