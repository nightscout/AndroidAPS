package app.aaps.implementation.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import android.app.NotificationManager as AndroidNotificationManager

@Singleton
class NotificationManagerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val context: Context,
    private val preferences: Preferences,
    private val iconsProvider: IconsProvider,
    private val notificationHolder: NotificationHolder,
    private val alarmNotificationManager: AlarmNotificationManager,
    private val alarmSoundPlayer: AlarmSoundPlayer
) : NotificationManager {

    private val _notifications = MutableStateFlow<List<AapsNotification>>(emptyList())
    override val notifications: StateFlow<List<AapsNotification>> = _notifications.asStateFlow()

    /** instanceKey of the URGENT alarm currently owning [AlarmSoundPlayer], or null when silent. */
    private var soundingKey: Int? = null

    private val nextInstanceKey = AtomicInteger(10000)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ordinal = intent?.getIntExtra("alertID", -1) ?: -1
            NotificationId.fromOrdinal(ordinal)?.let { dismiss(it) }
        }
    }

    init {
        createNotificationChannel()

        // Periodic cleanup for expiration when no new posts arrive
        scope.launch {
            while (true) {
                delay(30_000L)
                cleanUp()
            }
        }
    }

    @Synchronized
    override fun cleanUp() {
        removeExpired()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun createNotificationChannel() {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        val channel = NotificationChannel(NotificationManager.CHANNEL_ID, NotificationManager.CHANNEL_ID, AndroidNotificationManager.IMPORTANCE_HIGH)
        mgr.createNotificationChannel(channel)

        // Register dismiss receiver for system notification delete intents
        val filter = IntentFilter(NotificationManager.DISMISS_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            context.registerReceiver(dismissReceiver, filter)
    }

    @Synchronized
    override fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        validMinutes: Int,
        @RawRes soundRes: Int?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle {
        val now = System.currentTimeMillis()
        val validTo = if (validMinutes > 0) now + TimeUnit.MINUTES.toMillis(validMinutes.toLong()) else 0L
        return postInternal(
            id = id, text = text, level = level,
            date = now, validTo = validTo,
            soundRes = soundRes, actions = actions, validityCheck = validityCheck
        )
    }

    @Synchronized
    override fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        date: Long,
        validTo: Long,
        @RawRes soundRes: Int?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle {
        return postInternal(
            id = id, text = text, level = level,
            date = date, validTo = validTo,
            soundRes = soundRes, actions = actions, validityCheck = validityCheck
        )
    }

    private fun postInternal(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        date: Long,
        validTo: Long,
        @RawRes soundRes: Int?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle {
        // Clean up expired notifications piggyback on writes
        removeExpired()

        val instanceKey: Int
        val current = _notifications.value.toMutableList()

        if (id.allowMultiple) {
            instanceKey = nextInstanceKey.getAndIncrement()
        } else {
            instanceKey = id.ordinal
            // Cancel just the replaced notification's own sound — not any other concurrent alarms.
            current.filter { it.id == id }.forEach { old ->
                cancelSilentAlarmNotification(old)
            }
            current.removeAll { it.id == id }
        }

        val notification = AapsNotification(
            id = id,
            instanceKey = instanceKey,
            text = text,
            level = level,
            date = date,
            validTo = validTo,
            soundRes = soundRes,
            actions = actions,
            validityCheck = validityCheck
        )

        current.add(notification)
        current.sortBy { it.level.priority }
        _notifications.value = current

        // Alarm tier (URGENT + sound): the system notification is silent (heads-up + vibration, no
        // channel sound); the ramping audio is owned by AlarmSoundPlayer and driven by
        // refreshAlarmSound() below so concurrent URGENT alarms hand off correctly. Sound is gated
        // on URGENT — a soundRes on a lower level is intentionally ignored (only the alarm tier rings).
        if (level == NotificationLevel.URGENT && soundRes != null && soundRes != 0) {
            alarmNotificationManager.postSilentAlarmNotification(
                notificationKey = instanceKey,
                title = rh.gs(app.aaps.core.ui.R.string.urgent_alarm),
                body = text,
                urgent = true
            )
        } else if (preferences.get(BooleanKey.AlertUrgentAsAndroidNotification) && actions.isEmpty()) {
            // No-sound visual-only path (preference-gated).
            raiseSystemNotification(notification)
        }
        refreshAlarmSound()

        aapsLogger.debug(LTag.NOTIFICATION, "Notification posted: [${id.name}] $text")
        return NotificationHandle(instanceKey)
    }

    @Synchronized
    override fun post(
        id: NotificationId,
        @StringRes textRes: Int,
        vararg formatArgs: Any?,
        level: NotificationLevel,
        validMinutes: Int,
        date: Long,
        validTo: Long,
        @RawRes soundRes: Int?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle {
        val text = if (formatArgs.isEmpty()) rh.gs(textRes) else rh.gs(textRes, *formatArgs)
        val effectiveValidTo = if (validMinutes > 0) date + TimeUnit.MINUTES.toMillis(validMinutes.toLong()) else validTo
        return postInternal(
            id = id, text = text, level = level,
            date = date, validTo = effectiveValidTo,
            soundRes = soundRes, actions = actions, validityCheck = validityCheck
        )
    }

    @Synchronized
    override fun dismiss(id: NotificationId) {
        val current = _notifications.value
        val dismissed = current.filter { it.id == id }
        val filtered = current.filter { it.id != id }
        if (filtered.size != current.size) {
            dismissed.forEach { n ->
                cancelSilentAlarmNotification(n)
            }
            _notifications.value = filtered
            refreshAlarmSound()
            aapsLogger.debug(LTag.NOTIFICATION, "Notification dismissed: ${id.name}")
        }
    }

    @Synchronized
    override fun dismiss(handle: NotificationHandle) {
        val current = _notifications.value
        val dismissed = current.filter { it.instanceKey == handle.instanceKey }
        val filtered = current.filter { it.instanceKey != handle.instanceKey }
        if (filtered.size != current.size) {
            dismissed.forEach { n ->
                cancelSilentAlarmNotification(n)
            }
            _notifications.value = filtered
            refreshAlarmSound()
            aapsLogger.debug(LTag.NOTIFICATION, "Notification dismissed by handle: ${handle.instanceKey}")
        }
    }

    /**
     * Silence and dismiss every active audible alarm — the global "mute all" path used by the Wear
     * snooze/mute gesture, the full-screen acknowledge, and app onTerminate.
     *
     * Drops every audible URGENT notification from the registry so [refreshAlarmSound] stops the
     * internal ([AlarmSoundPlayer.OWNER_INTERNAL]) player and clears [soundingKey]; then stops the
     * full-screen ([AlarmSoundPlayer.OWNER_FULLSCREEN]) audio and cancels every alarm system
     * notification (FSI + the silent sound ones). A visible full-screen ErrorActivity stays on
     * screen but goes silent until the user dismisses it.
     */
    @Synchronized
    override fun muteAllAlarms() {
        val current = _notifications.value
        val audible = current.filter { it.level == NotificationLevel.URGENT && it.soundRes != null && it.soundRes != 0 }
        if (audible.isNotEmpty()) {
            audible.forEach { cancelSilentAlarmNotification(it) }
            _notifications.value = current - audible.toSet()
        }
        refreshAlarmSound()
        alarmSoundPlayer.stop(AlarmSoundPlayer.OWNER_FULLSCREEN)
        alarmNotificationManager.cancelAlarm()
        aapsLogger.debug(LTag.NOTIFICATION, "Muted all alarms")
    }

    /**
     * Mutates `_notifications.value` — must run while holding this object's monitor.
     * Both current callers (`@Synchronized postInternal` via `removeExpired()` and
     * `@Synchronized cleanUp()`) satisfy that, but the @Synchronized annotation here makes
     * the contract explicit and safe against future direct callers.
     */
    @Synchronized
    private fun removeExpired() {
        val now = System.currentTimeMillis()
        val current = _notifications.value
        val expired = current.filter { n ->
            (n.validTo != 0L && n.validTo < now) || (n.validityCheck?.invoke() == false)
        }
        if (expired.isNotEmpty()) {
            expired.forEach { n ->
                cancelSilentAlarmNotification(n)
                aapsLogger.debug(LTag.NOTIFICATION, "Notification expired: ${n.text}")
            }
            _notifications.value = current - expired.toSet()
            refreshAlarmSound()
        }
    }

    /**
     * Cancel the silent system notification for [n] (no-op if it never carried sound). The ramping
     * audio is (re)evaluated separately by [refreshAlarmSound] after the registry has changed.
     */
    private fun cancelSilentAlarmNotification(n: AapsNotification) {
        if (n.soundRes != null) alarmNotificationManager.cancelSoundAlarm(n.instanceKey)
    }

    /**
     * Re-evaluate which alarm owns the ramping audio: the highest-priority active URGENT
     * notification carrying a sound owns it; when it changes the player switches, when none remain
     * it stops. Replaces a single "currently sounding" slot so concurrent URGENT alarms hand off
     * correctly — dismissing the audible one promotes the next remaining one instead of going silent.
     *
     * Must run after every [_notifications] mutation. Uses [AlarmSoundPlayer.OWNER_INTERNAL] so it
     * never silences a full-screen (ErrorActivity) alarm.
     */
    private fun refreshAlarmSound() {
        val top = _notifications.value
            .filter { it.level == NotificationLevel.URGENT && it.soundRes != null && it.soundRes != 0 }
            .maxByOrNull { it.date }
        when {
            top == null                    ->
                if (soundingKey != null) {
                    alarmSoundPlayer.stop(AlarmSoundPlayer.OWNER_INTERNAL)
                    soundingKey = null
                }

            top.instanceKey != soundingKey -> {
                soundingKey = top.instanceKey
                alarmSoundPlayer.play(top.soundRes!!, AlarmSoundPlayer.OWNER_INTERNAL)
            }
            // else: already playing the top alarm — leave the ramp running.
        }
    }

    private fun raiseSystemNotification(n: AapsNotification) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        val largeIcon = rh.decodeResource(iconsProvider.getIcon())
        val smallIcon = iconsProvider.getNotificationIcon()
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notificationBuilder = NotificationCompat.Builder(context, NotificationManager.CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDeleteIntent(deleteIntent(n.id.ordinal))
            .setContentIntent(notificationHolder.openAppIntent(context))
        if (n.level == NotificationLevel.URGENT) {
            notificationBuilder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
                .setContentTitle(rh.gs(app.aaps.core.ui.R.string.urgent_alarm))
                .setSound(sound, AudioManager.STREAM_ALARM)
        } else {
            notificationBuilder.setVibrate(longArrayOf(0, 100, 50, 100, 50))
                .setContentTitle(rh.gs(app.aaps.core.ui.R.string.info))
        }
        mgr.notify(n.id.ordinal, notificationBuilder.build())
    }

    private fun deleteIntent(id: Int): PendingIntent {
        val intent = Intent(NotificationManager.DISMISS_ACTION).putExtra("alertID", id)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
