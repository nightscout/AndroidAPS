package app.aaps.core.interfaces.rx.events

/**
 * Fired when the scale of a graph is changed.
 *
 * @param hours The new number of hours to display on the graph.
 */
class EventScale(val hours: Int) : Event()