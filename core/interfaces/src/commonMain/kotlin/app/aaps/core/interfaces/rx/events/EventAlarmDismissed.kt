package app.aaps.core.interfaces.rx.events

/**
 * The alarm shown was dismissed away from its screen, with OK on the watch. The alarm screen closes on it, as
 * its own OK would. A plain mute leaves the screen, so the message can still be read.
 */
class EventAlarmDismissed : Event()
