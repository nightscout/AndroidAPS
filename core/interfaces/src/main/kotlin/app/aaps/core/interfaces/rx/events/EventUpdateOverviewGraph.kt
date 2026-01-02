package app.aaps.core.interfaces.rx.events

/**
 * Fired to update the overview graph.
 *
 * @param from A string indicating the source of the update request.
 */
class EventUpdateOverviewGraph(val from: String) : Event()