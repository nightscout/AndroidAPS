package app.aaps.implementation.androidNotification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.DoNotDisturbRule
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.AndroidActivities
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.rawRes
import app.aaps.implementation.R
import app.aaps.implementation.alerts.EatReminderReceiver
import app.aaps.implementation.androidNotification.AlarmNotificationManager.Companion.CHANNEL_SILENT
import app.aaps.implementation.androidNotification.AlarmNotificationManager.Companion.CHANNEL_SILENT_URGENT
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Builds and posts Android notifications for AAPS alarms. Replaces the old `AlarmSoundService` /
 * `AlarmSoundServiceHelper` foreground-service-based path.
 *
 * One alarm shape now, not two. Every alarm is a card in the notification list, and this class posts
 * [postSilentAlarmNotification] for it: a silent channel with heads-up and vibration, while the sound comes from
 * [AlarmSoundPlayer], which the registry starts. [scheduleScreenWakeAndLaunch] wakes the screen for the alarm
 * screen when AAPS is in the background, and [postChime] is the single sound of a WARNING alert.
 *
 * There used to be a second one, `postFullScreenAlarm`, for `UiInteraction.runAlarm`: its own sound-bearing
 * channel, its own looping player under `OWNER_FULLSCREEN`, its own Mute action and its own record of an
 * unanswered alarm - a whole parallel alarm path that iOS had no answer to. `runAlarm` raises a
 * `Alert` now, so there is one path on both platforms; see `_docs/PUMP_DESIGN.md`, section 8.
 *
 * Channels are created on the first post rather than at construction - see the `channels` field. Every posting
 * method touches it before building anything, so a post can never race their creation.
 */
@SingleIn(AppScope::class)
@Inject
class AlarmNotificationManager(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val iconsProvider: IconsProvider,
    private val androidActivitiesProvider: () -> AndroidActivities,
    private val notificationHolderProvider: () -> NotificationHolder,
    private val rh: ResourceHelper
) {

    companion object {

        const val GROUP_ID = "aaps_alarm_group"

        /**
         * Sound-suppressed channels used by [postSilentAlarmNotification], where audio is owned
         * externally by [app.aaps.core.interfaces.notifications.AlarmSoundPlayer]. Heads-up popup still
         * shows (IMPORTANCE_HIGH) and the channel vibrates, but it plays no sound. One for urgent alerts
         * and one for the others, because the vibration pattern belongs to the channel.
         * (Not used by [postFullScreenAlarm], which always posts on a sound-bearing channel so
         * the alarm is audible even when its full-screen activity can't launch.)
         */
        const val CHANNEL_SILENT_URGENT = "aaps_alarm_v2_silent_urgent"
        const val CHANNEL_SILENT = "aaps_alarm_v2_silent"

        /**
         * Since Android 8 a notification vibrates only as its channel says: `setVibrate` on the
         * notification is ignored. And an app cannot change the vibration of a channel that exists, only
         * the user can. The old channels had no vibration, so alarms did not vibrate at all, and the
         * channels that must vibrate got new ids (#5217). The old ones are deleted.
         */
        private val OLD_CHANNEL_IDS = listOf("aaps_alarm_fullscreen", "aaps_alarm_fullscreen_silent") +
            listOf("alarm", "boluserror", "error", "urgentalarm").flatMap { listOf("aaps_alarm_${it}_alarm", "aaps_alarm_${it}_notify") }

        /** Delay, vibrate, pause, vibrate... in ms */
        private val VIBRATION_URGENT = longArrayOf(1000, 1000, 1000, 1000)
        private val VIBRATION_NORMAL = longArrayOf(0, 100, 50, 100, 50)

        /** Request code for the screen-wake broadcast + its AlarmClockInfo show intent. */
        private const val WAKE_REQUEST_CODE = 4713

        /**
         * Delay before the screen-wake / activity-launch alarms fire. Small buffer so both
         * setAlarmClock alarms register before firing; a ~1.5s wait to light the screen is
         * negligible for a medical alarm.
         */
        private const val SCREEN_WAKE_DELAY_MS = 1_500L

        /**
         * Base offset for per-AAPS-notification sound alarm IDs. The system notification ID is
         * `SOUND_ID_OFFSET + notificationKey`, where notificationKey is the `AapsNotification`'s
         * instanceKey (the `NotificationId.ordinal`, 0..~80, for single notifications; 10000+ for
         * `allowMultiple` ones). The 100_000 offset keeps both ranges well clear of
         * NotificationHolder=4711 and FULL_SCREEN=4712.
         */
        const val SOUND_ID_OFFSET = 100_000

        /** A ringtone chosen for a WARNING alert plays at most this long: a chime, not a phone call. */
        const val CHIME_RINGTONE_MS = 5_000L

        /** Base of the ids of [postChime]: above the sound alarms, whose keys stay far below 100 000. */
        const val CHIME_ID_OFFSET = 300_000

        private val SOUND_NAMES: Map<AlarmSound, String> = mapOf(
            AlarmSound.ALARM to "alarm",
            AlarmSound.BOLUS_ERROR to "boluserror",
            AlarmSound.ERROR to "error",
            AlarmSound.URGENT_ALARM to "urgentalarm",
            AlarmSound.EAT_REMINDER to "eatreminder"
        )
    }

    private val mgr: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Tracks active sound notification keys so [cancelAlarm] can clear all of them.
     * Per-AAPS-notification cancel goes through [cancelSoundAlarm] and only touches one id —
     * avoids the "dismissing notification A stops notification B's sound" bug that comes from
     * a shared singleton notification id.
     * IMPORTANT: every read/write of this field — AND the paired `mgr.notify`/`mgr.cancel`
     * call — must run inside `synchronized(activeSoundKeys)`. The post and cancel paths can be
     * called concurrently from unrelated threads (post comes from `NotificationManagerImpl`
     * under its own monitor; cancel comes from `UiInteractionImpl.stopAlarm` which has no
     * outer lock). Without pairing the notify/add (and remove/cancel) in one critical section
     * a global cancel can iterate-and-clear in between, leaving the just-posted system
     * notification orphaned.
     * Note on OS-driven dismissal: notifications are `setOngoing(true)` so the user can't
     * swipe to dismiss, but the OS may still trim notifications under memory pressure. In
     * that rare case `activeSoundKeys` will hold stale entries until the next `cancelAlarm()`
     * (which is harmless — `mgr.cancel(id)` for a no-longer-active id is a no-op).
     */
    private val activeSoundKeys: MutableSet<Int> = mutableSetOf()

    /**
     * The channels, created once on first use rather than in an `init` block.
     * Android requires a channel to exist before anything posts to it, and **every** post here touches this
     * first. So the guarantee is the same as creating them in the constructor - and stronger in one way: it cannot
     * be missed by a code path that gets an instance some other way.
     * `lazy` is synchronized by default, so two threads posting at once still create them once.
     */
    private val channels: Unit by lazy { createChannels() }

    private fun createChannels() {
        mgr.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, context.getString(R.string.alarm_channel_group)))

        // Harmless if absent.
        OLD_CHANNEL_IDS.forEach { mgr.deleteNotificationChannel(it) }

        // Silent channels for the ramp-enabled case. IMPORTANCE_HIGH keeps the heads-up
        // popup visible; vibration enabled because the user opted into ramp, not into a
        // completely-silent alert.
        for (urgent in listOf(true, false)) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    if (urgent) CHANNEL_SILENT_URGENT else CHANNEL_SILENT,
                    context.getString(if (urgent) R.string.alarm_channel_silent_urgent else R.string.alarm_channel_silent),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = if (urgent) VIBRATION_URGENT else VIBRATION_NORMAL
                    group = GROUP_ID
                    description = context.getString(R.string.alarm_channel_silent_description)
                }
            )
        }

        // Two sound-bearing channels per R.raw sound: one routed to STREAM_ALARM (overrides DND),
        // one routed to STREAM_NOTIFICATION (respects silent mode). Picked at post time based on
        // the user's AlertOverrideDoNotDisturb preference. The second one does not respect DND: the
        // notification is CATEGORY_ALARM, which DND lets ring when the profile allows alarms, so under
        // DND with the override off the silent channel is used instead.
        //
        // [postChime] is what uses these now - the one sound of a WARNING alert. A ringing alarm does not: its
        // audio is the looping, ramping [AlarmSoundPlayer], and a channel sound on top of it would be two sounds
        // for one alarm. They all vibrate with the urgent pattern.
        for (sound in AlarmSound.entries) {
            val displayName = rh.gs(sound.title)
            val uri: Uri = Uri.parse("android.resource://${context.packageName}/${sound.rawRes}")

            val alarmAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelIdForSound(sound, overrideDnd = true),
                    context.getString(R.string.alarm_channel_override_dnd, displayName),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(uri, alarmAttrs)
                    enableVibration(true)
                    vibrationPattern = VIBRATION_URGENT
                    group = GROUP_ID
                }
            )

            val notifyAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // IMPORTANCE_HIGH gives heads-up popup behavior regardless of DND choice — silent mode
            // is enforced by USAGE_NOTIFICATION at the audio layer, not by channel importance.
            // For medical alarms we always want heads-up visibility.
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelIdForSound(sound, overrideDnd = false),
                    context.getString(R.string.alarm_channel_respects_dnd, displayName),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(uri, notifyAttrs)
                    enableVibration(true)
                    vibrationPattern = VIBRATION_URGENT
                    group = GROUP_ID
                }
            )
        }
    }

    private fun channelIdForSound(sound: AlarmSound, overrideDnd: Boolean): String {
        val name = SOUND_NAMES[sound] ?: "error"
        val suffix = if (overrideDnd) "alarm" else "notify"
        return "aaps_alarm_v2_${name}_$suffix"
    }


    /**
     * Wakes the display and launches [AndroidActivities.alarm]
     * for a background alarm without `USE_FULL_SCREEN_INTENT`, by firing two simultaneous
     * [AlarmManager.setAlarmClock] alarms (alarm-clock alarms are Doze-exempt and get the
     * background-activity-launch exemption):
     *  1. a broadcast to [AlarmScreenWakeReceiver], whose `ACQUIRE_CAUSES_WAKEUP` wake lock powers
     *     the screen on;
     *  2. [activityPendingIntent] (a `getActivity` at ErrorActivity), which the OS holds until the
     *     screen is on and then presents — over the keyguard when AAPS is the foreground task.
     * A small delay lets the alarms register before firing.
     *
     * `setAlarmClock` needs `SCHEDULE_EXACT_ALARM` from targetSdk 31 on, unless AAPS is excluded from
     * battery optimization; Android 14 and later deny the permission by default on a new install.
     * `canScheduleExactAlarms()` is true in either case. Without it this step is skipped rather than
     * letting a `SecurityException` reach whoever raised the alarm - the notification and the sound
     * already started, so the alarm still rings. `PluginPermissionsImpl` asks the user for it.
     *
     * Internal rather than private so a test can reach it without building notification channels, which a plain
     * JVM test cannot construct.
     */
    internal fun scheduleScreenWakeAndLaunch(activityPendingIntent: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!am.canScheduleExactAlarms()) {
            aapsLogger.warn(LTag.NOTIFICATION, "Exact alarms not allowed - alarm rings, but the screen is not woken")
            return
        }
        val triggerAt = System.currentTimeMillis() + SCREEN_WAKE_DELAY_MS
        val show = PendingIntent.getActivity(
            context, WAKE_REQUEST_CODE,
            Intent(context, androidActivitiesProvider().main.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val wakeOp = PendingIntent.getBroadcast(
            context, WAKE_REQUEST_CODE,
            Intent(context, AlarmScreenWakeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), wakeOp)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), activityPendingIntent)
        } catch (e: SecurityException) {
            // The permission was taken away between the check above and this call.
            aapsLogger.error(LTag.NOTIFICATION, "Exact alarms revoked - alarm rings, but the screen is not woken", e)
            return
        }
        aapsLogger.debug(LTag.NOTIFICATION, "Scheduled screen-wake + activity launch in ${SCREEN_WAKE_DELAY_MS}ms")
    }

    /**
     * Post a **silent** alarm notification on [CHANNEL_SILENT_URGENT] or [CHANNEL_SILENT]
     * (heads-up + vibration, no channel sound). The audio is owned by [AlarmSoundPlayer]; this
     * notification provides shade/lock-screen visibility and a tap target to open the app.
     * Tracked in [activeSoundKeys], so [cancelSoundAlarm] / [cancelAlarm] clear it.
     * @param notificationKey unique-per-AAPS-notification identifier (the `AapsNotification.instanceKey`)
     * @param urgent          true for URGENT-level alerts (stronger vibration)
     */
    fun postSilentAlarmNotification(
        notificationKey: Int,
        title: String,
        body: String,
        urgent: Boolean,
        dismissIntent: PendingIntent? = null
    ) {
        channels // created on first post, see the field
        val builder = NotificationCompat.Builder(context, if (urgent) CHANNEL_SILENT_URGENT else CHANNEL_SILENT)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, iconsProvider.getIcon()))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(notificationHolderProvider().openAppIntent())
        // Mute, and the swipe that means the same thing. Both say "this card is answered", and what that does to
        // the alarm behind it is the alarm's own business: one of the app's own ends, a pump alert goes quiet for
        // its silence window and comes back. This notification had neither before - only the deleted
        // `postFullScreenAlarm` did - so a ringing pump alert could not be muted from the lock screen at all, and a
        // swipe of it did nothing (#5338, in the half of the code that did not get the fix).
        dismissIntent?.let {
            builder.setDeleteIntent(it)
            builder.addAction(0, rh.gs(app.aaps.core.ui.R.string.mute), it)
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
            // POST_NOTIFICATIONS revoked at runtime (Android 13+). The user's next return to the main screen
            // refreshes the permissions sheet, which surfaces it and offers to open Android settings.
            aapsLogger.error(
                LTag.NOTIFICATION,
                "Failed to post silent alarm \"$title\" key=$notificationKey — POST_NOTIFICATIONS likely revoked",
                ex
            )
        }
    }

    /**
     * A notification that sounds once, for a WARNING alert: the sound channel of [sound], so the sound plays one time
     * and does not loop. While Do Not Disturb is on and the override is off it only vibrates, as every alarm here (see
     * [DoNotDisturbRule]). [chimeKey] identifies it for [cancelChime].
     *
     * With the user's [ringtoneUri] (`_docs/ALARMS.md`, step 2e) the notification goes on the silent channel and the
     * ringtone plays once, for [CHIME_RINGTONE_MS] at most: a channel's sound is fixed when it is made, so it cannot be
     * the ringtone. A ringtone that cannot be opened chimes with [sound] instead. [vibrateOnly]: the user chose Vibrate
     * for its kind, so it only vibrates, as in Do Not Disturb. [breakThroughDnd]: it sounds in Do Not Disturb whatever
     * the override says, for a test the user asked for.
     */
    fun postChime(
        chimeKey: Int,
        title: String,
        body: String,
        sound: AlarmSound = AlarmSound.ALARM,
        ringtoneUri: String? = null,
        actions: List<NotificationCompat.Action> = emptyList(),
        vibrateOnly: Boolean = false,
        breakThroughDnd: Boolean = false
    ) {
        channels // created on first post, see the field
        val overrideDnd = breakThroughDnd || preferences.get(BooleanKey.AlertOverrideDoNotDisturb)
        val quiet = vibrateOnly || DoNotDisturbRule.staysQuiet(overrideDnd, mgr.currentInterruptionFilter)
        val ringtonePlayed = !quiet && ringtoneUri != null && playRingtoneOnce(ringtoneUri, overrideDnd)
        val channelId =
            if (quiet || ringtonePlayed) CHANNEL_SILENT
            else channelIdForSound(sound, overrideDnd)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(notificationHolderProvider().openAppIntent())
            .also { builder -> actions.forEach { builder.addAction(it) } }
            .build()
        try {
            mgr.notify(CHIME_ID_OFFSET + chimeKey, notification)
            aapsLogger.debug(LTag.NOTIFICATION, "Posted chime key=$chimeKey: $title")
        } catch (ex: SecurityException) {
            aapsLogger.error(LTag.NOTIFICATION, "Failed to post chime \"$title\" - POST_NOTIFICATIONS likely revoked", ex)
        }
    }

    /** Plays [uri] once on the stream the alarms use, stopped after [CHIME_RINGTONE_MS]. False when it cannot be played. */
    private fun playRingtoneOnce(uri: String, overrideDnd: Boolean): Boolean = try {
        val ringtone = RingtoneManager.getRingtone(context, Uri.parse(uri))
        if (ringtone == null) {
            aapsLogger.error(LTag.NOTIFICATION, "Ringtone $uri cannot be opened, the chime plays its bundled sound")
            false
        } else {
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(if (overrideDnd) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.isLooping = false
            ringtone.play()
            Handler(Looper.getMainLooper()).postDelayed({ ringtone.stop() }, CHIME_RINGTONE_MS)
            true
        }
    } catch (e: Exception) {
        aapsLogger.error(LTag.NOTIFICATION, "Ringtone $uri cannot be played, the chime plays its bundled sound", e)
        false
    }

    fun cancelChime(chimeKey: Int) = mgr.cancel(CHIME_ID_OFFSET + chimeKey)

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
     * Cancel every alarm notification this class posted.
     *
     * Used by the global stop paths: Mute on the notification or a swipe of it (both through `AlarmMuteReceiver`),
     * app onTerminate, the watch's "mute all" gesture. The registry calls it from `muteAllAlarms`, and the alerts
     * whose cards go with it read that as the user's answer - one of the app's own alarms ends, a pump alert goes
     * quiet for its silence window.
     *
     * It no longer stops `OWNER_FULLSCREEN` audio, because nothing starts it any more: the one looping alarm sound
     * is the registry's, under `OWNER_INTERNAL`, and `AndroidSystemNotificationPlatform.cancelAll` stops it when
     * the last sounding card goes.
     */
    fun cancelAlarm() {
        synchronized(activeSoundKeys) {
            activeSoundKeys.forEach { mgr.cancel(SOUND_ID_OFFSET + it) }
            activeSoundKeys.clear()
        }
        aapsLogger.debug(LTag.NOTIFICATION, "Cancelled all AAPS alarm notifications")
    }
}
