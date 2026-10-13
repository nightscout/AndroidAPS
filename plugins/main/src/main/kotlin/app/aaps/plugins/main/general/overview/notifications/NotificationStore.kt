package app.aaps.plugins.main.general.overview.notifications

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
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.OverviewNotificationItemBinding
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationStore @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val context: Context,
    private val iconsProvider: IconsProvider,
    private val uiInteraction: UiInteraction,
    private val dateUtil: DateUtil,
    private val notificationHolder: NotificationHolder,
    private val activePlugin: ActivePlugin
) {

    private var store: MutableList<Notification> = ArrayList()

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
        if (preferences.get(BooleanKey.AlertUrgentAsAndroidNotification) && n !is NotificationWithAction)
            raiseSystemNotification(n)
        if (n.soundId != null && n.soundId != 0) uiInteraction.startAlarm(n.soundId!!, n.text)
        Collections.sort(store, NotificationComparator())
        return true
    }

    @Synchronized
    fun remove(id: Int): Boolean {
        for (i in store.indices) {
            if (store[i].id == id) {
                if (store[i].soundId != null) uiInteraction.stopAlarm("Removed " + store[i].text)
                aapsLogger.debug(LTag.NOTIFICATION, "Notification removed: " + store[i].text)
                store.removeAt(i)
                return true
            }
        }
        return false
    }

    @Synchronized
    private fun removeExpired() {
        var i = 0
        while (i < store.size) {
            val n = store[i]
            if (n.validTo != 0L && n.validTo < System.currentTimeMillis()) {
                if (store[i].soundId != null) uiInteraction.stopAlarm("Expired " + store[i].text)
                aapsLogger.debug(LTag.NOTIFICATION, "Notification expired: " + store[i].text)
                store.removeAt(i)
                i--
            }
            if (n is NotificationWithAction) {
                if (n.validityCheck?.invoke() == false) {
                    store.removeAt(i)
                    i--
                }
            }
            i++
        }
    }

    private fun raiseSystemNotification(n: Notification) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val largeIcon = rh.decodeResource(iconsProvider.getIcon())
        val smallIcon = iconsProvider.getNotificationIcon()
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDeleteIntent(deleteIntent(n.id))
            .setContentIntent(notificationHolder.openAppIntent(context))
        if (n.level == Notification.URGENT) {
            notificationBuilder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
                .setContentTitle(rh.gs(app.aaps.core.ui.R.string.urgent_alarm))
                .setSound(sound, AudioManager.STREAM_ALARM)
        } else {
            notificationBuilder.setVibrate(longArrayOf(0, 100, 50, 100, 50))
                .setContentTitle(rh.gs(app.aaps.core.ui.R.string.info))
        }
        mgr.notify(n.id, notificationBuilder.build())
    }

    private fun deleteIntent(id: Int): PendingIntent {
        val intent = Intent(DismissNotificationReceiver.ACTION).putExtra("alertID", id)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun createNotificationChannel() {
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH)
        mNotificationManager.createNotificationChannel(channel)
    }

    @Synchronized
    fun updateNotifications(notificationsView: RecyclerView) {
        removeExpired()
        val clonedStore = ArrayList(store)
        if (clonedStore.isNotEmpty()) {
            val adapter = NotificationRecyclerViewAdapter(clonedStore)
            notificationsView.adapter = adapter
            notificationsView.visibility = View.VISIBLE
        } else {
            notificationsView.visibility = View.GONE
        }
    }

    inner class NotificationRecyclerViewAdapter internal constructor(private val notificationsList: List<Notification>) :
        RecyclerView.Adapter<NotificationRecyclerViewAdapter.NotificationsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): NotificationsViewHolder =
            NotificationsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.overview_notification_item, viewGroup, false))

        override fun onBindViewHolder(holder: NotificationsViewHolder, position: Int) {
            val notification = notificationsList[position]
            holder.binding.dismiss.tag = notification
            if (notification.buttonText != 0) holder.binding.dismiss.setText(notification.buttonText)
            else holder.binding.dismiss.setText(app.aaps.core.ui.R.string.snooze)
            @Suppress("SetTextI18n")
            holder.binding.text.text = dateUtil.timeString(notification.date) + " " + notification.text
            when (notification.level) {
                Notification.URGENT       -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationUrgent))
                Notification.NORMAL       -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationNormal))
                Notification.LOW          -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationLow))
                Notification.INFO         -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationInfo))
                Notification.ANNOUNCEMENT -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationAnnouncement))
            }
        }

        override fun getItemCount(): Int {
            return notificationsList.size
        }

        inner class NotificationsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = OverviewNotificationItemBinding.bind(itemView)

            init {
                binding.dismiss.setOnClickListener {
                    val notification = it.tag as Notification
                    notification.contextForAction = itemView.context
                    notification.action?.run()
                    if (remove(notification.id)) activePlugin.activeOverview.overviewBus.send(EventUpdateOverviewNotification("NotificationCleared"))
                }
            }
        }
    }
}