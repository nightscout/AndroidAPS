package app.aaps.wear.g7

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aaps.cgm.dexcomg7.ble.G7Bluetooth
import app.aaps.cgm.dexcomg7.data.G7CommLog
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.G7DisplayType
import app.aaps.cgm.dexcomg7.session.G7ConnectionStatus
import app.aaps.cgm.dexcomg7.session.G7Reading
import app.aaps.cgm.dexcomg7.session.G7Session
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.comm.DataHandlerWear
import app.aaps.wear.data.ComplicationDataRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Direct-to-watch, watch side. When the phone turns it on, the watch connects to the Dexcom G7 itself, in
 * the sensor's watch slot, with its own key, next to the phone. Its readings go to the faces like the
 * phone's do; the newest one is shown.
 *
 * The session runs in the app process, which the data layer's foreground service keeps alive. It needs
 * Android 12 (the Bluetooth permissions it uses); older watches report [EventData.G7WatchState.NOT_SUPPORTED].
 *
 * The phone owns the glucose alarms. The watch raises only a low or urgent low of its own, and only while
 * the phone has sent nothing for [G7WatchReadings.PHONE_SILENT_MS].
 */
@OptIn(FlowPreview::class)
@SingleIn(AppScope::class)
@Inject
class G7WatchDirect(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val store: G7StateStore,
    private val session: G7Session,
    private val bluetooth: G7Bluetooth,
    private val commLog: G7CommLog,
    private val repository: ComplicationDataRepository,
    private val dataHandler: DataHandlerWear
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var started = false

    /** Start time counts as "phone heard": the phone gets five quiet minutes after a restart of the watch app. */
    private var lastPhoneReadingAt = System.currentTimeMillis()
    private var raisedLow: G7WatchReadings.Low? = null
    private var raisedLowAt = 0L
    private var lastReported: EventData.G7WatchStatus? = null

    private val _status = MutableStateFlow(EventData.G7WatchStatus(EventData.G7WatchState.OFF))

    /** For the watch's own screen. */
    val status: StateFlow<EventData.G7WatchStatus> = _status.asStateFlow()

    /** Log lines up to this time have gone to the phone. */
    private var logSentUpTo = 0L

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * False when the watch may put the app to sleep in the background. A Samsung watch then freezes it as
     * soon as the screen dims (seen on a Galaxy Watch Ultra 2: frozen for minutes, foreground service or
     * not), and the sensor's readings are missed. Unrestricted battery use stops that.
     */
    val isBatteryUnrestricted: Boolean
        get() = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun start() {
        if (started) return
        started = true
        scope.launch { rxBus.toFlow(EventData.G7WatchDirect::class).collect { handle(it) } }
        scope.launch {
            rxBus.toFlow(EventData.SingleBg::class).collect { if (it.dataset == 0 && !it.fromSensor) lastPhoneReadingAt = System.currentTimeMillis() }
        }
        if (!isSupported) {
            _status.value = EventData.G7WatchStatus(EventData.G7WatchState.NOT_SUPPORTED)
            return
        }
        scope.launch { session.readings.collect { onReadings(it) } }
        scope.launch {
            combine(store.state, session.status) { state, status -> describe(state, status) }
                .distinctUntilChanged()
                .collect { report(it) }
        }
        // The watch app's log cannot be read on the watch, so new lines go to the phone's G7 log
        scope.launch {
            commLog.entries
                .debounce(LOG_BATCH_MS)
                .collect { sendLog(it, since = maxOf(logSentUpTo, System.currentTimeMillis() - LOG_RESEND_MS)) }
        }
        if (store.value.isPaired) scope.launch { startSession() }
    }

    /** After the wearer allowed Bluetooth on the watch's own screen. */
    fun retry() {
        scope.launch { if (store.value.isPaired) startSession() }
    }

    /** The watch's own screen came back: battery use may have been changed in the settings. */
    fun refresh() {
        scope.launch { report(describe(store.value, session.status.value)) }
    }

    private suspend fun handle(message: EventData.G7WatchDirect) {
        if (!isSupported) {
            report(EventData.G7WatchStatus(EventData.G7WatchState.NOT_SUPPORTED), force = true)
            return
        }
        val current = store.value
        if (message.active && message.address != null && message.pairingCode != null) {
            if (current.address != message.address || current.pairingCode != message.pairingCode) {
                // Another sensor: let the old one go first. forgetSensor clears the state, so wait for it.
                if (current.isPaired) session.forgetSensor(removeBond = true).join()
                store.update {
                    G7State(
                        address = message.address,
                        pairingCode = message.pairingCode,
                        serial = message.serial,
                        sensorName = message.sensorName,
                        displayTypeCode = G7DisplayType.WATCH.code,
                        pairedAt = System.currentTimeMillis()
                    )
                }
                commLog.add("Direct to watch: set up for ${message.sensorName ?: message.address}")
                startSession()
            } else if (session.status.value == G7ConnectionStatus.STOPPED || session.status.value == G7ConnectionStatus.BLOCKED) {
                // The phone sends the setting again each time the watch asks for data. A running link is left alone.
                startSession()
            }
        } else {
            if (current.isPaired) {
                session.forgetSensor(removeBond = true).join()
                commLog.add("Direct to watch: turned off by the phone")
            }
            session.stop().join()
            G7WatchService.stop(context)
            clearLowAlarm()
        }
        // Always answer, so the phone knows the message arrived.
        report(describe(store.value, session.status.value), force = true)
        // The phone sends this after it was away or restarted: lines sent in between may have been lost.
        // It drops the lines it already has.
        sendLog(commLog.entries.value, since = System.currentTimeMillis() - LOG_RESEND_MS)
    }

    private suspend fun startSession() {
        // Restart, so a session that stopped on a missing permission or Bluetooth being off tries again.
        session.stop().join()
        session.start().join()
        G7WatchService.start(context)
    }

    private fun sendLog(entries: List<G7CommLog.Entry>, since: Long) {
        val lines = entries.filter { it.time > since }.takeLast(LOG_MAX_LINES)
        if (lines.isEmpty()) return
        logSentUpTo = maxOf(logSentUpTo, lines.last().time)
        rxBus.send(EventWearToMobile(EventData.G7WatchLog(lines.map { EventData.G7WatchLogLine(it.time, it.text) })))
    }

    private fun describe(state: G7State, status: G7ConnectionStatus): EventData.G7WatchStatus {
        val watchState = when {
            !isSupported                           -> EventData.G7WatchState.NOT_SUPPORTED
            !state.isPaired                        -> EventData.G7WatchState.OFF
            !bluetooth.hasPermissions              -> EventData.G7WatchState.NEEDS_PERMISSION
            !bluetooth.isEnabled                   -> EventData.G7WatchState.BLUETOOTH_OFF
            state.lastAuthenticationFailure != null -> EventData.G7WatchState.REFUSED
            state.sharedKeyHex == null             -> EventData.G7WatchState.PAIRING
            status == G7ConnectionStatus.CONNECTED -> EventData.G7WatchState.CONNECTED
            else                                   -> EventData.G7WatchState.WAITING
        }
        return EventData.G7WatchStatus(
            state = watchState,
            address = state.address,
            latestReadingAt = state.latestReadingAt,
            detail = state.lastAuthenticationFailure.takeIf { watchState == EventData.G7WatchState.REFUSED },
            batteryRestricted = state.isPaired && isSupported && !isBatteryUnrestricted
        )
    }

    private fun report(status: EventData.G7WatchStatus, force: Boolean = false) {
        _status.value = status
        updateNotification(status)
        if (!force && status == lastReported) return
        if (status.batteryRestricted && lastReported?.batteryRestricted != true)
            commLog.add("Direct to watch: battery use is not unrestricted; the watch may freeze AAPS in the background and miss readings")
        lastReported = status
        aapsLogger.debug(LTag.WEAR, "G7 direct: ${status.state} batteryRestricted=${status.batteryRestricted}")
        rxBus.send(EventWearToMobile(status))
    }

    private suspend fun onReadings(readings: List<G7Reading>) {
        val usable = readings.filter { it.isUsable }.sortedBy { it.timestamp }
        if (usable.isEmpty()) return
        val data = repository.current()
        val template = data.bgData.takeIf { !it.fromSensor && it.timeStamp > 0 } ?: data.bgData.takeIf { it.timeStamp > 0 }
        val history = data.graphData.entries.toMutableList()
        val built = usable.map { reading ->
            G7WatchReadings.toSingleBg(reading.timestamp, reading.clampedGlucose!!.toDouble(), reading.trendArrow, template, history).also { history += it }
        }
        val live = usable.lastOrNull { !it.fromBackfill }
        dataHandler.showSensorReadings(live?.let { built[usable.indexOf(it)] }, built)
        live?.let { checkLow(built[usable.indexOf(it)]) }
    }

    private fun checkLow(bg: EventData.SingleBg) {
        val now = System.currentTimeMillis()
        val level = G7WatchReadings.lowLevel(bg.sgv, bg.low)
        if (level == null) {
            clearLowAlarm()
            return
        }
        val phoneSilent = now - lastPhoneReadingAt > G7WatchReadings.PHONE_SILENT_MS
        if (!G7WatchReadings.shouldRaise(level, phoneSilent, raisedLow, raisedLowAt, now)) return
        raisedLow = level
        raisedLowAt = now
        val urgent = level == G7WatchReadings.Low.URGENT_LOW
        commLog.add("Direct to watch: ${level.name.lowercase()} ${bg.sgvString} and no reading from the phone; alarm on the watch")
        rxBus.send(
            EventData.CgmAlarm(
                id = LOW_ALARM_ID,
                title = context.getString(if (urgent) R.string.g7_watch_urgent_low else R.string.g7_watch_low),
                message = context.getString(R.string.g7_watch_low_message, bg.sgvString, bg.slopeArrow),
                vibrationSeconds = if (urgent) 10 else 5,
                urgent = urgent
            )
        )
    }

    private fun clearLowAlarm() {
        if (raisedLow == null) return
        raisedLow = null
        rxBus.send(EventData.CgmAlarmCancel(LOW_ALARM_ID))
    }

    /**
     * A notification only when the wearer has to do something: allow Bluetooth, allow unrestricted battery
     * use, or accept the pairing.
     */
    private fun updateNotification(status: EventData.G7WatchStatus) {
        val text = when {
            status.state == EventData.G7WatchState.NEEDS_PERMISSION -> R.string.g7_watch_notification_permission
            // Before the pairing: a frozen app misses the sensor's visit, the pairing included
            status.batteryRestricted                                -> R.string.g7_watch_notification_battery
            status.state == EventData.G7WatchState.PAIRING          -> R.string.g7_watch_notification_pairing
            status.state == EventData.G7WatchState.REFUSED          -> R.string.g7_watch_notification_refused
            else                                                    -> null
        }
        val manager = NotificationManagerCompat.from(context)
        if (text == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.g7_watch_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(context, 0, Intent(context, G7WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(context.getString(R.string.g7_watch_title))
            .setContentText(context.getString(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.WEAR, "G7 direct notification not allowed", e)
        }
    }

    companion object {

        const val LOW_ALARM_ID = "G7_WATCH_LOW"

        /** New log lines are collected this long, then sent to the phone in one message. */
        private const val LOG_BATCH_MS = 3_000L

        /** How far back the log is sent again when the phone asks, in case lines were lost while it was away. */
        private const val LOG_RESEND_MS = 2 * 60 * 60_000L

        /** A cap per message. A normal reading cycle writes about ten lines. */
        private const val LOG_MAX_LINES = 400
        private const val CHANNEL_ID = "AndroidAPS-G7Direct"

        // Clear of the ids DataLayerListenerServiceWear (1, 2, 556677) and CgmAlarmWear (7700..7955) use.
        private const val NOTIFICATION_ID = 7960
    }
}
