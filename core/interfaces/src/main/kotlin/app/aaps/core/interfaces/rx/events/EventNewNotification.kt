package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.notifications.Notification

class EventNewNotification(var notification: Notification) : Event()
