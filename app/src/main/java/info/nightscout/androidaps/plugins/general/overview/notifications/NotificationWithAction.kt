package info.nightscout.androidaps.plugins.general.overview.notifications

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.sync.nsclient.NSClientPlugin
import info.nightscout.androidaps.plugins.sync.nsclient.data.NSAlarm
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class NotificationWithAction constructor(
    injector: HasAndroidInjector
) : Notification() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var nsClientPlugin: NSClientPlugin

    init {
        injector.androidInjector().inject(this)
    }

    constructor(injector: HasAndroidInjector, id: Int, text: String, level: Int) : this(injector) {
        this.id = id
        date = System.currentTimeMillis()
        this.text = text
        this.level = level
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
                soundId = R.raw.alarm
            }

            2 -> {
                id = NS_URGENT_ALARM
                level = URGENT
                text = nsAlarm.title()
                soundId = R.raw.urgentalarm
            }
        }
        buttonText = R.string.snooze
        action = Runnable {
            nsClientPlugin.handleClearAlarm(nsAlarm, 60 * 60 * 1000L)
            // Adding current time to snooze if we got staleData
            aapsLogger.debug(LTag.NOTIFICATION, "Notification text is: $text")
            val msToSnooze = sp.getInt(R.string.key_nsalarm_staledatavalue, 15) * 60 * 1000L
            aapsLogger.debug(LTag.NOTIFICATION, "snooze nsalarm_staledatavalue in minutes is ${T.msecs(msToSnooze).mins()} currentTimeMillis is: ${System.currentTimeMillis()}")
            sp.putLong(R.string.key_snoozedTo, System.currentTimeMillis() + msToSnooze)
        }
    }

    fun action(buttonText: Int, action: Runnable) {
        this.buttonText = buttonText
        this.action = action
    }

}