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
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
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
    private val uiInteraction: UiInteraction
) : NotificationManager {

    private val _notifications = MutableStateFlow<List<AapsNotification>>(emptyList())
    override val notifications: StateFlow<List<AapsNotification>> = _notifications.asStateFlow()

    private val nextInstanceKey = AtomicInteger(10000)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
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
    override fun createNotificationChannel() {
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
            instanceKey = id.legacyId
            // Stop alarm for replaced notification if it had sound
            current.filter { it.id == id }.forEach { old ->
                if (old.soundRes != null) uiInteraction.stopAlarm("Replaced ${old.text}")
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

        // Start alarm if sound specified
        if (soundRes != null && soundRes != 0) {
            uiInteraction.startAlarm(soundRes, text)
        }

        // Raise Android system notification if pref enabled and no action buttons
        if (preferences.get(BooleanKey.AlertUrgentAsAndroidNotification) && actions.isEmpty()) {
            raiseSystemNotification(notification)
        }

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
                if (n.soundRes != null) uiInteraction.stopAlarm("Dismissed ${n.text}")
            }
            _notifications.value = filtered
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
                if (n.soundRes != null) uiInteraction.stopAlarm("Dismissed ${n.text}")
            }
            _notifications.value = filtered
            aapsLogger.debug(LTag.NOTIFICATION, "Notification dismissed by handle: ${handle.instanceKey}")
        }
    }

    private fun removeExpired() {
        val now = System.currentTimeMillis()
        val current = _notifications.value
        val expired = current.filter { n ->
            (n.validTo != 0L && n.validTo < now) || (n.validityCheck?.invoke() == false)
        }
        if (expired.isNotEmpty()) {
            expired.forEach { n ->
                if (n.soundRes != null) uiInteraction.stopAlarm("Expired ${n.text}")
                aapsLogger.debug(LTag.NOTIFICATION, "Notification expired: ${n.text}")
            }
            _notifications.value = current - expired.toSet()
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
            .setDeleteIntent(deleteIntent(n.id.legacyId))
            .setContentIntent(notificationHolder.openAppIntent(context))
        if (n.level == NotificationLevel.URGENT) {
            notificationBuilder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
                .setContentTitle(rh.gs(app.aaps.core.ui.R.string.urgent_alarm))
                .setSound(sound, AudioManager.STREAM_ALARM)
        } else {
            notificationBuilder.setVibrate(longArrayOf(0, 100, 50, 100, 50))
                .setContentTitle(rh.gs(app.aaps.core.ui.R.string.info))
        }
        mgr.notify(n.id.legacyId, notificationBuilder.build())
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val legacyId = intent?.getIntExtra("alertID", -1) ?: -1
            NotificationId.fromLegacyId(legacyId)?.let { dismiss(it) }
        }
    }

    private fun deleteIntent(id: Int): PendingIntent {
        val intent = Intent(NotificationManager.DISMISS_ACTION).putExtra("alertID", id)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
