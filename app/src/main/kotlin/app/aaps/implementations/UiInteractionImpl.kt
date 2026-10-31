package app.aaps.implementations

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.ComposeMainActivity
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAlarmDismissed
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.ui.activities.ErrorActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class UiInteractionImpl(
    private val context: Context,
    private val alarmNotificationManager: AlarmNotificationManager,
    private val notificationManager: () -> NotificationManager,
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    @ApplicationScope private val appScope: CoroutineScope,
    private val rxBus: RxBus,
    // A provider: EatReminder needs UiInteraction back to silence its alarm.
    private val eatReminder: () -> EatReminder
) : UiInteraction {

    override val mainActivity: KClass<*> = ComposeMainActivity::class
    override val errorHelperActivity: KClass<*> = ErrorActivity::class

    /**
     * The alarm shown now is the "time to eat" timer's. There is one full-screen alarm at a time and a newer one
     * takes its place, so this follows the last [runAlarm]. Stopping, silencing and +5 min of the timer use it to
     * reach only their own alarm (#5408).
     */
    @Volatile private var eatReminderAlarmShown = false

    /** The alarm shown now, for the re-ring of a mute. Null while the eat alarm is shown: it has no mute, +5 min moves its timer. */
    @Volatile private var lastAlarm: Alarm? = null

    /** The re-ring of a mute, until it rang or a stop or a new alarm ended it. */
    @Volatile private var reRing: Job? = null

    private class Alarm(val status: String, val title: String, val sound: AlarmSound?)

    override fun runAlarm(status: String, title: String, sound: AlarmSound?) = runAlarm(status, title, sound, eatReminder = false)

    override fun runEatReminderAlarm(status: String, title: String) = runAlarm(status, title, AlarmSound.EAT_REMINDER, eatReminder = true)

    /** [announce] false for the re-ring of a mute: the announcement was written when the alarm first rang. */
    private fun runAlarm(status: String, title: String, sound: AlarmSound?, eatReminder: Boolean, announce: Boolean = true) {
        // A new alarm takes the place of a muted one
        reRing?.cancel()
        reRing = null
        lastAlarm = if (eatReminder) null else Alarm(status, title, sound)
        eatReminderAlarmShown = eatReminder
        // The watch shows the alarm with Silence and Mute 5 min. It rings the eat alarm by itself, so for that one
        // only the alarm it takes the place of goes away there.
        rxBus.send(
            EventMobileToWear(
                if (eatReminder) EventData.AlarmStopped(System.currentTimeMillis())
                else EventData.AlarmRinging(System.currentTimeMillis(), title = title, status = status, canMute = true)
            )
        )
        // Persist the error as an announcement at fire time — gated by the NS-announcement
        // preference + APS build. Done here (not in ErrorActivity) so the record is written for
        // every alarm with the true trigger time, regardless of whether/how it is later
        // acknowledged (phone activity, Wear mute, OS-trimmed notification, or never opened).
        if (config.APS && announce)
            appScope.launch {
                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE.asAnnouncement(status),
                    action = Action.CAREPORTAL,
                    source = Sources.Aaps,
                    note = status,
                    listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
                )
            }

        // ProcessLifecycleOwner.currentState requires main-thread access (officially @MainThread).
        // BLE callbacks, RxJava workers, and coroutine non-main dispatchers all call runAlarm
        // from non-main threads. From those contexts we skip the foreground-direct optimization
        // entirely and use the FSI path, which is safe from any thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            aapsLogger.debug(LTag.CORE, "runAlarm (off-main → FSI): $title - $status (sound=$sound)")
            alarmNotificationManager.postFullScreenAlarm(status = status, title = title, sound = sound, eatReminder = eatReminder)
            return
        }

        if (isAppInForeground()) {
            // Foreground path — launch the activity directly. No notification needed:
            //   • Avoids channel-sound vs activity-ramp conflict.
            //   • Activity opens instantly, owns ramped audio from 0.
            //   • Works because the caller's process is already foreground (Android's
            //     background-activity-start restriction does not apply).
            aapsLogger.debug(LTag.CORE, "runAlarm (foreground direct): $title - $status (sound=$sound)")
            val intent = Intent(context, errorHelperActivity.java).apply {
                putExtra(AlarmIntent.EXTRA_SOUND, sound?.name)
                putExtra(AlarmIntent.EXTRA_STATUS, status)
                putExtra(AlarmIntent.EXTRA_TITLE, title)
                putExtra(AlarmIntent.EXTRA_EAT_REMINDER, eatReminder)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Defensive: if the activity start is rejected for any reason, fall back to
                // the FSI notification path so the alert is never silently lost.
                aapsLogger.error(LTag.CORE, "runAlarm: direct startActivity failed, falling back to FSI", ex)
                postFsiFallback(status, title, sound, eatReminder)
            }
        } else {
            // Background path — the full-screen alarm ("FSI" in the names here, although it no
            // longer uses a full-screen intent): a notification with channel sound plus the looping
            // alarm sound, and a setAlarmClock screen wake that brings up the activity when exact
            // alarms are allowed.
            aapsLogger.debug(LTag.CORE, "runAlarm (background via FSI): $title - $status (sound=$sound)")
            alarmNotificationManager.postFullScreenAlarm(status = status, title = title, sound = sound, eatReminder = eatReminder)
        }
    }

    override fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "stopAlarm: $reason")
        // A muted alarm that is stopped does not come back
        reRing?.cancel()
        reRing = null
        // Every way of stopping the alarm shown comes here (alarm screen, notification Mute or swipe, watch). When it
        // is the "time to eat" alarm, that is the timer's Stop, as on its bar: the timer ends and only its alarm is
        // silenced, other alarms keep sounding. Stopping another alarm leaves the timer alone, also a pump alarm that
        // came in after the eat alarm rang and took its place (#5408).
        if (eatReminderAlarmShown) {
            // Only the timer this alarm rang for. A new timer started since then has not rung, and runs on.
            if (eatReminder().state.value?.alarmRang == true) eatReminder().stop()
            // Also when the timer is gone or new: this alarm is still shown
            stopEatReminderAlarm()
            return
        }
        // Route through the registry owner so all audible alarms are actually silenced: clears the
        // internal AlarmSoundPlayer (Wear snooze used to only cancel the system notification, leaving
        // the ramping audio playing), stops the full-screen audio, and cancels the notifications.
        notificationManager().muteAllAlarms()
        rxBus.send(EventMobileToWear(EventData.AlarmStopped(System.currentTimeMillis())))
    }

    override fun dismissAlarm(reason: String) {
        stopAlarm(reason)
        // The alarm screen is its own activity: it is told, and closes itself
        rxBus.send(EventAlarmDismissed())
    }

    override fun muteAlarm(minutes: Int, reason: String) {
        stopAlarm(reason)
        val alarm = lastAlarm ?: return
        reRing = appScope.launch {
            delay(T.mins(minutes.toLong()).msecs())
            reRing = null
            aapsLogger.debug(LTag.CORE, "Alarm rings again after $minutes min: ${alarm.title} - ${alarm.status}")
            // On the main thread, so with the app in front it goes straight to the alarm screen, as every alarm does
            withContext(Dispatchers.Main) { runAlarm(alarm.status, alarm.title, alarm.sound, eatReminder = false, announce = false) }
        }
    }

    override fun stopEatReminderAlarm() {
        if (!eatReminderAlarmShown) return
        eatReminderAlarmShown = false
        aapsLogger.debug(LTag.CORE, "stopEatReminderAlarm")
        // The full-screen alarm and its looping sound, nothing in the notification list
        alarmNotificationManager.cancelAlarm()
    }

    /**
     * Posts an FSI alarm notification with an additional last-resort Toast safety net.
     * If `POST_NOTIFICATIONS` is also revoked, `postFullScreenAlarm`'s catch block already
     * logs the failure but the alarm is otherwise lost — the Toast provides at least a
     * visible signal that something tried to alarm. Best-effort; Toast can also fail (e.g.
     * if a system overlay permission is denied) but it costs nothing to try.
     */
    private fun postFsiFallback(status: String, title: String, sound: AlarmSound?, eatReminder: Boolean) {
        alarmNotificationManager.postFullScreenAlarm(status = status, title = title, sound = sound, eatReminder = eatReminder)
        // Toast must be created on the main thread (we are — runAlarm guards above).
        runCatching {
            Toast.makeText(context, "ALARM: $title — $status", Toast.LENGTH_LONG).show()
        }.onFailure {
            aapsLogger.error(LTag.CORE, "runAlarm: Toast fallback also failed; alarm not user-visible", it)
        }
    }

    /**
     * True when the AAPS process has at least one STARTED activity — meaning Android's
     * background-activity-start restriction does not apply and we can [Context.startActivity]
     * without going through a notification PendingIntent.
     *
     * **Must only be called from the main thread.** `Lifecycle.currentState` is officially
     * `@MainThread`; callers are guarded in [runAlarm].
     */
    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
