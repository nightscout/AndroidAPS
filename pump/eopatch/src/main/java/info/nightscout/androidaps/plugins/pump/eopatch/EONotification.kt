package info.nightscout.androidaps.plugins.pump.eopatch

import info.nightscout.interfaces.notifications.Notification
import info.nightscout.rx.logging.AAPSLogger
import javax.inject.Inject

class EONotification constructor() : Notification() {

    @Inject lateinit var aapsLogger: AAPSLogger

    constructor(id: Int, text: String, level: Int) : this() {
        this.id = id
        date = System.currentTimeMillis()
        this.text = text
        this.level = level
    }

    fun action(buttonText: Int, action: Runnable) {
        this.buttonText = buttonText
        this.action = action
    }

}