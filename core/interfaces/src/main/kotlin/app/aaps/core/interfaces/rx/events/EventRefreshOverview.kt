package app.aaps.core.interfaces.rx.events

/**
 * Fired to refresh the overview screen.
 *
 * @param from A string indicating the source of the refresh request.
 * @param now If true, the refresh should happen immediately.
 */
class EventRefreshOverview(var from: String, val now: Boolean = false) : Event()