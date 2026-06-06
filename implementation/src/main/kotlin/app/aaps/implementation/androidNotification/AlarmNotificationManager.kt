package app.aaps.implementation.androidNotification

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.androidNotification.AlarmNotificationManager.Companion.CHANNEL_FULL_SCREEN_SILENT
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Builds and posts Android notifications for AAPS alarms. Replaces the old `AlarmSoundService` /
 * `AlarmSoundServiceHelper` foreground-service-based path.
 *
 * Two alarm modalities:
 *  - [postFullScreenAlarm]: posts on a silent FSI channel; Android auto-launches ErrorActivity
 *    when the device is idle, or shows a heads-up notification when the user is active.
 *    The activity owns audio playback (MediaPlayer + volume ramp).
 *  - [postSoundOnlyAlarm]: posts on a sound-bearing channel chosen by the
 *    [BooleanKey.AlertOverrideDoNotDisturb] preference. No activity, system plays channel sound.
 *
 * Channels are created eagerly when this @Singleton is first injected so that the first post
 * doesn't race channel creation.
 */
@Singleton
class AlarmNotificationManager @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val iconsProvider: IconsProvider,
    // Provider breaks a Dagger cycle: UiInteractionImpl injects this class, but we need
    // UiInteraction.errorHelperActivity for the FSI target. Provider defers resolution.
    private val uiInteractionProvider: Provider<UiInteraction>,
    private val rh: ResourceHelper
) {

    companion object {

        const val GROUP_ID = "aaps_alarm_group"

        /**
         * Sound-suppressed channel used by runAlarm when [BooleanKey.AlertIncreaseVolume] is on.
         * Heads-up popup still shows (IMPORTANCE_HIGH) and vibration still fires, but the channel
         * itself plays no sound — the activity's ramped MediaPlayer owns the audio experience.
         */
        const val CHANNEL_FULL_SCREEN_SILENT = "aaps_alarm_fullscreen_silent"

        /** Stable id for runAlarm notification (single active full-screen alarm at a time). */
        const val NOTIFICATION_ID_FULL_SCREEN = 4712

        /**
         * Base offset for per-AAPS-notification sound alarm IDs. The system notification ID is
         * `SOUND_ID_OFFSET + notificationKey`, where notificationKey is the `AapsNotification`'s
         * instanceKey (the `NotificationId.ordinal`, 0..~80, for single notifications; 10000+ for
         * `allowMultiple` ones). The 100_000 offset keeps both ranges well clear of
         * NotificationHolder=4711 and FULL_SCREEN=4712.
         */
        const val SOUND_ID_OFFSET = 100_000

        private val SOUND_NAMES: Map<Int, String> = mapOf(
            app.aaps.core.ui.R.raw.alarm to "alarm",
            app.aaps.core.ui.R.raw.boluserror to "boluserror",
            app.aaps.core.ui.R.raw.error to "error",
            app.aaps.core.ui.R.raw.urgentalarm to "urgentalarm"
        )

        private val DISPLAY_NAMES: Map<Int, String> = mapOf(
            app.aaps.core.ui.R.raw.alarm to "Standard alarm",
            app.aaps.core.ui.R.raw.boluserror to "Bolus error",
            app.aaps.core.ui.R.raw.error to "General error",
            app.aaps.core.ui.R.raw.urgentalarm to "Urgent alarm"
        )
    }

    private val mgr: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Tracks active sound notification keys so [cancelAlarm] can clear all of them.
     * Per-AAPS-notification cancel goes through [cancelSoundAlarm] and only touches one id —
     * avoids the "dismissing notification A stops notification B's sound" bug that comes from
     * a shared singleton notification id.
     *
     * IMPORTANT: every read/write of this field — AND the paired `mgr.notify`/`mgr.cancel`
     * call — must run inside `synchronized(activeSoundKeys)`. The post and cancel paths can be
     * called concurrently from unrelated threads (post comes from `NotificationManagerImpl`
     * under its own monitor; cancel comes from `UiInteractionImpl.stopAlarm` which has no
     * outer lock). Without pairing the notify/add (and remove/cancel) in one critical section
     * a global cancel can iterate-and-clear in between, leaving the just-posted system
     * notification orphaned.
     *
     * Note on OS-driven dismissal: notifications are `setOngoing(true)` so the user can't
     * swipe to dismiss, but the OS may still trim notifications under memory pressure. In
     * that rare case `activeSoundKeys` will hold stale entries until the next `cancelAlarm()`
     * (which is harmless — `mgr.cancel(id)` for a no-longer-active id is a no-op).
     */
    private val activeSoundKeys: MutableSet<Int> = mutableSetOf()

    init {
        createChannels()
    }

    private fun createChannels() {
        mgr.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, "AAPS Alarms"))

        // Clean up an even-older silent FSI channel id from before the rampEnabled split.
        // Harmless if absent.
        mgr.deleteNotificationChannel("aaps_alarm_fullscreen")

        // Silent FSI channel for the ramp-enabled case. IMPORTANCE_HIGH keeps the heads-up
        // popup visible; vibration enabled because the user opted into ramp, not into a
        // completely-silent alert.
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FULL_SCREEN_SILENT,
                "Urgent alarm (volume-ramp mode)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(true)
                group = GROUP_ID
                description = "Used when 'Gradually increase notification volume' is on — the activity's MediaPlayer owns the audio experience."
            }
        )

        // Two sound-bearing channels per R.raw sound: one routed to STREAM_ALARM (overrides DND),
        // one routed to STREAM_NOTIFICATION (respects DND/silent). Picked at post time based on
        // the user's AlertOverrideDoNotDisturb preference.
        //
        // Both `runAlarm` (full-screen) and `startAlarm` (sound-only) use these channels. For the
        // FSI path, the channel's one-shot sound covers the "user is active, FSI downgrades to
        // heads-up" case so the user always hears an audio cue at post time, not only after they
        // tap the notification. When the FSI does auto-launch (lockscreen/idle), ErrorActivity's
        // looping ramped audio takes over — its volume ramp starts at 0 so the overlap with the
        // channel one-shot is inaudible.
        for ((soundId, displayName) in DISPLAY_NAMES) {
            val uri: Uri = Uri.parse("android.resource://${context.packageName}/$soundId")

            val alarmAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelIdForSound(soundId, overrideDnd = true),
                    "$displayName (override DND)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(uri, alarmAttrs)
                    group = GROUP_ID
                }
            )

            val notifyAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // IMPORTANCE_HIGH gives heads-up popup behavior regardless of DND choice — DND/silent
            // is enforced by USAGE_NOTIFICATION at the audio layer, not by channel importance.
            // For medical alarms we always want heads-up visibility.
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelIdForSound(soundId, overrideDnd = false),
                    "$displayName (respects DND)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(uri, notifyAttrs)
                    group = GROUP_ID
                }
            )
        }
    }

    private fun openAppPendingIntent(): PendingIntent? {
        val mainActivity = uiInteractionProvider.get().mainActivity
        return TaskStackBuilder.create(context).run {
            addParentStack(mainActivity)
            addNextIntent(Intent(context, mainActivity))
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun channelIdForSound(@RawRes soundId: Int, overrideDnd: Boolean): String {
        val name = SOUND_NAMES[soundId] ?: "error"
        val suffix = if (overrideDnd) "alarm" else "notify"
        return "aaps_alarm_${name}_$suffix"
    }

    /**
     * Post a full-screen-intent alarm notification.
     * The notification carries an FSI to [UiInteraction.errorHelperActivity]; Android either
     * launches it full-screen (device idle / lock screen) or shows a heads-up notification.
     * The activity is responsible for sound playback.
     */
    fun postFullScreenAlarm(status: String, title: String, @RawRes soundId: Int) {
        // Ramp ON: channel sound is suppressed (setSilent below) so the activity's ramped audio
        // experience starts cleanly from silence. No deferral needed in the activity.
        // Ramp OFF: channel one-shot plays so the user hears immediate audio cue; the activity
        // defers its loop until the channel sound has finished (avoids double-audio).
        val rampEnabled = preferences.get(BooleanKey.AlertIncreaseVolume)

        val intent = Intent(context, uiInteractionProvider.get().errorHelperActivity).apply {
            putExtra(AlarmIntent.EXTRA_SOUND_ID, soundId)
            putExtra(AlarmIntent.EXTRA_STATUS, status)
            putExtra(AlarmIntent.EXTRA_TITLE, title)
            // Only stamp the post time when channel sound will actually play — the activity
            // uses this to compute its deferral. Omitting it tells the activity "no channel
            // sound, start audio immediately".
            if (!rampEnabled) {
                putExtra(AlarmIntent.EXTRA_POSTED_AT_ELAPSED_REALTIME, SystemClock.elapsedRealtime())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_FULL_SCREEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // On API 34+ the user can revoke USE_FULL_SCREEN_INTENT — if revoked, Android silently
        // downgrades the FSI to a heads-up. For a medical alarm that's a meaningful regression;
        // log loudly so it shows up in support logs. (The notification still posts and can still
        // be tapped to open ErrorActivity — just no auto-launch on the lock screen.)
        val fsiAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || mgr.canUseFullScreenIntent()
        if (!fsiAllowed) {
            aapsLogger.warn(
                LTag.NOTIFICATION,
                "USE_FULL_SCREEN_INTENT revoked by user — alarm will not auto-launch on lock screen. " +
                    "Grant via Settings → Apps → AAPS → Permissions → Full screen notifications."
            )
        }

        val overrideDnd = preferences.get(BooleanKey.AlertOverrideDoNotDisturb)
        // Ramp ON → silent channel (no channel sound, but heads-up + vibration still fire).
        // Ramp OFF → sound-bearing channel matching the requested soundId + DND preference.
        val channelId =
            if (rampEnabled) CHANNEL_FULL_SCREEN_SILENT
            else channelIdForSound(soundId, overrideDnd)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setContentTitle(title)
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .apply { if (fsiAllowed) setFullScreenIntent(pendingIntent, true) }
            .build()

        try {
            mgr.notify(NOTIFICATION_ID_FULL_SCREEN, notification)
            aapsLogger.debug(LTag.NOTIFICATION, "Posted full-screen alarm: $title - $status")
        } catch (ex: SecurityException) {
            // POST_NOTIFICATIONS revoked at runtime (Android 13+). The user's next return to
            // ComposeMainActivity refreshes the global PermissionsSheet (onResume → refresh),
            // which will surface the missing-permission state and offer to open Android settings.
            aapsLogger.error(
                LTag.NOTIFICATION,
                "Failed to post full-screen alarm \"$title\" — POST_NOTIFICATIONS likely revoked",
                ex
            )
        }
    }

    /**
     * Post a **silent** alarm notification on the [CHANNEL_FULL_SCREEN_SILENT] channel
     * (heads-up + vibration, no channel sound). The audio is owned by [AlarmSoundPlayer]; this
     * notification provides shade/lock-screen visibility and a tap target to open the app.
     *
     * Tracked in [activeSoundKeys] exactly like [postSoundAlarmNotification], so
     * [cancelSoundAlarm] / [cancelAlarm] clear it.
     *
     * @param notificationKey unique-per-AAPS-notification identifier (the `AapsNotification.instanceKey`)
     * @param urgent          true for URGENT-level alerts (stronger vibration)
     */
    fun postSilentAlarmNotification(
        notificationKey: Int,
        title: String,
        body: String,
        urgent: Boolean
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_FULL_SCREEN_SILENT)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setLargeIcon(rh.decodeResource(iconsProvider.getIcon()))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent())
        if (urgent) {
            builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
        } else {
            builder.setVibrate(longArrayOf(0, 100, 50, 100, 50))
        }

        val systemId = SOUND_ID_OFFSET + notificationKey
        try {
            // notify + add must be atomic relative to cancelAlarm/cancelSoundAlarm — see activeSoundKeys.
            synchronized(activeSoundKeys) {
                mgr.notify(systemId, builder.build())
                activeSoundKeys.add(notificationKey)
            }
            aapsLogger.debug(LTag.NOTIFICATION, "Posted silent alarm key=$notificationKey: $title - $body")
        } catch (ex: SecurityException) {
            // POST_NOTIFICATIONS revoked at runtime (Android 13+) — see postFullScreenAlarm.
            aapsLogger.error(
                LTag.NOTIFICATION,
                "Failed to post silent alarm \"$title\" key=$notificationKey — POST_NOTIFICATIONS likely revoked",
                ex
            )
        }
    }

    /**
     * Cancel a specific sound alarm by its [notificationKey] (the `AapsNotification.instanceKey`).
     * No-op if that key has no active notification. Use this when an individual AAPS notification is
     * dismissed/replaced/expired so we only cancel its own sound, not any concurrently active
     * alarms.
     */
    fun cancelSoundAlarm(notificationKey: Int) {
        val cancelled = synchronized(activeSoundKeys) {
            if (activeSoundKeys.remove(notificationKey)) {
                mgr.cancel(SOUND_ID_OFFSET + notificationKey)
                true
            } else false
        }
        if (cancelled) aapsLogger.debug(LTag.NOTIFICATION, "Cancelled sound alarm key=$notificationKey")
    }

    /**
     * Cancel all active AAPS alarm notifications (full-screen + every tracked sound alarm).
     * Used by the global stop paths: user-acknowledge from ErrorActivity, app onTerminate,
     * Wear "mute all" gesture.
     */
    fun cancelAlarm() {
        mgr.cancel(NOTIFICATION_ID_FULL_SCREEN)
        synchronized(activeSoundKeys) {
            activeSoundKeys.forEach { mgr.cancel(SOUND_ID_OFFSET + it) }
            activeSoundKeys.clear()
        }
        aapsLogger.debug(LTag.NOTIFICATION, "Cancelled all AAPS alarm notifications")
    }
}
