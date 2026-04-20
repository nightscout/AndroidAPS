package info.nightscout.androidaps.plugins.pump.carelevo.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.ext.transformNotificationStringResources
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.model.AlarmEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarelevoAlarmNotifier @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val dateUtil: DateUtil,
    private val notificationManager: app.aaps.core.interfaces.notifications.NotificationManager,
    private val sp: SP,
    private val alarmActionHandler: CarelevoAlarmActionHandler
) {

    private val disposables = CompositeDisposable()
    private val _alarms = MutableStateFlow<List<CarelevoAlarmInfo>>(emptyList())
    val alarms = _alarms.asStateFlow()
    private var onAlarmsUpdated: ((List<CarelevoAlarmInfo>) -> Unit)? = null
    private val channelId = "carelevo_alarm_channel"

    fun startObserving(
        onAlarmsUpdated: (List<CarelevoAlarmInfo>) -> Unit
    ) {
        this.onAlarmsUpdated = onAlarmsUpdated
        createNotificationChannel()

        disposables += alarmActionHandler.observeAlarms()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { alarms -> handleAlarmsInternal(alarms) },
                { e -> aapsLogger.error(LTag.PUMP, "[CarelevoAlarmNotifier] observeAlarms.error error=$e") }
            )
    }

    fun refreshAlarms(includeUnacknowledged: Boolean = false) {
        disposables += alarmActionHandler.getAlarmsOnce(includeUnacknowledged)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { alarms -> handleAlarmsInternal(alarms) },
                { e -> aapsLogger.error(LTag.PUMP, "[CarelevoAlarmNotifier] refreshAlarms.error error=$e") }
            )
    }

    private fun handleAlarmsInternal(alarms: List<CarelevoAlarmInfo>) {
        aapsLogger.debug(LTag.PUMP, "[CarelevoAlarmNotifier] handleAlarmsInternal alarms=$alarms")
        _alarms.value = alarms

        if (!isInForeground) {
            alarms.forEach { alarm ->
                showNotification(alarm)
            }
        }

        onAlarmsUpdated?.invoke(alarms)
    }

    fun showTopNotification(alarms: List<CarelevoAlarmInfo>) {
        alarms.forEach { newAlarm ->
            val (titleRes, descRes, btnRes) = newAlarm.cause.transformNotificationStringResources()

            val descArgs = buildDescArgsFor(newAlarm)
            val desc = buildDescription(descRes, descArgs)
            aapsLogger.debug(LTag.PUMP, "[CarelevoAlarmNotifier] showTopNotification titleRes=$titleRes descArgs=$descArgs desc=$desc")
            notificationManager.post(
                id = NotificationId.COMBO_PUMP_ALARM,
                text = context.getString(titleRes) + "\n" + HtmlCompat.fromHtml(desc, HtmlCompat.FROM_HTML_MODE_LEGACY),
                level = NotificationLevel.NORMAL,
                actions = listOf(
                    NotificationAction(btnRes) {
                        alarmActionHandler.triggerEvent(AlarmEvent.ClearAlarm(info = newAlarm))
                    }
                ),
                date = dateUtil.now(),
                validTo = dateUtil.now() + T.mins(1).msecs(),
                soundRes = null,
                validityCheck = null,
            )
        }
    }

    fun stopObserving() {
        disposables.clear()
    }

    fun showNotification(alarm: CarelevoAlarmInfo) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val (titleRes, descRes, _) = alarm.cause.transformNotificationStringResources()
        val description = buildNotificationDescription(alarm, descRes)

        val contentPendingIntent = createAlarmActivityPendingIntent()

        val notification = buildNotification(
            title = context.getString(titleRes),
            description = description,
            contentIntent = contentPendingIntent
        )

        notificationManager.notify(alarm.alarmId.hashCode(), notification)
    }

    /** Builds the final body text from the alarm metadata and descRes. */
    private fun buildNotificationDescription(
        alarm: CarelevoAlarmInfo,
        @StringRes descRes: Int?
    ): String {
        if (descRes == null) return ""
        return when (alarm.cause) {
            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            AlarmCause.ALARM_NOTICE_LOW_INSULIN -> {
                val remain = (alarm.value ?: 0).toString()
                context.getString(descRes, remain)
            }

            AlarmCause.ALARM_NOTICE_PATCH_EXPIRED -> {
                formatPatchExpired(descRes, alarm.value ?: 0)
            }

            AlarmCause.ALARM_NOTICE_BG_CHECK -> {
                val span = formatBgCheckSpan(alarm.value ?: 0)
                context.getString(descRes, span)
            }

            else -> context.getString(descRes)
        }
    }

    private fun formatPatchExpired(@StringRes descRes: Int, totalHours: Int): String {
        val days = totalHours / 24
        val remainHours = totalHours % 24
        return context.getString(descRes, days, remainHours)
    }

    private fun formatBgCheckSpan(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(
                R.string.common_label_unit_value_duration_hour_and_minute,
                hours, minutes
            )
        } else {
            context.getString(
                R.string.common_label_unit_value_minute,
                minutes
            )
        }
    }

    private fun buildNotification(
        title: String,
        description: String,
        contentIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(app.aaps.core.ui.R.drawable.notif_icon)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun createAlarmActivityPendingIntent(): PendingIntent {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            flags
        )
    }

    private fun createNotificationChannel() {
        val name = "Carelevo Alarm Channel"
        val descriptionText = "케어레보 패치 알람 알림 채널"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun cancelNotification(alarmId: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId.hashCode())
    }

    private fun playBeep() {
        val player = MediaPlayer.create(context, app.aaps.core.ui.R.raw.error) // res/raw/alarm_sound.mp3
        player.setOnCompletionListener { it.release() }
        player.start()
    }

    private fun buildDescArgsFor(alarm: CarelevoAlarmInfo): List<String> = when (alarm.cause) {
        AlarmCause.ALARM_NOTICE_LOW_INSULIN,
        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN -> {
            val lowInsulinNoticeAmount = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key, 30)
            listOf((lowInsulinNoticeAmount).toString())
        }

        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED -> {
            val expiry = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 116)
            aapsLogger.debug(LTag.PUMP, "[CarelevoAlarmNotifier] buildDescArgsFor alarm=${alarm.value} expiry=$expiry")
            val (days, hours) = splitDaysAndHours(expiry)
            listOf(days.toString(), hours.toString())
        }

        AlarmCause.ALARM_NOTICE_BG_CHECK -> {
            val totalMinutes = alarm.value ?: 0
            listOf(formatBgCheckDuration(totalMinutes))
        }

        else -> emptyList()
    }

    private fun buildDescription(@StringRes descRes: Int?, args: List<String>): String {
        return descRes?.let { resId ->
            if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args.toTypedArray())
        } ?: ""
    }

    private fun splitDaysAndHours(totalHours: Int): Pair<Int, Int> {
        val days = totalHours / 24
        val hours = totalHours % 24
        return days to hours
    }

    private fun formatBgCheckDuration(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 ->
                context.getString(R.string.common_label_unit_value_duration_hour_and_minute, hours, minutes)
            hours > 0 ->
                context.getString(R.string.common_label_unit_value_duration_hour, hours)
            else ->
                context.getString(R.string.common_label_unit_value_minute, minutes)
        }
    }

    val isInForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
