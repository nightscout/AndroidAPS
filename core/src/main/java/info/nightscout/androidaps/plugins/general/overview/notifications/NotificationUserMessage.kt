package info.nightscout.androidaps.plugins.general.overview.notifications

class NotificationUserMessage  (text :String): Notification() {

    init {
        var hash = text.hashCode()
        if (hash < USER_MESSAGE) hash += USER_MESSAGE
        id = hash
        date = System.currentTimeMillis()
        this.text = text
        level = URGENT
    }
}