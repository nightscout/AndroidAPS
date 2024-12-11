package app.aaps.plugins.main.general.overview.notifications

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.plugins.main.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class NotificationWithAction(
    injector: HasAndroidInjector
) : Notification() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var activePlugin: ActivePlugin

    var validityCheck: (() -> Boolean)? = null

    init {
        injector.androidInjector().inject(this)
    }

    constructor(injector: HasAndroidInjector, id: Int, text: String, level: Int, validityCheck: (() -> Boolean)?) : this(injector) {
        this.id = id
        date = System.currentTimeMillis()
        this.text = text
        this.level = level
        this.validityCheck = validityCheck
    }

    constructor (injector: HasAndroidInjector, nsAlarm: NSAlarm) : this(injector) {
        date = System.currentTimeMillis()
        when (nsAlarm.level()) {
            0 -> {
                id = NS_ANNOUNCEMENT
                level = ANNOUNCEMENT
                text = nsAlarm.message()
                validTo = System.currentTimeMillis() + T.mins(60).msecs()
            }

            1 -> {
                id = NS_ALARM
                level = NORMAL
                text = nsAlarm.title()
                soundId = app.aaps.core.ui.R.raw.alarm
            }

            2 -> {
                id = NS_URGENT_ALARM
                level = URGENT
                text = nsAlarm.title()
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
            sp.putLong(rh.gs(app.aaps.core.utils.R.string.key_snoozed_to) + nsAlarm.level(), System.currentTimeMillis() + msToSnooze)
        }
    }

    fun action(buttonText: Int, action: Runnable): NotificationWithAction {
        this.buttonText = buttonText
        this.action = action
        return this
    }

}