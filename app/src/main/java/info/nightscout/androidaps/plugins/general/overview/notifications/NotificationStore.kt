package info.nightscout.androidaps.plugins.general.overview.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by mike on 03.12.2016.
 */
@Singleton
class NotificationStore @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val resourceHelper: ResourceHelper,
    private val mainApp: MainApp
) {

    var store: MutableList<Notification> = ArrayList()
    private var usesChannels = false

    companion object {
        private const val CHANNEL_ID = "AndroidAPS-Overview"
    }

    inner class NotificationComparator : Comparator<Notification> {
        override fun compare(o1: Notification, o2: Notification): Int {
            return o1.level - o2.level
        }
    }

    @Synchronized
    fun add(n: Notification): Boolean {
        aapsLogger.debug(LTag.NOTIFICATION, "Notification received: " + n.text)
        for (storeNotification in store) {
            if (storeNotification.id == n.id) {
                storeNotification.date = n.date
                storeNotification.validTo = n.validTo
                return false
            }
        }
        store.add(n)
        if (sp.getBoolean(R.string.key_raise_notifications_as_android_notifications, false) && n !is NotificationWithAction) {
            raiseSystemNotification(n)
            if (usesChannels && n.soundId != null && n.soundId != 0) {
                val alarm = Intent(mainApp, AlarmSoundService::class.java)
                alarm.putExtra("soundid", n.soundId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mainApp.startForegroundService(alarm) else mainApp.startService(alarm)
            }
        } else {
            if (n.soundId != null && n.soundId != 0) {
                val alarm = Intent(mainApp, AlarmSoundService::class.java)
                alarm.putExtra("soundid", n.soundId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mainApp.startForegroundService(alarm) else mainApp.startService(alarm)
            }
        }
        Collections.sort(store, NotificationComparator())
        return true
    }

    @Synchronized fun remove(id: Int): Boolean {
        for (i in store.indices) {
            if (store[i].id == id) {
                if (store[i].soundId != null) {
                    val alarm = Intent(mainApp, AlarmSoundService::class.java)
                    mainApp.stopService(alarm)
                }
                store.removeAt(i)
                return true
            }
        }
        return false
    }

    @Synchronized private fun removeExpired() {
        var i = 0
        while (i < store.size) {
            val n = store[i]
            if (n.validTo.time != 0L && n.validTo.time < System.currentTimeMillis()) {
                store.removeAt(i)
                i--
            }
            i++
        }
    }

    fun snoozeTo(timeToSnooze: Long) {
        aapsLogger.debug(LTag.NOTIFICATION, "Snoozing alarm until: $timeToSnooze")
        sp.putLong("snoozedTo", timeToSnooze)
    }

    private fun unSnooze() {
        if (Notification.isAlarmForStaleData()) {
            val notification = Notification(Notification.NSALARM, resourceHelper.gs(R.string.nsalarm_staledata), Notification.URGENT)
            sp.putLong("snoozedTo", System.currentTimeMillis())
            add(notification)
            aapsLogger.debug(LTag.NOTIFICATION, "Snoozed to current time and added back notification!")
        }
    }

    private fun raiseSystemNotification(n: Notification) {
        val mgr = mainApp.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val largeIcon = BitmapFactory.decodeResource(mainApp.resources, MainApp.getIcon())
        val smallIcon = MainApp.getNotificationIcon()
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notificationBuilder = NotificationCompat.Builder(mainApp, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setContentText(n.text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDeleteIntent(DismissNotificationService.deleteIntent(n.id))
        if (n.level == Notification.URGENT) {
            notificationBuilder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
                .setContentTitle(resourceHelper.gs(R.string.urgent_alarm))
                .setSound(sound, AudioManager.STREAM_ALARM)
        } else {
            notificationBuilder.setVibrate(longArrayOf(0, 100, 50, 100, 50))
                .setContentTitle(resourceHelper.gs(R.string.info))
        }
        mgr.notify(n.id, notificationBuilder.build())
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            usesChannels = true
            val mNotificationManager = mainApp.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            @SuppressLint("WrongConstant") val channel = NotificationChannel(CHANNEL_ID,
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_HIGH)
            mNotificationManager.createNotificationChannel(channel)
        }
    }

    @Synchronized
    fun updateNotifications(notificationsView: RecyclerView) {
        removeExpired()
        unSnooze()
        if (store.size > 0) {
            val adapter = NotificationRecyclerViewAdapter(this, cloneStore())
            notificationsView.adapter = adapter
            notificationsView.visibility = View.VISIBLE
        } else {
            notificationsView.visibility = View.GONE
        }
    }

    @Synchronized
    private fun cloneStore(): List<Notification> {
        val clone: MutableList<Notification> = ArrayList(store.size)
        clone.addAll(store)
        return clone
    }

}