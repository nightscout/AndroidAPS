package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.widget.WidgetUpdater
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * Phone features a desktop does not have.
 *
 * Each one reports absence rather than pretending, and each says so in the log. The rule this file
 * follows is the Apple one: nothing returns a plausible value, and nothing is silent.
 */

/**
 * No SMS on a desktop, and it says so before anything tries.
 *
 * [isEnabled] answering false is what keeps this honest: the SMS commands plugin checks it, so the
 * feature is visibly off rather than present and dropping messages. Should something send anyway,
 * [sendSMS] returns false - a failure the caller can report - rather than true.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopSmsCommunicator @Inject constructor(
    private val aapsLogger: AAPSLogger
) : SmsCommunicator {

    override var messages: ArrayList<Sms> = ArrayList()

    override fun isEnabled(): Boolean = false

    override fun sendSMS(sms: Sms): Boolean {
        aapsLogger.error(LTag.SMS, "Desktop cannot send SMS")
        return false
    }

    override fun sendNotificationToAllNumbers(text: String): Boolean {
        aapsLogger.error(LTag.SMS, "Desktop cannot send SMS")
        return false
    }
}

/**
 * No analytics from the desktop build.
 *
 * Firebase has no desktop client here, and [fabricEnabled] answering false is the truthful answer
 * rather than a stub's shrug. Worth keeping that way on purpose: this tree has already had CI
 * instrumented runs counted as production users once.
 *
 * [logException] logs at error, because an exception nobody records is one nobody fixes.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopFabricPrivacy @Inject constructor(
    private val aapsLogger: AAPSLogger
) : FabricPrivacy {

    override fun fabricEnabled(): Boolean = false

    override fun setUserProperty(key: String, value: String) {}

    override fun logCustom(event: String) {
        aapsLogger.debug(LTag.CORE, "Analytics event (not sent): $event")
    }

    override fun logCustom(name: String, params: Map<String, Long>) {
        aapsLogger.debug(LTag.CORE, "Analytics event (not sent): $name $params")
    }

    override fun logMessage(message: String) {
        aapsLogger.debug(LTag.CORE, "Analytics message (not sent): $message")
    }

    override fun logException(throwable: Throwable) {
        aapsLogger.error(LTag.CORE, "Exception (not reported to analytics): ${throwable.message}", throwable)
    }

    override fun logWearException(wearException: EventData.WearException) {
        aapsLogger.error(LTag.CORE, "Wear exception (not reported to analytics): $wearException")
    }
}

/**
 * There is no home screen widget to update.
 *
 * A widget is an Android launcher feature. Nothing is lost by doing nothing here, which is why this
 * logs at debug rather than error - unlike the ones above, no user-facing behaviour goes missing.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopWidgetUpdater @Inject constructor(
    private val aapsLogger: AAPSLogger
) : WidgetUpdater {

    override fun update(from: String) {
        aapsLogger.debug(LTag.CORE, "No desktop widget to update (from $from)")
    }
}

/**
 * The parts of `UiInteraction` a desktop can do, and honest failure for the parts it cannot.
 *
 * The alarms are real: they go to the same notification registry every other alarm uses, so an
 * urgent condition still reaches the user through the system tray.
 *
 * The two activity classes are not. They exist so Android code can build an `Intent`, which has no
 * meaning here; nothing on this platform reads them, and they return this class so the type is
 * satisfied. If one of these ever turns up in a stack trace, the caller is Android-only code that
 * should not have been reached.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopUiInteraction @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager
) : UiInteraction {

    override val mainActivity: KClass<*> = DesktopUiInteraction::class
    override val errorHelperActivity: KClass<*> = DesktopUiInteraction::class

    override fun runAlarm(status: String, title: String, sound: AlarmSound?) {
        aapsLogger.debug(LTag.CORE, "Alarm: $title - $status")
        notificationManager.post(NotificationId.TOAST_ALARM, status, sound = sound)
    }

    override fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "Alarm stopped: $reason")
        notificationManager.dismiss(NotificationId.TOAST_ALARM)
    }
}
