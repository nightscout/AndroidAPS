package app.aaps.core.interfaces.rx.events

/**
 * Fired to update the NTP status.
 *
 * @param status The status message.
 * @param percent The progress percentage.
 */
class EventNtpStatus(val status: String, val percent: Int) : Event()