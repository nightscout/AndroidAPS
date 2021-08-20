package info.nightscout.androidaps.plugins.general.overview.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.services.AlarmSoundServiceHelper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.IconsProvider
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationStore @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val context: Context,
    private val iconsProvider: IconsProvider,
    private val alarmSoundServiceHelper: AlarmSoundServiceHelper,
    private val dateUtil: DateUtil
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
            if (usesChannels && n.soundId != null && n.soundId != 0) alarmSoundServiceHelper.startAlarm(context, n.soundId)
        } else {
            if (n.soundId != null && n.soundId != 0) alarmSoundServiceHelper.startAlarm(context, n.soundId)
        }
        Collections.sort(store, NotificationComparator())
        return true
    }

    @Synchronized fun remove(id: Int): Boolean {
        for (i in store.indices) {
            if (store[i].id == id) {
                if (store[i].soundId != null) alarmSoundServiceHelper.stopService(context)
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
            if (n.validTo != 0L && n.validTo < System.currentTimeMillis()) {
                store.removeAt(i)
                i--
            }
            i++
        }
    }

    private fun raiseSystemNotification(n: Notification) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val largeIcon = resourceHelper.decodeResource(iconsProvider.getIcon())
        val smallIcon = iconsProvider.getNotificationIcon()
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDeleteIntent(deleteIntent(n.id))
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

    private fun deleteIntent(id: Int): PendingIntent {
        val intent = Intent(context, DismissNotificationService::class.java)
        intent.putExtra("alertID", id)
        return PendingIntent.getService(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun createNotificationChannel() {
        usesChannels = true
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        @SuppressLint("WrongConstant") val channel = NotificationChannel(CHANNEL_ID,
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH)
        mNotificationManager.createNotificationChannel(channel)
    }

    @Synchronized
    fun updateNotifications(notificationsView: RecyclerView) {
        removeExpired()
//        unSnooze()
        if (store.size > 0) {
            val adapter = NotificationRecyclerViewAdapter(cloneStore())
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

    inner class NotificationRecyclerViewAdapter internal constructor(private val notificationsList: List<Notification>) : RecyclerView.Adapter<NotificationRecyclerViewAdapter.NotificationsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): NotificationsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.overview_notification_item, viewGroup, false)
            return NotificationsViewHolder(v)
        }

        override fun onBindViewHolder(holder: NotificationsViewHolder, position: Int) {
            val notification = notificationsList[position]
            holder.dismiss.tag = notification
            if (notification.buttonText != 0) holder.dismiss.setText(notification.buttonText)
            else holder.dismiss.setText(R.string.snooze)
            @Suppress("SetTextI18n")
            holder.text.text = dateUtil.timeString(notification.date) + " " + notification.text
            when (notification.level) {
                Notification.URGENT -> holder.cv.setBackgroundColor(resourceHelper.gc(R.color.notificationUrgent))
                Notification.NORMAL -> holder.cv.setBackgroundColor(resourceHelper.gc(R.color.notificationNormal))
                Notification.LOW -> holder.cv.setBackgroundColor(resourceHelper.gc(R.color.notificationLow))
                Notification.INFO -> holder.cv.setBackgroundColor(resourceHelper.gc(R.color.notificationInfo))
                Notification.ANNOUNCEMENT -> holder.cv.setBackgroundColor(resourceHelper.gc(R.color.notificationAnnouncement))
            }
        }

        override fun getItemCount(): Int {
            return notificationsList.size
        }

        inner class NotificationsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var cv: CardView = itemView.findViewById(R.id.notification_cardview)
            var text: TextView = itemView.findViewById(R.id.notification_text)
            var dismiss: Button = itemView.findViewById(R.id.notification_dismiss)

            init {
                dismiss.setOnClickListener {
                    val notification = it.tag as Notification
                    rxBus.send(EventDismissNotification(notification.id))
                    notification.action?.run()
                }
            }
        }
    }
}