package info.nightscout.core.events

import info.nightscout.interfaces.notifications.Notification
import info.nightscout.rx.events.Event

class EventNewNotification(var notification: Notification) : Event()
