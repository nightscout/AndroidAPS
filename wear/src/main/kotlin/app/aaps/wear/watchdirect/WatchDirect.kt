package app.aaps.wear.watchdirect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.alarm.CgmAlarmWatch
import app.aaps.wear.comm.DataHandlerWear
import app.aaps.wear.data.ComplicationDataRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Direct-to-watch, watch side. When the phone turns it on ([EventData.CgmWatchDirect]), the watch
 * connects to the Dexcom ONE+ / G7 itself, in the sensor's watch slot, with its own key, next to the
 * phone. The phone stays the master: it says which sensor to follow and when to let it go.
 *
 * The watch's readings go to its faces, complications and tiles only. The phone's copy of the same
 * reading wins ([WatchDirectReadings.choose]); the watch's own copy shows when the phone is away.
 * No reading is ever sent to the phone: the only message this class sends is the link status
 * ([EventData.CgmWatchStatus]). That is the hard rule of direct-to-watch, and the reason the loop
 * can never get a watch reading.
 *
 * The phone owns the glucose alarms. The watch raises only a low or urgent low of its own, and only
 * while the phone has sent nothing for [WatchDirectReadings.PHONE_SILENT_MS]. It goes through
 * [CgmAlarmWatch], the same notification, vibration and Acknowledge as the alarms the phone sends,
 * and with the same alarm ids, so a later alarm or cancel from the phone replaces it.
 *
 * The link needs Android 12 (the Bluetooth permissions it uses); an older watch reports
 * [EventData.CgmWatchState.NOT_SUPPORTED], so the phone shows it as not possible.
 *
 * Behaviour follows `G7WatchDirect` in nightscout/AndroidAPS pull request 5397 by t1dude (commit
 * `bec1b16ba20`), re-built on our own driver through [WatchSensorLink]. See the NOTICE file of this
 * module. Fixes by stavlor, and by olorinmaia: #250, and #251 (the watch sleeps between readings
 * and wakes just before the sensor).
 */
@SingleIn(AppScope::class)
@Inject
class WatchDirect(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val repository: ComplicationDataRepository,
    private val dataHandler: DataHandlerWear,
    private val cgmAlarmWatch: CgmAlarmWatch
) {

    /** One at a time: the link's callbacks and the phone's messages are handled in order. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var started = false

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val link: WatchSensorLink? by lazy { if (isSupported) OnePlusWatchSensorLink(context) else null }

    /**
     * The time of the newest reading the phone has: the reading's own time, not when its message
     * arrived. A phone that is away from the sensor but reaches the watch over Wi-Fi or LTE sends its
     * old reading again with every sync. By arrival time it looked present, and the watch kept its
     * own low alarm quiet while the phone could not see the glucose at all.
     *
     * Start time counts as "phone heard": the phone gets its quiet minutes after a restart of the watch app.
     */
    private var lastPhoneReadingAt = System.currentTimeMillis()
    private var raisedLow: WatchDirectReadings.Low? = null
    private var raisedLowAt = 0L
    private var lastReported: EventData.CgmWatchStatus? = null

    /** The last "on" message, kept while the wearer still has to allow Bluetooth. */
    private var waitingForPermission: EventData.CgmWatchDirect? = null

    /** The last problem the link gave up on, for the status. Cleared by a connection. */
    private var refusal: String? = null

    /** True once this app run has asked the link to connect, so a running pairing is not started twice. */
    private var connectAsked = false

    /** Waits while the link is down, then asks it to start again. See [WatchDirectRestart]. */
    private var restartJob: Job? = null

    private val _status = MutableStateFlow(EventData.CgmWatchStatus(EventData.CgmWatchState.OFF))

    /** For the watch's own screen. */
    val status: StateFlow<EventData.CgmWatchStatus> = _status.asStateFlow()

    /** False when the watch may put the app to sleep in the background and miss readings. */
    val isBatteryUnrestricted: Boolean
        get() = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * True when the watch has a screen where the user can allow unrestricted battery use.
     *
     * Some watches have none (a Pixel Watch 4 was reported). There a warning would ask for something
     * the user cannot do and would never go away, so the app does not ask. This looks for the screen
     * itself rather than at the Android version: the version does not say whether a maker kept the
     * setting, and a watch that has it must still be asked, or it may stop the link in the background.
     * The app info screen does not count, it has no battery choice on these watches.
     *
     * A screen that answers is not enough. A Galaxy Watch 5 on Wear OS 6 answers both requests with
     * an empty stand-in screen of the watch settings (`FakeSettingsActivity`): it opens and closes
     * at once, so the button did nothing and the warning never went away. That stand-in does not count.
     */
    private val canAskForUnrestrictedBattery: Boolean by lazy {
        val found = batterySettingsIntents(context.packageName).any { intent ->
            val screen = context.packageManager.resolveActivity(intent, 0)?.activityInfo?.name
            screen != null && !screen.endsWith(STAND_IN_SETTINGS_SCREEN)
        }
        if (!found) aapsLogger.info(LTag.WEAR, "Direct to watch: this watch has no battery setting to ask for, the battery warning is not shown")
        found
    }

    private val hasPermissions: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT))

    private val bluetoothOn: Boolean
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (started) return
        started = true
        scope.launch { rxBus.toFlow(EventData.CgmWatchDirect::class).collect { handle(it) } }
        scope.launch {
            rxBus.toFlow(EventData.SingleBg::class).collect {
                if (it.dataset == 0 && !it.fromSensor) lastPhoneReadingAt = WatchDirectReadings.newestPhoneReading(lastPhoneReadingAt, it.timeStamp)
            }
        }
        val link = link
        if (link == null) {
            _status.value = EventData.CgmWatchStatus(EventData.CgmWatchState.NOT_SUPPORTED)
            return
        }
        link.setListener(object : WatchSensorLink.Listener {
            override fun onReading(reading: WatchSensorReading) {
                // The name must differ from this method. With the same name the call inside meant
                // this method again, not the one of WatchDirect: the reading was never handled, and
                // the app launched the same call without end (a full processor core, for hours).
                scope.launch { showReading(reading) }
            }

            override fun onConnected(connected: Boolean) {
                scope.launch {
                    if (connected) {
                        refusal = null
                        stopWatchingTheLink()
                    } else watchTheLink()
                    report(describe())
                }
            }

            override fun onProblem(message: String, fatal: Boolean) {
                scope.launch {
                    if (fatal) refusal = message
                    report(describe())
                }
            }
        })
        // A sensor that was set up before the watch app restarted: pick it up again.
        scope.launch {
            // Not while paused: direct to watch was turned off, and the sensor is only kept.
            if (link.address() != null && !link.isPaused() && hasPermissions) {
                // Resume needs a key. An app that was restarted before the first pairing has none,
                // so it starts the pairing again by itself and does not wait for the phone.
                if (!link.resume() && !link.hasKey()) {
                    connectAsked = link.pairAgain()
                    if (connectAsked) aapsLogger.info(LTag.WEAR, "Direct to watch: not paired yet, pairing started again after a restart")
                }
                startService()
                watchTheLink()
            }
            report(describe())
        }
    }

    /** True while direct to watch is on and its foreground service should run. */
    @Volatile private var serviceWanted = false

    private fun startService() {
        serviceWanted = true
        WatchDirectService.start(context, aapsLogger)
    }

    /** Android refused the service while the app was in the background: start it from the alarm of `ForegroundStart`. */
    fun startServiceAgain() {
        if (serviceWanted) WatchDirectService.start(context, aapsLogger, retryWhenRefused = false)
    }

    /**
     * Start the wait of [WatchDirectRestart]: while the link stays down, ask it again each time the
     * wait is over. One wait at a time: a call while one runs changes nothing, so the phone's
     * repeated messages do not push the wait further out.
     *
     * Asking does nothing while the driver still has a session of its own, which is the normal case.
     * The wait is counted while the watch is awake, so on a sleeping watch it can take longer.
     */
    private fun watchTheLink() {
        if (restartJob?.isActive == true) return
        restartJob = scope.launch {
            while (true) {
                delay(WatchDirectRestart.DOWN_BEFORE_ASKING_MS)
                val link = link ?: return@launch
                val ask = WatchDirectRestart.shouldAsk(
                    hasSensor = link.address() != null,
                    hasKey = link.hasKey(),
                    paused = link.isPaused(),
                    permitted = hasPermissions,
                    bluetoothOn = bluetoothOn,
                    connected = link.isConnected()
                )
                if (ask && link.resume()) {
                    aapsLogger.info(LTag.WEAR, "Direct to watch: the link had stopped by itself, started again")
                    report(describe())
                }
            }
        }
    }

    private fun stopWatchingTheLink() {
        restartJob?.cancel()
        restartJob = null
    }

    /** After the wearer allowed Bluetooth on the watch's own screen. */
    fun retry() {
        scope.launch {
            waitingForPermission?.let { handle(it) } ?: run {
                link?.let { if (it.address() != null && !it.isPaused() && hasPermissions) it.resume() }
                report(describe())
            }
        }
    }

    /** The watch's own screen came back: battery use may have changed in the settings. */
    fun refresh() {
        scope.launch { report(describe()) }
    }

    private fun handle(message: EventData.CgmWatchDirect) {
        val link = link
        if (link == null) {
            report(EventData.CgmWatchStatus(EventData.CgmWatchState.NOT_SUPPORTED), force = true)
            return
        }
        val address = message.address
        val pairingCode = message.pairingCode
        if (message.active && address != null && pairingCode != null) {
            if (!hasPermissions) {
                // Nothing can connect yet. Kept, so the watch's own screen can go on after the grant.
                waitingForPermission = message
            } else {
                waitingForPermission = null
                if (link.address() != address || link.pairingCode() != pairingCode) {
                    // Another sensor, or the same one with a new code: let the old one go first.
                    if (link.address() != null) link.forget()
                    refusal = null
                    aapsLogger.info(LTag.WEAR, "Direct to watch: set up for ${message.sensorName ?: address}")
                    link.connect(address, pairingCode, message.serial)
                    connectAsked = true
                } else if (!link.isConnected()) {
                    // The phone sends the setting again each time the watch asks for data. A link that
                    // is already running is left alone; resume does nothing then.
                    val resumed = link.resume()
                    // Resume needs a key. A watch app that was restarted before the first pairing
                    // has the sensor and its code but no key, so nothing was running any more and
                    // the pairing request never came back. Start the pairing again, once per app run.
                    if (!resumed && !link.hasKey() && !connectAsked) {
                        aapsLogger.info(LTag.WEAR, "Direct to watch: not paired yet, pairing started again for ${message.sensorName ?: address}")
                        link.connect(address, pairingCode, message.serial)
                        connectAsked = true
                    }
                }
                startService()
                if (!link.isConnected()) watchTheLink()
            }
        } else {
            waitingForPermission = null
            if (link.address() != null && !link.isPaused()) {
                // A pause, not a forget: the sensor, its key and the bond are kept, so turning it
                // on again goes on where it was. Forgetting here cost the user the link: the key
                // was gone, and the sensor did not keep a new one made over the old bond.
                link.pause()
                aapsLogger.info(LTag.WEAR, "Direct to watch: turned off by the phone, the sensor is kept")
            }
            connectAsked = false
            refusal = null
            stopWatchingTheLink()
            serviceWanted = false
            WatchDirectService.stop(context)
            clearLowAlarm()
        }
        // Always answer, so the phone knows the message arrived.
        report(describe(), force = true)
    }

    private fun describe(): EventData.CgmWatchStatus {
        val link = link ?: return EventData.CgmWatchStatus(EventData.CgmWatchState.NOT_SUPPORTED)
        val address = link.address()
        val state = when {
            waitingForPermission != null && !hasPermissions -> EventData.CgmWatchState.NEEDS_PERMISSION
            address == null || link.isPaused()               -> EventData.CgmWatchState.OFF
            !hasPermissions                                  -> EventData.CgmWatchState.NEEDS_PERMISSION
            !bluetoothOn                                     -> EventData.CgmWatchState.BLUETOOTH_OFF
            refusal != null                                  -> EventData.CgmWatchState.REFUSED
            !link.hasKey()                                   -> EventData.CgmWatchState.PAIRING
            link.isConnected()                               -> EventData.CgmWatchState.CONNECTED
            else                                             -> EventData.CgmWatchState.WAITING
        }
        return EventData.CgmWatchStatus(
            state = state,
            address = address ?: waitingForPermission?.address,
            latestReadingAt = latestReadingAt,
            detail = refusal.takeIf { state == EventData.CgmWatchState.REFUSED },
            batteryRestricted = state != EventData.CgmWatchState.OFF && canAskForUnrestrictedBattery && !isBatteryUnrestricted
        )
    }

    private var latestReadingAt: Long? = null

    private fun report(status: EventData.CgmWatchStatus, force: Boolean = false) {
        _status.value = status
        updateNotification(status)
        if (!force && status == lastReported) return
        lastReported = status
        aapsLogger.debug(LTag.WEAR, "Direct to watch: ${status.state} batteryRestricted=${status.batteryRestricted}")
        // The status is the only thing this class ever sends to the phone. Never a reading.
        rxBus.send(EventWearToMobile(status))
    }

    /** One reading from the watch's own link: to the faces, and a low alarm when the phone is away. */
    private suspend fun showReading(reading: WatchSensorReading) {
        if (!WatchDirectReadings.isUsable(reading)) return
        val data = repository.current()
        val template = data.bgData.takeIf { !it.fromSensor && it.timeStamp > 0 } ?: data.bgData.takeIf { it.timeStamp > 0 }
        val bg = WatchDirectReadings.toSingleBg(reading, template, data.graphData.entries)
        // The driver asks the sensor for its last 3 hours at every connection, so each round brings
        // 36 readings the watch already has. Storing one changes nothing, but it still wrote the
        // store and redrew the face, the complications and the tile: 37 times per round, not once.
        if (!WatchDirectReadings.addsNothing(bg, data.bgData, data.graphData.entries)) dataHandler.showSensorReadings(bg)
        if (bg.timeStamp > (latestReadingAt ?: 0L)) {
            latestReadingAt = bg.timeStamp
            // For the watch's own screen only. Not sent to the phone: its next status carries it.
            _status.value = describe()
            checkLow(bg)
        }
    }

    private fun checkLow(bg: EventData.SingleBg) {
        val now = System.currentTimeMillis()
        val level = WatchDirectReadings.lowLevel(bg.sgv, bg.low)
        if (level == null) {
            clearLowAlarm()
            return
        }
        val phoneSilent = now - lastPhoneReadingAt > WatchDirectReadings.PHONE_SILENT_MS
        if (!WatchDirectReadings.shouldRaise(level, phoneSilent, raisedLow, raisedLowAt, now)) return
        raisedLow = level
        raisedLowAt = now
        aapsLogger.info(LTag.WEAR, "Direct to watch: ${level.name.lowercase()} ${bg.sgvString} and no reading from the phone; alarm on the watch")
        val urgent = level == WatchDirectReadings.Low.URGENT_LOW
        cgmAlarmWatch.show(
            EventData.CgmAlarm(
                id = alarmId(level),
                title = context.getString(if (urgent) R.string.watch_direct_urgent_low else R.string.watch_direct_low),
                message = context.getString(R.string.watch_direct_low_message, bg.sgvString, bg.slopeArrow),
                vibrationSeconds = if (urgent) URGENT_VIBRATION_S else LOW_VIBRATION_S,
                urgent = urgent
            )
        )
    }

    private fun clearLowAlarm() {
        val raised = raisedLow ?: return
        raisedLow = null
        cgmAlarmWatch.remove(alarmId(raised))
    }

    /** The id the phone uses for the same alarm (`CgmAlarmType.id`). */
    private fun alarmId(level: WatchDirectReadings.Low): String =
        if (level == WatchDirectReadings.Low.URGENT_LOW) URGENT_LOW_ALARM_ID else LOW_ALARM_ID

    /**
     * A notification only when the wearer has to do something: allow Bluetooth, allow unrestricted
     * battery use, or accept the pairing.
     */
    private fun updateNotification(status: EventData.CgmWatchStatus) {
        val text = when {
            status.state == EventData.CgmWatchState.NEEDS_PERMISSION -> R.string.watch_direct_notification_permission
            // Before the pairing: a frozen app misses the sensor's visit, the pairing included
            status.batteryRestricted                                 -> R.string.watch_direct_notification_battery
            status.state == EventData.CgmWatchState.PAIRING          -> R.string.watch_direct_notification_pairing
            status.state == EventData.CgmWatchState.REFUSED          -> R.string.watch_direct_notification_refused
            else                                                     -> null
        }
        val manager = NotificationManagerCompat.from(context)
        if (text == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.watch_direct_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(context, 0, Intent(context, WatchDirectActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(context.getString(R.string.watch_direct_title))
            .setContentText(context.getString(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.WEAR, "Direct to watch notification not allowed", e)
        }
    }

    private companion object {

        const val CHANNEL_ID = "AndroidAPS-WatchDirect"

        // Clear of the ids DataLayerListenerServiceWear (1, 2, 556677) and the other watch notifications use.
        const val NOTIFICATION_ID = 7972

        /** The ids of `CgmAlarmType.URGENT_LOW` and `CgmAlarmType.LOW` on the phone. */
        const val URGENT_LOW_ALARM_ID = "urgent_low"
        const val LOW_ALARM_ID = "low"

        /** About 10 s for an urgent low and 5 s for a low, as before. */
        const val URGENT_VIBRATION_S = 10
        const val LOW_VIBRATION_S = 5

        /** The empty screen Wear OS settings put in place of a setting the watch does not have. */
        const val STAND_IN_SETTINGS_SCREEN = "FakeSettingsActivity"
    }
}

/** The screens where the user can allow unrestricted battery use, best first. */
internal fun batterySettingsIntents(packageName: String): List<Intent> = listOf(
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
)
