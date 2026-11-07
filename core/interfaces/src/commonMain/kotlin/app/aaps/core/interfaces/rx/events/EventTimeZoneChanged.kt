package app.aaps.core.interfaces.rx.events

/**
 * Fired when the time zone of the phone has changed.
 *
 * Calculations that turn a past timestamp into a time of day (the basal profile, and with it the
 * basal part of IOB) use the current time zone, so their cached results are no longer valid.
 */
class EventTimeZoneChanged : Event()
