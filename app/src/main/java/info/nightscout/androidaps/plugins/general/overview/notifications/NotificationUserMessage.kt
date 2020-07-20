package info.nightscout.androidaps.plugins.general.overview.notifications

class NotificationUserMessage  (text :String): Notification() {

    init {
        var hash = text.hashCode()
        if (hash < USERMESSAGE) hash += USERMESSAGE
        id = hash
        date = System.currentTimeMillis()
        this.text = text
        level = URGENT
    }
}