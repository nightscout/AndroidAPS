package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification

class EventNewNotification(var notification: Notification) : Event()
