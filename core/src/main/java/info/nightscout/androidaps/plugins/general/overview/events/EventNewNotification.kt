package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.rx.events.Event

class EventNewNotification(var notification: Notification) : Event()
