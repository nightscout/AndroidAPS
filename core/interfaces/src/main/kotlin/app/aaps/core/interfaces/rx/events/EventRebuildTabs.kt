package app.aaps.core.interfaces.rx.events

/**
 * Fired to signal that the tabs in the main UI should be rebuilt.
 *
 * @param recreate If true, the tabs should be completely recreated, not just updated.
 */
class EventRebuildTabs(var recreate: Boolean = false) : Event()