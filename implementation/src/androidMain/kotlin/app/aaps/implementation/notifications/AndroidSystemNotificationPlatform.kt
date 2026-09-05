package app.aaps.implementation.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import android.app.NotificationManager as AndroidNotificationManager

/**
 * The Android half of notification handling: the channel, the system tray and the alarm audio.
 * The registry that decides *which* notifications exist is shared - see `CommonNotificationManager`.
 * This class only answers "given this notification, what does Android actually do", which is the
 * question `NotificationManagerImpl` used to answer inline.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidSystemNotificationPlatform @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    private val preferences: Preferences,
    private val iconsProvider: IconsProvider,
    // Providers, not instances: AlarmNotificationManager creates its channels in its own constructor,
    // so injecting it directly would reach Android while the graph is being built. They are resolved
    // when a notification is actually shown or silenced.
    private val notificationHolder: () -> NotificationHolder,
    private val alarmNotificationManager: () -> AlarmNotificationManager,
    private val alarmSoundPlayer: () -> AlarmSoundPlayer
) : SystemNotificationPlatform {

    /** instanceKey of the alarm currently owning [AlarmSoundPlayer.OWNER_INTERNAL], null when silent. */
    private var soundingKey: Int? = null

    private var dismissCallback: ((Int) -> Unit)? = null

    /** Guards the one-off channel + receiver setup done by [ensureStarted]. */
    private var started = false

    private val notificationManager: AndroidNotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val instanceKey = intent?.getIntExtra(EXTRA_INSTANCE_KEY, -1) ?: -1
            if (instanceKey >= 0) dismissCallback?.invoke(instanceKey)
        }
    }

    /**
     * Three outcomes, and the whole reason this method takes the notification rather than a few
     * chosen fields:
     * 1. an URGENT notification **carrying a sound** is posted **silently**, because the ramping
     *    audio belongs to [AlarmSoundPlayer] through [setAudibleAlarm]. Posting it normally would
     *    alert the user twice for one alarm.
     * 2. anything else is shown only when the user asked for it with
     *    [BooleanKey.AlertUrgentAsAndroidNotification] **and** it carries no actions - a notification
     *    with actions is answered in the app, not from the tray.
     * 3. otherwise nothing is shown at all, which is a legitimate outcome here.
     */
    override fun show(notification: AapsNotification, title: String) {
        ensureStarted()
        if (notification.level == NotificationLevel.URGENT && notification.sound != null) {
            alarmNotificationManager().postSilentAlarmNotification(
                notificationKey = notification.instanceKey,
                title = title,
                body = notification.text,
                urgent = true
            )
        } else if (preferences.get(BooleanKey.AlertUrgentAsAndroidNotification) && notification.actions.isEmpty()) {
            raiseSystemNotification(notification, title)
        }
    }

    override fun cancel(instanceKey: Int) {
        // Harmless when this key never carried a sound - the alarm manager only tracks the ones it posted.
        alarmNotificationManager().cancelSoundAlarm(instanceKey)
        notificationManager.cancel(instanceKey)
    }

    /**
     * Every **alarm** this app posted, not literally every notification.
     * `AndroidNotificationManager.cancelAll()` would also take down the ongoing foreground service
     * notification that shows the loop status, which is not what "mute all alarms" means. This is the
     * same set `NotificationManagerImpl` cleared on that path.
     */
    override fun cancelAll() {
        alarmNotificationManager().cancelAlarm()
    }

    override fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?) {
        if (instanceKey == null || sound == null) {
            if (soundingKey != null) {
                soundingKey = null
                alarmSoundPlayer().stop(AlarmSoundPlayer.OWNER_INTERNAL)
            }
            return
        }
        // Called again with the same key means "keep playing" - restarting would reset the ramp.
        if (instanceKey != soundingKey) {
            soundingKey = instanceKey
            alarmSoundPlayer().play(sound, AlarmSoundPlayer.OWNER_INTERNAL)
        }
    }

    /** Just remembers the callback. The receiver it feeds is registered by [ensureStarted]. */
    override fun onDismissed(callback: (instanceKey: Int) -> Unit) {
        dismissCallback = callback
    }

    /**
     * Create the channel and register the dismiss receiver, once, on the first notification.
     * Deliberately not done when this class is built, and not in [onDismissed] either. The registry
     * calls `onDismissed` from its own constructor, so doing it there means building the object
     * graph touches Android - and the plain-JVM graph tests, which resolve every binding, fail on
     * `getSystemService` returning something that is not a NotificationManager. Nothing can be
     * dismissed before something is shown, so first show is the right moment.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun ensureStarted() {
        if (started) return
        started = true

        notificationManager.createNotificationChannel(
            NotificationChannel(NotificationManager.CHANNEL_ID, NotificationManager.CHANNEL_ID, AndroidNotificationManager.IMPORTANCE_HIGH)
        )

        val filter = IntentFilter(NotificationManager.DISMISS_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            context.registerReceiver(dismissReceiver, filter)

        aapsLogger.debug(LTag.NOTIFICATION, "Notification channel and dismiss receiver ready")
    }

    private fun raiseSystemNotification(n: AapsNotification, title: String) {
        val largeIcon = BitmapFactory.decodeResource(context.resources, iconsProvider.getIcon())
        val builder = NotificationCompat.Builder(context, NotificationManager.CHANNEL_ID)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDeleteIntent(deleteIntent(n.instanceKey))
            .setContentIntent(notificationHolder().openAppIntent())
        if (n.level == NotificationLevel.URGENT) {
            builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioManager.STREAM_ALARM)
        } else {
            builder.setVibrate(longArrayOf(0, 100, 50, 100, 50))
        }
        // Keyed by instanceKey, which the registry also cancels by. For everything that cannot appear
        // twice that is the same number as before (instanceKey == id.ordinal); for the few that can, it
        // is what stops a second one replacing the first in the tray.
        notificationManager.notify(n.instanceKey, builder.build())
    }

    private fun deleteIntent(instanceKey: Int): PendingIntent {
        val intent = Intent(NotificationManager.DISMISS_ACTION).putExtra(EXTRA_INSTANCE_KEY, instanceKey)
        return PendingIntent.getBroadcast(
            context, instanceKey, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {

        /**
         * Was `alertID` carrying a `NotificationId` ordinal. The registry dismisses by instance now,
         * so the extra carries the instance key; for anything that cannot appear twice the two are
         * the same number.
         */
        const val EXTRA_INSTANCE_KEY = "instanceKey"
    }
}
