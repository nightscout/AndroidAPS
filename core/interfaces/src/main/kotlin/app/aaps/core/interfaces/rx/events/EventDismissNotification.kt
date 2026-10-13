package app.aaps.core.interfaces.rx.events

/**
 * Fired to dismiss a notification.
 *
 * @param id The ID of the notification to dismiss.
 */
class EventDismissNotification(var id: Int) : Event()
