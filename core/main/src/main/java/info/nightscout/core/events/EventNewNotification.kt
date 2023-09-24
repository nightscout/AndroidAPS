package info.nightscout.core.events

import app.aaps.interfaces.notifications.Notification
import app.aaps.interfaces.rx.events.Event

class EventNewNotification(var notification: Notification) : Event()
