package app.aaps.core.main.events

import app.aaps.interfaces.notifications.Notification
import app.aaps.interfaces.rx.events.Event

class EventNewNotification(var notification: Notification) : Event()
