package app.aaps.cgm.dexcomg7.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.data.G7StringNonKey
import app.aaps.cgm.dexcomg7.protocol.G7Trend
import app.aaps.cgm.dexcomg7.ui.DexcomG7Formatting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Calendar
import kotlin.math.abs

/**
 * Runs the glucose and sensor alarms: feeds [G7AlarmEngine] with every change of the sensor state and
 * does what it decides - a notification with an Acknowledge button, a vibration, and a sound from
 * [G7AlarmPlayer] on the alarm stream, so it is heard with the phone on silent.
 *
 * The phone may sleep between two readings, so reminders, snooze ends and signal loss are also
 * scheduled with [AlarmManager], which wakes it up even in Doze.
 *
 * Every alarm also goes to the AAPS watch app ([EventData.CgmAlarm]), which vibrates for the time set
 * for that alarm (a separate time at night) and has its own Acknowledge button. An acknowledge on the
 * watch, or the watch's snooze button, comes back here and acknowledges on the phone too. Nothing
 * happens there unless the Wear plugin is on and the watch app is installed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@Inject
class G7Alarms(
    private val context: Context,
    private val store: G7StateStore,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val iconsProvider: IconsProvider,
    private val rxBus: RxBus,
    private val player: G7AlarmPlayer,
    private val aapsLogger: AAPSLogger
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val json = Json { ignoreUnknownKeys = true }
    private var job: Job? = null
    private var receiverRegistered = false

    // --- only touched from [scope], which runs one thing at a time ---
    private var playing: G7AlarmType? = null

    private val _runtime = MutableStateFlow(load())

    /** The alarms and where each stands, for the status screen. */
    val runtime: StateFlow<G7AlarmRuntime> = _runtime.asStateFlow()

    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            scope.launch {
                try {
                    when (intent.action) {
                        ACTION_ACKNOWLEDGE -> doAcknowledge(intent.getStringExtra(EXTRA_TYPE)?.let { name -> G7AlarmType.entries.firstOrNull { it.name == name } })
                        ACTION_CHECK       -> check(store.value)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    fun start() {
        createChannel()
        if (!receiverRegistered) {
            context.registerReceiver(receiver, IntentFilter().apply { addAction(ACTION_ACKNOWLEDGE); addAction(ACTION_CHECK) }, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        job?.cancel()
        job = scope.launch {
            launch { store.state.collect { check(it) } }
            // From the AAPS watch app: its Acknowledge button, and its snooze button (which silences every alarm).
            launch { rxBus.toFlow(EventData.CgmAlarmAcknowledge::class).collect { ack -> G7AlarmType.entries.firstOrNull { it.name == ack.id }?.let { doAcknowledge(it) } } }
            launch { rxBus.toFlow(EventData.SnoozeAlert::class).collect { doAcknowledge(null) } }
            // Settings changes and time based conditions, while the phone is awake.
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                check(store.value)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        scope.launch { execute(G7AlarmEngine.clearAll(_runtime.value), config()) }
    }

    /** Acknowledge [type], or every alarm going off when null. */
    fun acknowledge(type: G7AlarmType? = null) {
        scope.launch { doAcknowledge(type) }
    }

    /** Plays the chosen sound of [type] once, so the user can hear it. */
    fun testSound(type: G7AlarmType) {
        val cfg = config()
        player.play(cfg[type].sound, loop = false, minimumVolumePercent = preferences.get(G7AlarmKeys.MinimumVolume))
        player.vibrate(type.urgent)
    }

    private fun doAcknowledge(type: G7AlarmType?) {
        val config = config()
        aapsLogger.debug(LTag.BGSOURCE, "Dexcom G7: alarm acknowledged: ${type ?: "all"}")
        execute(G7AlarmEngine.acknowledge(_runtime.value, config, type, System.currentTimeMillis()), config)
        // The engine gives the next snooze end only; a new check works out everything else.
        check(store.value)
    }

    private fun check(state: G7State) {
        val config = config()
        execute(G7AlarmEngine.step(state, config, _runtime.value, System.currentTimeMillis()), config)
    }

    private fun execute(step: G7AlarmStep, config: G7AlarmConfig) {
        if (step.runtime != _runtime.value) {
            _runtime.value = step.runtime
            preferences.put(G7StringNonKey.AlarmState, json.encodeToString(G7AlarmRuntime.serializer(), step.runtime))
        }
        for (action in step.actions.filterIsInstance<G7AlarmAction.Clear>()) {
            manager?.cancel(notificationId(action.type))
            rxBus.send(EventMobileToWear(EventData.CgmAlarmCancel(action.type.name)))
            if (playing == action.type) {
                player.stop()
                playing = null
            }
        }
        val raises = step.actions.filterIsInstance<G7AlarmAction.Raise>()
        raises.forEach {
            notify(it)
            sendToWatch(it.type)
        }
        // Most important first. A sound already playing for a more important alarm keeps playing.
        raises.firstOrNull()?.let { top ->
            aapsLogger.debug(LTag.BGSOURCE, "Dexcom G7: alarm ${top.type} (sound ${top.withSound}, reminder ${top.reminder})")
            player.vibrate(top.type.urgent)
            val current = playing
            if (top.withSound && (current == null || current.ordinal >= top.type.ordinal || !step.runtime[current].isRaised)) {
                player.play(config[top.type].sound, preferences.get(G7AlarmKeys.SoundUntilAcknowledged), preferences.get(G7AlarmKeys.MinimumVolume))
                playing = top.type
            }
        }
        if (step.runtime.raised.isEmpty() && playing != null) {
            player.stop()
            playing = null
        }
        schedule(step.nextCheckAt)
    }

    private fun config(): G7AlarmConfig = G7AlarmConfig(
        enabled = preferences.get(G7AlarmKeys.AlarmsEnabled),
        types = G7AlarmType.entries.associateWith { type ->
            val keys = G7AlarmKeys.of(type)
            G7AlarmTypeConfig(
                enabled = keys.enabled?.let { preferences.get(it) } ?: true,
                levelMgdl = keys.level?.let { profileUtil.convertToMgdlDetect(preferences.get(it)) },
                rate = keys.rate?.let { preferences.get(it) },
                delayMinutes = keys.delay?.let { preferences.get(it) } ?: 0,
                repeatMinutes = preferences.get(keys.repeat),
                vibrateFirst = preferences.get(keys.vibrateFirst),
                sound = G7AlarmSound.byId(preferences.get(keys.sound)) ?: type.defaultSound
            )
        }
    )

    private fun notify(raise: G7AlarmAction.Raise) {
        val type = raise.type
        val acknowledge = PendingIntent.getBroadcast(
            context, ACKNOWLEDGE_REQUEST_BASE + type.ordinal,
            Intent(ACTION_ACKNOWLEDGE).setPackage(context.packageName).putExtra(EXTRA_TYPE, type.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, OPEN_REQUEST, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val body = body(type, store.value)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setContentTitle(rh.gs(type.title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            // Not copied to a watch: the AAPS watch app shows its own, with the vibration set here.
            .setLocalOnly(true)
            .setContentIntent(open)
            // From Android 14 an ongoing notification can be swiped away. Take that as an acknowledge.
            .setDeleteIntent(acknowledge)
            .addAction(0, rh.gs(R.string.dexcom_g7_alarm_acknowledge), acknowledge)
            .build()
        try {
            manager?.notify(notificationId(type), notification)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: alarm notification not allowed", e)
        }
    }

    private fun sendToWatch(type: G7AlarmType) {
        val keys = G7AlarmKeys.of(type)
        val seconds = preferences.get(if (isNight()) keys.watchVibrationNight else keys.watchVibration)
        rxBus.send(EventMobileToWear(EventData.CgmAlarm(type.name, rh.gs(type.title), body(type, store.value), seconds, type.urgent)))
    }

    private fun isNight(): Boolean {
        if (!preferences.get(G7AlarmKeys.WatchNight)) return false
        val now = Calendar.getInstance()
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return G7AlarmNight.isNight(minuteOfDay, preferences.get(G7AlarmKeys.WatchNightStart), preferences.get(G7AlarmKeys.WatchNightEnd))
    }

    /** What the notification says. */
    fun body(type: G7AlarmType, state: G7State): String = when (type) {
        G7AlarmType.URGENT_LOW, G7AlarmType.URGENT_LOW_SOON, G7AlarmType.LOW, G7AlarmType.HIGH, G7AlarmType.FALL_RATE, G7AlarmType.RISE_RATE -> {
            val glucose = state.latestGlucose?.let { profileUtil.fromMgdlToStringWithUnits(it.toDouble()) } ?: "---"
            val arrow = DexcomG7Formatting.arrow(state.latestTrendRate?.takeIf { abs(it) <= G7Trend.MAXIMUM_RATE_FOR_ARROW }?.let { G7Trend.fromRate(it) })
            rh.gs(R.string.dexcom_g7_alarm_body_glucose, glucose, arrow)
        }

        G7AlarmType.SENSOR_FAILED                                                                                                           -> rh.gs(R.string.dexcom_g7_alert_sensor_failed)
        G7AlarmType.SENSOR_ENDED                                                                                                            -> rh.gs(R.string.dexcom_g7_alert_session_ended)
        G7AlarmType.SIGNAL_LOSS                                                                                                             ->
            rh.gs(R.string.dexcom_g7_alarm_body_signal_loss, state.latestReadingAt?.let { dateUtil.timeString(it) } ?: "?")

        G7AlarmType.SENSOR_ISSUE                                                                                                            -> rh.gs(R.string.dexcom_g7_alarm_body_sensor_issue)
    }

    private fun schedule(at: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, CHECK_REQUEST, Intent(ACTION_CHECK).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (at == null) {
            am.cancel(pending)
            return
        }
        try {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: could not schedule the alarm check", e)
        }
    }

    private fun createChannel() {
        // No sound and no vibration on the channel: the player does both, on the alarm stream.
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, rh.gs(R.string.dexcom_g7_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun load(): G7AlarmRuntime {
        val raw = preferences.get(G7StringNonKey.AlarmState)
        if (raw.isBlank()) return G7AlarmRuntime()
        return runCatching { json.decodeFromString(G7AlarmRuntime.serializer(), raw) }.getOrDefault(G7AlarmRuntime())
    }

    private fun notificationId(type: G7AlarmType) = NOTIFICATION_ID_BASE + type.ordinal

    companion object {

        const val CHANNEL_ID = "dexcom_g7_alarms"
        const val CHECK_INTERVAL_MS = 60_000L
        private const val ACTION_ACKNOWLEDGE = "app.aaps.cgm.dexcomg7.ALARM_ACKNOWLEDGE"
        private const val ACTION_CHECK = "app.aaps.cgm.dexcomg7.ALARM_CHECK"
        private const val EXTRA_TYPE = "type"

        // Clear of the ids AAPS uses (4711..4714, 100000+).
        private const val NOTIFICATION_ID_BASE = 7700
        private const val ACKNOWLEDGE_REQUEST_BASE = 7700
        private const val CHECK_REQUEST = 7690
        private const val OPEN_REQUEST = 7691
    }
}
