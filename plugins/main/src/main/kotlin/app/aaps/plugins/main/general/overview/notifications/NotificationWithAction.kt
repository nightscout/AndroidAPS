package app.aaps.plugins.main.general.overview.notifications

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.R
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class NotificationWithAction @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin
) : Notification() {


    var validityCheck: (() -> Boolean)? = null

    fun with(id: Int, text: String, level: Int, validityCheck: (() -> Boolean)?) = this.also {
        this.id = id
        date = System.currentTimeMillis()
        this.text = text
        this.level = level
        this.validityCheck = validityCheck
    }

    fun with (nsAlarm: NSAlarm) = this.also {
        date = System.currentTimeMillis()
        when (nsAlarm.level) {
            0 -> {
                id = NS_ANNOUNCEMENT
                level = ANNOUNCEMENT
                text = nsAlarm.message
                validTo = System.currentTimeMillis() + T.mins(60).msecs()
            }

            1 -> {
                id = NS_ALARM
                level = NORMAL
                text = nsAlarm.title
                soundId = app.aaps.core.ui.R.raw.alarm
            }

            2 -> {
                id = NS_URGENT_ALARM
                level = URGENT
                text = nsAlarm.title
                soundId = R.raw.urgentalarm
            }
        }
        buttonText = app.aaps.core.ui.R.string.snooze
        action = Runnable {
            activePlugin.activeNsClient?.handleClearAlarm(nsAlarm, 60 * 60 * 1000L)
            // Adding current time to snooze if we got staleData
            aapsLogger.debug(LTag.NOTIFICATION, "Notification text is: $text")
            val msToSnooze = preferences.get(IntKey.NsClientAlarmStaleData) * 60 * 1000L
            aapsLogger.debug(LTag.NOTIFICATION, "snooze nsalarm_staledatavalue in minutes is ${T.msecs(msToSnooze).mins()} currentTimeMillis is: ${System.currentTimeMillis()}")
            preferences.put(LongComposedKey.NotificationSnoozedTo, nsAlarm.level.toString(), value = System.currentTimeMillis() + msToSnooze)
        }
    }

    fun action(buttonText: Int, action: Runnable): NotificationWithAction {
        this.buttonText = buttonText
        this.action = action
        return this
    }

}