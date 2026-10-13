package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.notifications.Notification

/**
 * Fired when a new notification is created.
 *
 * @param notification The new notification.
 */
class EventNewNotification(var notification: Notification) : Event()
