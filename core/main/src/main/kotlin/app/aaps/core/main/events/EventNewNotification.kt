package app.aaps.core.main.events

import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.rx.events.Event

class EventNewNotification(var notification: Notification) : Event()
