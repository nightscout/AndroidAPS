package app.aaps.implementations

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.ComposeMainActivity
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import app.aaps.ui.activities.ErrorActivity
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class UiInteractionImpl @Inject constructor(
    private val context: Context,
    private val alarmNotificationManager: AlarmNotificationManager,
    // Provider breaks a Dagger cycle: NotificationManagerImpl injects NotificationHolder, which
    // injects this UiInteraction. notificationManager is only needed lazily in stopAlarm().
    private val notificationManager: Provider<NotificationManager>,
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    @ApplicationScope private val appScope: CoroutineScope
) : UiInteraction {

    override val mainActivity: Class<*> = ComposeMainActivity::class.java
    override val errorHelperActivity: Class<*> = ErrorActivity::class.java

    override fun runAlarm(status: String, title: String, @RawRes soundId: Int) {
        // Persist the error as an announcement at fire time — gated by the NS-announcement
        // preference + APS build. Done here (not in ErrorActivity) so the record is written for
        // every alarm with the true trigger time, regardless of whether/how it is later
        // acknowledged (phone activity, Wear mute, OS-trimmed notification, or never opened).
        if (config.APS)
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
            aapsLogger.debug(LTag.CORE, "runAlarm (off-main → FSI): $title - $status (sound=$soundId)")
            alarmNotificationManager.postFullScreenAlarm(status = status, title = title, soundId = soundId)
            return
        }

        if (isAppInForeground()) {
            // Foreground path — launch the activity directly. No notification needed:
            //   • Avoids channel-sound vs activity-ramp conflict.
            //   • Activity opens instantly, owns ramped audio from 0.
            //   • Works because the caller's process is already foreground (Android's
            //     background-activity-start restriction does not apply).
            aapsLogger.debug(LTag.CORE, "runAlarm (foreground direct): $title - $status (sound=$soundId)")
            val intent = Intent(context, errorHelperActivity).apply {
                putExtra(AlarmIntent.EXTRA_SOUND_ID, soundId)
                putExtra(AlarmIntent.EXTRA_STATUS, status)
                putExtra(AlarmIntent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Defensive: if the activity start is rejected for any reason, fall back to
                // the FSI notification path so the alert is never silently lost.
                aapsLogger.error(LTag.CORE, "runAlarm: direct startActivity failed, falling back to FSI", ex)
                postFsiFallback(status, title, soundId)
            }
        } else {
            // Background path — FSI notification. Android auto-launches the activity on
            // lockscreen/idle, or shows a heads-up (with channel sound) when the user is
            // active in another app.
            aapsLogger.debug(LTag.CORE, "runAlarm (background via FSI): $title - $status (sound=$soundId)")
            alarmNotificationManager.postFullScreenAlarm(status = status, title = title, soundId = soundId)
        }
    }

    override fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "stopAlarm: $reason")
        // Route through the registry owner so all audible alarms are actually silenced: clears the
        // internal AlarmSoundPlayer (Wear snooze used to only cancel the system notification, leaving
        // the ramping audio playing), stops the full-screen audio, and cancels the notifications.
        notificationManager.get().muteAllAlarms()
    }

    /**
     * Posts an FSI alarm notification with an additional last-resort Toast safety net.
     * If `POST_NOTIFICATIONS` is also revoked, `postFullScreenAlarm`'s catch block already
     * logs the failure but the alarm is otherwise lost — the Toast provides at least a
     * visible signal that something tried to alarm. Best-effort; Toast can also fail (e.g.
     * if a system overlay permission is denied) but it costs nothing to try.
     */
    private fun postFsiFallback(status: String, title: String, @RawRes soundId: Int) {
        alarmNotificationManager.postFullScreenAlarm(status = status, title = title, soundId = soundId)
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
