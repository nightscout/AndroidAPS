package app.aaps.core.interfaces.rx.events

/**
 * Fired to update the setup wizard.
 *
 * @param redraw If true, forces a redraw of the wizard.
 */
class EventSWUpdate(var redraw: Boolean) : Event()