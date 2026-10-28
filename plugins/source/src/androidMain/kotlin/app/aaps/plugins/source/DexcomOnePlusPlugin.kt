package app.aaps.plugins.source

import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.CgmSensorStatusProvider
import app.aaps.core.interfaces.source.CgmStagingEvidence
import app.aaps.core.interfaces.source.CgmWarmupProvider
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.SensorCalibrationResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcPluginByoda
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriver
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseWatcher
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.session.OnePlusCalibrationGate
import app.aaps.plugins.source.compose.BgSourceComposeContent
import app.aaps.plugins.source.keys.DexcomOnePlusBooleanKey
import app.aaps.plugins.source.keys.DexcomOnePlusIntKey
import app.aaps.plugins.source.keys.DexcomOnePlusIntentKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Native Dexcom ONE+ / G7 BG source: the BLE driver of `:plugins:dexcom_oneplus` runs inside AAPS,
 * no Dexcom app or xDrip is needed.
 *
 * Two sensor slots. The production slot feeds the loop. The staging slot can pre-soak the next
 * sensor while the old one still works; its readings are only collected and shown, never given to
 * the loop, until the user (or the optional automatic switch) promotes it.
 *
 * Everything that needs an Android `Context` (driver registry, setup screens, foreground service,
 * warm-up notification) is asked of [DexcomOnePlusHost], so this class can be tested without one.
 *
 * Registers itself into the plugin list. Scoped with Metro's own [SingleIn].
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(446)
@SingleIn(AppScope::class)
class DexcomOnePlusPlugin @Inject constructor(
    rh: TextResolver,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    // Not a `val`: PluginBase already keeps it as a protected property, so a second one here would
    // shadow it. This only carries it to the super constructor.
    notificationManager: NotificationManager,
    private val host: DexcomOnePlusHost,
    private val persistenceLayer: PersistenceLayer,
    private val warmupBasalGuard: DexcomOnePlusWarmupBasalGuard,
    private val activePlugin: ActivePlugin,
    private val rxBus: RxBus,
) : AbstractBgSourcePlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        // No `showInList` call: the plugin is listed in every build, like the other BG sources.
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(SourceStrings.dexcom_oneplus_native),
            )
        }
        .icon(IcPluginByoda)
        .pluginName(SourceStrings.dexcom_oneplus_native)
        .preferencesVisibleInSimpleMode(false)
        .description(SourceStrings.description_source_dexcom_oneplus_native),
    ownPreferences = DexcomOnePlusIntentKey.entries + DexcomOnePlusBooleanKey.entries + DexcomOnePlusIntKey.entries,
    aapsLogger,
    rh,
    preferences,
    config,
    notificationManager,
), BgSource, OnePlusGlucoseWatcher, CgmSensorStatusProvider {

    private val resources: TextResolver = rh

    /**
     * Scope of the work this plugin starts: database writes of readings, the sensor age repair, the
     * reconnect watchdog and the automatic promotion.
     *
     * Made when work is first needed (normally by [onStart]) and cancelled in [onStop], so nothing
     * runs for a user who never enables the plugin, and nothing keeps running after it is stopped.
     * A user action on a setup screen after a stop makes a new one.
     */
    private var workScope: CoroutineScope? = null

    /** Guards [workScope]. */
    private val workScopeLock = AapsLock()

    private fun ioScope(): CoroutineScope = workScopeLock.withLock {
        workScope ?: CoroutineScope(SupervisorJob() + aapsIoDispatcher).also { workScope = it }
    }

    /** Cancels everything started on [ioScope]. */
    private fun cancelWork() {
        workScopeLock.withLock {
            workScope?.cancel()
            workScope = null
        }
    }

    /** True while a job started by this plugin is still running. For tests. */
    internal fun hasRunningWork(): Boolean = workScopeLock.withLock {
        workScope?.coroutineContext?.get(Job)?.children?.any { it.isActive } == true
    }

    /**
     * The last resort that brings a sensor back.
     *
     * The driver has its own ladder of retries and it is the one that should do the work. This is
     * the net for the case where that ladder itself stops, whatever the reason: as long as a sensor
     * is stored and the session is down, the plugin asks for a connection again. Without it the
     * only way back was to open the Start screen and connect by hand.
     */
    private var reconnectWatchdog: Job? = null

    private val driver: OnePlusCgmDriver
        get() = host.productionDriver()

    /** Dedicated STAGING (pre-soak) driver instance — its own BLE session and its own store. */
    private val stagingDriver: OnePlusCgmDriver
        get() = host.stagingDriver()

    @Volatile
    private var warmupPhase: OnePlusWarmupState.Phase = OnePlusWarmupState.Phase.IDLE

    /** The newest reading of the production sensor, for the calibration checks. */
    @Volatile
    private var lastProductionReading: OnePlusGlucoseSample? = null

    private val _warmup = MutableStateFlow(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.IDLE))

    /** Live warm-up / session status of the native driver — feeds the ongoing notification and the warm-up ring. */
    val warmup: StateFlow<OnePlusWarmupState> = _warmup.asStateFlow()

    /**
     * Generic [CgmWarmupProvider] view derived from the single source of truth [_warmup].
     * null while nothing is warming up / (re)connecting (READY / IDLE / FAILED).
     */
    private val _warmupStatus = MutableStateFlow(DexcomOnePlusWarmupMapper.toCgmWarmupStatus(_warmup.value))
    override val warmupStatus: StateFlow<CgmWarmupStatus?> = _warmupStatus.asStateFlow()

    /** Posts the user-visible alert. Tests replace it; production posts a status-bar notification. */
    internal var promotionAlerter: (String) -> Unit = { message -> host.alert(message) }

    /** Private key-value sensor store — used here only to persist/read the ingest
     *  high-water mark so restarts/updates don't re-insert duplicate readings. */
    private val sensorStore by lazy { host.slotStore(null) }

    /** Namespaced store for the STAGING sensor (identity / markers / session start kept separate). */
    private val stagingStore by lazy { host.slotStore(host.stagingNamespace) }

    /** Runs [DexcomOnePlusWarmupBasalGuard] on every warm-up / connection change. Started in onStart, cancelled in onStop. */
    private var warmupGuardJob: Job? = null

    /** True while the [DexcomOnePlusWarmupBasalGuard] collector runs. For tests. */
    internal fun isWarmupGuardRunning(): Boolean = warmupGuardJob?.isActive == true

    /** Session start already checked by [healMissingSensorChange], so a deletion is not undone twice. */
    @Volatile
    private var healedSensorChangeStartMs: Long = 0L

    // ---- Dual-sensor (staging / pre-soak) state ----

    private val _lifecycle = MutableStateFlow<CgmSensorLifecycle?>(null)
    override val lifecycle: StateFlow<CgmSensorLifecycle?> = _lifecycle.asStateFlow()

    private val _stagingLifecycle = MutableStateFlow<CgmSensorLifecycle?>(null)
    override val stagingLifecycle: StateFlow<CgmSensorLifecycle?> = _stagingLifecycle.asStateFlow()

    private val _stagingState = MutableStateFlow(StagingState.ABSENT)
    override val stagingState: StateFlow<StagingState> = _stagingState.asStateFlow()

    private val _stagingEvidence = MutableStateFlow<CgmStagingEvidence?>(null)
    override val stagingEvidence: StateFlow<CgmStagingEvidence?> = _stagingEvidence.asStateFlow()

    /**
     * Time left before the staging sensor has soaked [DexcomOnePlusStaging.STAGING_MIN_SETTLE_MS].
     * Updated together with [stagingState], so the overview can count down without its own copy of
     * the settle rule.
     */
    private val _stagingSettleRemainingMs = MutableStateFlow<Long?>(null)
    override val stagingSettleRemainingMs: StateFlow<Long?> = _stagingSettleRemainingMs.asStateFlow()

    /** A staging sensor session exists (warming/settling), collect-only until promoted. */
    @Volatile private var stagingPresent = false

    /** Staging sensor is still warming up / (re)connecting (no glucose yet). */
    @Volatile private var stagingWarming = false

    /** Latched once the staging sensor has left warm-up; survives restarts through the slot store. */
    @Volatile private var stagingWarmupDone = false

    /** Count of valid staging EGVs collected (promotion gate). */
    @Volatile private var stagingValidEgvCount = 0

    /** Last reading collected from the staging sensor (mg/dL), for the evidence surface. */
    @Volatile private var stagingLastValueMgdl: Double? = null

    /** Timestamp of [stagingLastValueMgdl]. */
    @Volatile private var stagingLastValueAtMs: Long? = null

    /** Recent staging readings (ts→mgdl), read by the automatic switch. Never published. */
    private val stagingBuffer = ArrayDeque<Pair<Long, Double>>()

    /**
     * Guards [stagingBuffer]. Was `stagingBufferLock.withLock`, which is JVM only; [AapsLock] is
     * reentrant and blocking, the same bargain the monitor made.
     */
    private val stagingBufferLock = AapsLock()

    /**
     * Only one promotion at a time. Without it the button and the automatic switch could both pass
     * the [stagingPresent] check and swap the driver instances twice.
     */
    private val promoteMutex = Mutex()

    /**
     * An automatic promotion is already on its way, so the next reading does not start another.
     *
     * Guarded by [autoPromoteLock] rather than by an `AtomicBoolean`, which is JVM only. The claim
     * and the test of the flag happen in one guarded step, so two readings arriving together still
     * start exactly one promotion.
     */
    private val autoPromoteLock = AapsLock()

    @Volatile
    private var autoPromoteInFlight = false

    /** Time of the last automatic try that was refused, see [StagingAutoPromotion.RETRY_INTERVAL_MS]. */
    @Volatile
    private var lastAutoPromoteTryMs = 0L

    /** Watches the STAGING driver; routes glucose to a local buffer (never the loop) until promoted. */
    private val stagingWatcher = object : OnePlusGlucoseWatcher {
        override fun onWarmup(state: OnePlusWarmupState) = handleStagingWarmup(state)
        override fun onGlucose(sample: OnePlusGlucoseSample) = handleStagingGlucose(sample)
        override fun onSession(up: Boolean, reason: String?) {
            aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_STAGING: session up=$up reason=$reason")
        }

        override fun onError(message: String, fatal: Boolean) {
            aapsLogger.error(LTag.BGSOURCE, "DEXCOM_ONEPLUS_STAGING: error fatal=$fatal $message")
        }
    }


    /**
     * A fingerstick goes to the sensor while [DexcomOnePlusBooleanKey.SendCalibrationToSensor] is on,
     * which is the default.
     *
     * When it is true the app must not also fit a software line on the same readings. The graph
     * worker already does this: its `PrepareGraphDataRunner.smoothData` skips the software
     * calibration while this returns true, and the master's calibration path sends the value here
     * instead of storing a calibration entry.
     */
    override fun calibratesInSensor(): Boolean =
        preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor)

    /** Why the sensor must not get [glucoseMgdl] now, already readable by the user, or null. */
    override fun sensorCalibrationRefusal(glucoseMgdl: Int, bloodAtMs: Long): String? {
        if (!calibratesInSensor()) return null
        val refusal = OnePlusCalibrationGate.check(glucoseMgdl, bloodAtMs, nowMs(), warmupPhase, lastProductionReading)
            ?: return null
        aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_CALIBRATION: refused $refusal")
        return resources.gs(DexcomOnePlusCalibrationText.refusal(refusal))
    }

    override fun calibrateSensor(glucoseMgdl: Int, bloodAtMs: Long): SensorCalibrationResult {
        if (!calibratesInSensor()) return SensorCalibrationResult.NotSupported
        // Checked again here: time has passed since the user saw the confirmation.
        sensorCalibrationRefusal(glucoseMgdl, bloodAtMs)?.let { return SensorCalibrationResult.Refused(it) }
        // Only the production driver: a pre-soak sensor is not the one feeding the loop, and a
        // calibration it accepted would stay in it for the whole of its own life.
        val queued = driver.offerCalibration(glucoseMgdl, bloodAtMs)
        return if (queued) SensorCalibrationResult.Queued
        else SensorCalibrationResult.Refused(resources.gs(SourceStrings.dexcom_oneplus_calibration_not_sent))
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "dexcom_oneplus_settings",
        title = SourceStrings.dexcom_oneplus_native,
        summary = SourceStrings.dexcom_oneplus_plugin_summary,
        // The three screen buttons come from the host, because opening a screen needs a Context.
        items = host.screenItems(this) + listOf(
            DexcomOnePlusBooleanKey.SendCalibrationToSensor,
            DexcomOnePlusBooleanKey.AutoPromote,
            DexcomOnePlusIntKey.AutoPromoteHours,
            DexcomOnePlusBooleanKey.RepairMissingSensorChange,
            DexcomOnePlusBooleanKey.AnchorSessionToManualSensorChange,
            DexcomOnePlusBooleanKey.CorrectSensorStart,
            // Sensor age on the overview comes from the SENSOR_CHANGE therapy event this writes.
            BooleanKey.BgSourceCreateSensorChange,
        ),
        icon = pluginDescription.icon,
    )

    override suspend fun onStart() {
        super.onStart()
        ioScope()
        syncDriverFromPrefs()
        // Rehydrate the ingest dedup BEFORE any reconnect can deliver glucose, so an app update /
        // restart cannot re-insert already-stored readings (duplicates halt the loop). Sequence floor
        // from the sensor store + recent already-stored ONE+ timestamps from the DB.
        val recentTs = try {
            persistenceLayer.getBgReadingsDataFromTime(nowMs() - INGEST_SEED_WINDOW_MS, ascending = true)
                .filter { it.sourceSensor == SourceSensor.DEXCOM_ONEPLUS_NATIVE }
                .map { it.timestamp }
        } catch (t: Throwable) {
            aapsLogger.error(LTag.BGSOURCE, "DEXCOM_ONEPLUS_BG: ingest seed query failed: ${t.message}", t)
            emptyList()
        }
        DexcomOnePlusIngest.seed(sensorStore.loadLastIngestSequence(), recentTs)
        refreshProductionLifecycle()
        // Before either slot resumes: an install that hit the cross-slot collision was left with the
        // same MAC in both stores, and the two resumes below would recreate it on every start.
        reconcileSlotsOnSameSensor()
        val autoResumeQueued = driver.resumeStoredSession()
        val stagingResumeQueued = resumeStagingSessionIfStored()
        warmupPhase = driver.warmupState().phase
        // The privilege the OEM profiles have been asking for since they were written. Only when a
        // sensor is actually stored: no session wanted, no service, no notification.
        refreshSessionService()
        // Reconcile a warm-up notification that survived a process restart with the driver's current
        // state — cancels it when warm-up is already READY/IDLE (otherwise nothing clears the stale
        // status-bar notification until the next onWarmup event, which may never arrive after restart).
        host.showWarmup(driver.warmupState())
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_SESSION: plugin start " +
                "autoResumeQueued=$autoResumeQueued " +
                "stagingResumeQueued=$stagingResumeQueued",
        )
        startWarmupBasalGuard()
    }

    /**
     * While the sensor warms up or the link is (re)connecting, no glucose arrives and the APS does
     * not run, so a high temp basal could keep running blind. On every change of that state the
     * guard checks its conditions and may cancel a temp basal above profile, see
     * [DexcomOnePlusWarmupBasalGuard]. It works in the background, with no screen open, and does
     * nothing once a reading ends the warm-up (the state becomes null).
     *
     * A change is a change of the generic status or of the driver's status text, so a long
     * reconnect that keeps reporting new attempts is checked again each time, also after the last
     * glucose has become older than 7 minutes.
     */
    private fun startWarmupBasalGuard() {
        warmupGuardJob?.cancel()
        warmupGuardJob = ioScope().launch {
            warmup
                .map { state -> DexcomOnePlusWarmupMapper.toCgmWarmupStatus(state)?.let { status -> status to state.message } }
                .distinctUntilChanged()
                .collect { active ->
                    if (active != null) {
                        try {
                            warmupBasalGuard.ensureProfileBasalDuringWarmup(this@DexcomOnePlusPlugin, ioScope())
                        } catch (t: Throwable) {
                            aapsLogger.error(LTag.BGSOURCE, "ONEPLUS_WARMUP_BASAL guard error: ${t.message}", t)
                        }
                    }
                }
        }
    }

    override suspend fun onStop() {
        cancelReconnectWatchdog()
        warmupGuardJob?.cancel()
        warmupGuardJob = null
        driver.removeWatcher(this)
        driver.shutdown()
        // Tear down any staging session too (independent of production).
        runCatching { stagingDriver.removeWatcher(stagingWatcher) }
        runCatching { stagingDriver.shutdown() }
        host.cancelWarmupNotification()
        host.setSessionServiceWanted(false)
        // Last, after the drivers are stopped, so no late callback can start new work after it.
        cancelWork()
        aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_SESSION: plugin stop")
        super.onStop()
    }

    /**
     * Make sure this plugin watches the production driver and that the driver has its platform.
     * Safe to call from the Start screen before connect.
     */
    fun syncDriverFromPrefs() {
        host.attachPlatform(host.attachProductionDriver(watcher = this))
    }

    override fun onWarmup(state: OnePlusWarmupState) {
        warmupPhase = state.phase
        _warmup.value = state
        // Set here, together with _warmup, instead of a collector: no coroutine runs while the
        // plugin is off, and the value can never lag behind _warmup.
        _warmupStatus.value = DexcomOnePlusWarmupMapper.toCgmWarmupStatus(state)
        host.showWarmup(state)
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_WARMUP: phase=${state.phase} remainingMs=${state.remainingMs} msg=${state.message}",
        )
    }

    /**
     * Watcher callback thread is not guaranteed (the real driver may call from its BLE executor).
     *
     * ⚠️ ASYNC IMPACT: GV mapping on the callback thread; [PersistenceLayer.insertCgmSourceData]
     * runs on [ioScope] (IO). Do not block the BLE executor from this callback.
     */
    override fun onGlucose(sample: OnePlusGlucoseSample) {
        if (DexcomOnePlusIngest.isWarmupBlockingIngest(warmupPhase)) {
            aapsLogger.debug(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_BG: ignored during WARMING ${sample.mgdl.toInt()} @${sample.timestampMs}",
            )
            return
        }
        // A real glucose reading means warm-up / (re)connection is over. Force READY so the lingering
        // ongoing notification is cancelled, even if the driver never emits an explicit READY event
        // (the reported "notification stuck forever" case). Idempotent: only fires while a
        // non-terminal warm-up phase is still showing.
        if (warmupPhase != OnePlusWarmupState.Phase.READY && warmupPhase != OnePlusWarmupState.Phase.IDLE) {
            onWarmup(OnePlusWarmupState(phase = OnePlusWarmupState.Phase.READY))
        }
        if (sample.timestampMs > (lastProductionReading?.timestampMs ?: Long.MIN_VALUE)) lastProductionReading = sample
        ingestToLoop(sample, sensorStore)
    }

    /**
     * Dedup + publish a reading to the loop (`insertCgmSourceData`) and persist its ingest high-water
     * mark to [store].
     */
    private fun ingestToLoop(sample: OnePlusGlucoseSample, store: OnePlusSensorStore) {
        if (!DexcomOnePlusIngest.shouldAccept(sample)) {
            aapsLogger.debug(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_BG: dedup drop ${sample.mgdl.toInt()} @${sample.timestampMs} seq=${sample.sequence}",
            )
            return
        }
        val glucoseValues = listOf(DexcomOnePlusIngest.mapToGv(sample))
        ioScope().launch {
            val result = persistenceLayer.insertCgmSourceData(
                Sources.DexcomOnePlus,
                glucoseValues,
                emptyList(),
                sensorInsertionTime = null,
            )
            aapsLogger.info(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_BG: insert complete — inserted: ${result.inserted.size}, updated: ${result.updated.size}",
            )
            // Persist the ingest high-water mark so a restart/update can't re-insert this reading,
            // and anchor the production sensor's lifecycle (session start) on first reading.
            store.saveLastIngest(sample.sequence, sample.timestampMs)
            // Off: the reading time. On: a manual SENSOR_CHANGE from the last day.
            val sessionStartMs = if (preferences.get(DexcomOnePlusBooleanKey.AnchorSessionToManualSensorChange)) {
                DexcomOnePlusSensorChangeAnchor.resolve(
                    autoStartMs = sample.timestampMs,
                    lastSensorChangeMs = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp,
                    now = nowMs(),
                )
            } else {
                sample.timestampMs
            }
            store.saveSessionStartIfAbsent(sessionStartMs)
            refreshProductionLifecycle()
        }
    }

    /**
     * The user explicitly started [deviceAddress] from the Start screen: anchor the sensor age on
     * that moment and log a `SENSOR_CHANGE` therapy event.
     *
     * Without it the overview sensor age stayed empty for this source (nothing ever wrote the event)
     * and had to be entered by hand in Care. Anchoring on the user action rather than on the first
     * reading keeps the age honest — the first reading only arrives after the ~30 min warm-up.
     *
     * No-op when the same sensor is simply re-connected, so re-pairing a running sensor does not
     * rejuvenate its age. The therapy event itself follows [BooleanKey.BgSourceCreateSensorChange].
     *
     * @param previousMac MAC stored for the running session before this start, so a sensor anchored
     *   by an older build (session start without its owner MAC) is still seen as the same sensor.
     */
    fun onSensorSessionStarted(
        deviceAddress: String,
        previousMac: String?,
        startMs: Long = nowMs(),
    ) {
        // A first pairing happens after onStart, when nothing was stored yet and so no service was
        // asked for. Before the early return: adopting the same sensor again is still a session.
        if (profileWantsForegroundService()) {
            host.setSessionServiceWanted(true)
        }
        if (!preferences.get(DexcomOnePlusBooleanKey.AnchorSessionToManualSensorChange)) {
            // Not anchored: the caller's timestamp, on the caller's thread.
            if (!sensorStore.startSessionForSensor(deviceAddress, startMs, previousMac)) return
            refreshProductionLifecycle()
            logSensorChange(startMs)
            return
        }
        ioScope().launch {
            val anchoredStartMs = DexcomOnePlusSensorChangeAnchor.resolve(
                autoStartMs = startMs,
                lastSensorChangeMs = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp,
                now = nowMs(),
            )
            if (!sensorStore.startSessionForSensor(deviceAddress, anchoredStartMs, previousMac)) return@launch
            refreshProductionLifecycle()
            logSensorChange(anchoredStartMs)
        }
    }

    /**
     * Creates the `SENSOR_CHANGE` therapy event the overview sensor age reads. The DB transaction
     * ignores a second event with the same timestamp, so this is safe to call again for one session.
     */
    private fun logSensorChange(startMs: Long) {
        if (!preferences.get(BooleanKey.BgSourceCreateSensorChange)) return
        ioScope().launch { writeSensorChange(startMs) }
    }

    /** @return true when a sensor change was really written (the DB refuses a duplicate timestamp). */
    private suspend fun writeSensorChange(startMs: Long): Boolean {
        val result = persistenceLayer.insertCgmSourceData(
            Sources.DexcomOnePlus,
            emptyList(),
            emptyList(),
            sensorInsertionTime = startMs,
        )
        val inserted = result.sensorInsertionsInserted.isNotEmpty()
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_SESSION: sensor change logged startMs=$startMs " +
                "inserted=${result.sensorInsertionsInserted.size}",
        )
        return inserted
    }

    /**
     * Move the insertion time of the sensor that feeds the loop — the Status screen's "correct the
     * insertion date".
     *
     * Two clocks have to agree, or the correction is only half visible: this source's own session
     * start and the `SENSOR_CHANGE` therapy event. Calibration entries are kept.
     *
     * Returns [DexcomOnePlusSensorStartCorrection.Verdict.Disabled] and changes nothing while
     * [DexcomOnePlusBooleanKey.CorrectSensorStart] is off.
     */
    suspend fun correctProductionSensorStart(newStartMs: Long): DexcomOnePlusSensorStartCorrection.Verdict {
        if (!preferences.get(DexcomOnePlusBooleanKey.CorrectSensorStart)) {
            aapsLogger.info(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_SESSION: insertion date refused — correction switch is off",
            )
            return DexcomOnePlusSensorStartCorrection.Verdict.Disabled
        }
        val currentStartMs = sensorStore.loadSessionStart()
        val now = nowMs()
        val verdict = DexcomOnePlusSensorStartCorrection.validate(newStartMs, currentStartMs, now)
        if (verdict != DexcomOnePlusSensorStartCorrection.Verdict.Accepted) {
            aapsLogger.info(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_SESSION: insertion date refused verdict=$verdict " +
                    "newStartMs=$newStartMs currentStartMs=$currentStartMs",
            )
            return verdict
        }
        val from = DexcomOnePlusSensorStartCorrection.cleanupFrom(newStartMs, currentStartMs)
        val taken = persistenceLayer.getTherapyEventDataIncludingInvalidFromTime(from, true)
            .filter { it.type == TE.Type.SENSOR_CHANGE }
            .map { it.timestamp }
            .toSet()
        val stampMs = DexcomOnePlusSensorStartCorrection.freeTimestamp(newStartMs, taken)
        val stale = persistenceLayer.getTherapyEventDataFromToTime(from, now)
            .filter { it.type == TE.Type.SENSOR_CHANGE }
        stale.forEach { event ->
            persistenceLayer.invalidateTherapyEvent(
                id = event.id,
                action = Action.CAREPORTAL_REMOVED,
                source = Sources.DexcomOnePlus,
                note = event.note,
                listValues = listOf(
                    ValueWithUnit.Timestamp(event.timestamp),
                    ValueWithUnit.TEType(event.type),
                ),
            )
        }
        sensorStore.overwriteSessionStart(stampMs)
        val written = writeSensorChange(stampMs)
        healedSensorChangeStartMs = stampMs
        refreshProductionLifecycle()
        rxBus.send(EventRefreshOverview(from = "DexcomOnePlus insertion date"))
        val nowShowing = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_SESSION: insertion date corrected from=$currentStartMs to=$stampMs " +
                "asked=$newStartMs removedSensorChanges=${stale.size} written=$written " +
                "lastSensorChangeNow=$nowShowing",
        )
        return verdict
    }

    override fun onSession(up: Boolean, reason: String?) {
        aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_SESSION: up=$up reason=$reason")
        // A session that ended because someone asked for it must stay ended. Everything else — a
        // lost link, an error, the EGV loop falling out — is a candidate for the watchdog.
        when {
            up                            -> cancelReconnectWatchdog()
            reason in DELIBERATE_STOPS    -> cancelReconnectWatchdog()
            else                          -> armReconnectWatchdog()
        }
    }

    /**
     * Asks for a connection again when the session has been down for a while.
     *
     * One watch at a time: a new one replaces the old, so a session that flaps does not leave a
     * queue behind. It does nothing when the driver has already brought the session back by itself,
     * which is the normal case, and nothing when no sensor is stored.
     */
    private fun armReconnectWatchdog() {
        reconnectWatchdog?.cancel()
        reconnectWatchdog = ioScope().launch {
            delay(RECONNECT_WATCHDOG_MS)
            if (driver.isSessionUp()) return@launch
            val real = driver
            aapsLogger.info(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_SESSION: session still down after ${RECONNECT_WATCHDOG_MS / 60_000} min, asking again",
            )
            host.attachPlatform(real)
            real.resumeStoredSession()
        }
    }

    private fun cancelReconnectWatchdog() {
        reconnectWatchdog?.cancel()
        reconnectWatchdog = null
    }

    /**
     * Whether this phone's OEM profile asks for a foreground service.
     *
     * Every profile says true today, which is why the flag looked dead. It is read rather than
     * assumed so a future profile can opt out on a stack where the service costs more than it buys.
     */
    private fun profileWantsForegroundService(): Boolean =
        DeviceProfileRegistry.resolve().useForegroundService

    /**
     * Keep the `connectedDevice` service alive while EITHER slot has a sensor stored, and give the
     * privilege back when neither does.
     *
     * A pre-soak is a Bluetooth session like any other, so a phone whose only stored sensor is a
     * pre-soak needs the service just as much. It never throws: it is called from production paths
     * as well as from pre-soak ones.
     *
     * The `runCatching` is on purpose: a failure is logged and does not break a promotion.
     */
    private fun refreshSessionService() {
        runCatching {
            val wanted = profileWantsForegroundService() &&
                (sensorStore.load() != null || stagingStore.load() != null)
            host.setSessionServiceWanted(wanted)
        }.onFailure { t ->
            aapsLogger.error(LTag.BGSOURCE, "DEXCOM_ONEPLUS_SESSION: session service refresh failed, ${t.message}", t)
        }
    }

    override fun onError(message: String, fatal: Boolean) {
        aapsLogger.error(LTag.BGSOURCE, "DEXCOM_ONEPLUS_ERROR: fatal=$fatal $message")
    }

    // ---------------- Dual-sensor (staging / pre-soak) ----------------

    /**
     * Undoes a poisoned pair of stores: the same transmitter recorded in both slots.
     *
     * The arbiter stops a second session being opened, but it cannot decide *which* slot was the
     * mistake, and it would leave the loser of the race silently unable to connect for the rest of
     * the run. So the ambiguity is resolved here, once, before anything connects.
     *
     * Production wins on purpose. It is the sensor the loop is dosing on, its store carries the
     * session start the overview sensor age reads, and a pre-soak is by definition the disposable
     * one. The staging slot is cleared the same way an explicit cancel clears it, so a later pre-soak
     * starts from a genuine fresh soak clock rather than inheriting a start time it did not earn.
     */
    private fun reconcileSlotsOnSameSensor() {
        val productionMac = sensorStore.load()?.lastMac ?: return
        if (!DexcomOnePlusStaging.isSameTransmitter(stagingStore.load()?.lastMac, productionMac)) return
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_STAGING: both slots hold the same sensor — clearing the pre-soak slot, " +
                "production keeps it (cross-slot collision recovery)",
        )
        runCatching { stagingDriver.removeWatcher(stagingWatcher) }
        runCatching { stagingDriver.disconnect() }
        runCatching { stagingDriver.shutdown() }
        runCatching { stagingStore.clearAll() }
        stagingPresent = false
        stagingWarmupDone = false
        stagingValidEgvCount = 0
        stagingLastValueMgdl = null
        stagingLastValueAtMs = null
        stagingBufferLock.withLock { stagingBuffer.clear() }
        _stagingLifecycle.value = null
        refreshStagingState()
        refreshStagingEvidence()
    }

    /**
     * Whether [deviceAddress] is the sensor the pre-soak slot holds.
     *
     * The one thing the dual-slot design cannot separate is the sensor itself. Two slots on one MAC
     * means two KEKS handshakes over a single ACL link: both GATT clients subscribe to the same Auth
     * and ExtraData characteristics, so each receives the other's notifications and the transmitter
     * sees one interleaved write stream. A field log showed both sides dying inside libkeks within
     * 1.6 s of the second connect, on a handshake that had just reported `authenticated=1`. It is
     * not a race that sometimes bites — it is certain.
     */
    fun isStagingSensor(deviceAddress: String): Boolean =
        DexcomOnePlusStaging.isSameTransmitter(stagingStore.load()?.lastMac, deviceAddress)

    /** Whether [deviceAddress] is the sensor currently feeding the loop — see [isStagingSensor]. */
    fun isProductionSensor(deviceAddress: String): Boolean =
        DexcomOnePlusStaging.isSameTransmitter(sensorStore.load()?.lastMac, deviceAddress)

    /**
     * Starts a pre-soak on [deviceAddress].
     *
     * @return false when the request was refused because that sensor already feeds the loop. The
     *   caller must not connect the staging driver in that case. Refusing here rather than only in
     *   the UI keeps the invariant for every future call site.
     */
    fun beginStaging(deviceAddress: String): Boolean {
        // Like onSensorSessionStarted, and before the early return: a pre-soak started while
        // production is stopped is still a Bluetooth session, and without this it would run its GATT
        // link with no foreground service at all.
        if (profileWantsForegroundService()) {
            host.setSessionServiceWanted(true)
        }
        if (isProductionSensor(deviceAddress)) {
            aapsLogger.info(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_STAGING: refused — this sensor already feeds the loop, a pre-soak on it " +
                    "would run a second KEKS handshake on the same link",
            )
            return false
        }
        host.attachPlatform(stagingDriver)
        stagingDriver.addWatcher(stagingWatcher)
        stagingPresent = true
        // The soak clock starts when the sensor is applied, not at its first reading (~30 min later).
        // MAC-owned, so re-running the start on the same sensor keeps its clock while a different
        // sensor restarts it — a PIN-only entry can no longer inherit a previous sensor's soak.
        // Read before the driver overwrites the slot's MAC in saveIdentity / connect.
        val previousStagingMac = stagingStore.load()?.lastMac
        val newSensor = stagingStore.startSessionForSensor(deviceAddress, nowMs(), previousStagingMac)
        if (newSensor) {
            // Another sensor: everything the previous soak collected is void.
            stagingWarmupDone = false
            stagingValidEgvCount = 0
            stagingLastValueMgdl = null
            stagingLastValueAtMs = null
            stagingBufferLock.withLock { stagingBuffer.clear() }
            // Durability: a restart must be able to tell "a staging sensor is warming" from "no
            // staging sensor" — see resumeStagingSessionIfStored.
            stagingStore.saveSlotProgress(present = true, validEgvCount = 0)
            stagingStore.saveSlotWarmupDone(false)
        } else {
            // Same sensor started again (one extra tap on the Start screen is enough). The store keeps
            // its soak clock, so its progress must be kept too: resetting it sent a settled slot back
            // to warm-up and hid the promote button for another ~30 min for nothing.
            stagingWarmupDone = stagingStore.loadSlotWarmupDone()
            stagingValidEgvCount = stagingStore.loadSlotValidEgvCount()
            stagingStore.saveSlotProgress(present = true, validEgvCount = stagingValidEgvCount)
        }
        stagingWarming = !stagingWarmupDone
        refreshStagingLifecycle()
        refreshStagingState()
        refreshStagingEvidence()
        aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_STAGING: begin")
        return true
    }

    /**
     * Restore a STAGING sensor after a process / plugin restart.
     *
     * Without this the dual-instance split silently lost the pre-soak sensor: [onStart] only resumed
     * the production driver, so a staged sensor that was already authenticated (identity + MAC +
     * KEKS key persisted in its own store) had **no** session recreated — it could never reconnect,
     * never leave warm-up and never reach the promotion gate, while its in-memory state reset to
     * ABSENT. Never publishes to the loop: promotion stays an explicit user action.
     */
    private fun resumeStagingSessionIfStored(): Boolean {
        if (!stagingStore.loadSlotPresent()) return false
        host.attachPlatform(stagingDriver)
        stagingDriver.addWatcher(stagingWatcher)
        // The driver itself vetoes an incomplete stored session (no MAC / PIN / KEKS key).
        // It also returns false when a session is ALREADY running (nothing to queue) — tearing the
        // slot down there would drop a live pre-soak on a plugin disable/enable cycle.
        val alreadyRunning = stagingDriver.isSessionUp()
        val queued = stagingDriver.resumeStoredSession()
        if (!queued && !alreadyRunning) {
            // Nothing is running for this slot, so it must not be shown as a warming sensor — that
            // was the "stuck in warm-up forever" state. The store is kept intact (clearing it would
            // reset session_start and let a re-start skip the soak), so the user can simply re-run
            // the staging start from the Start screen.
            runCatching { stagingDriver.removeWatcher(stagingWatcher) }
            stagingPresent = false
            stagingWarming = false
            _stagingLifecycle.value = null
            _stagingState.value = StagingState.ABSENT
            _stagingSettleRemainingMs.value = null
            aapsLogger.warn(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_STAGING: auto-resume skipped — stored staging session incomplete, re-run the sensor start",
            )
            return false
        }
        stagingPresent = true
        stagingValidEgvCount = stagingStore.loadSlotValidEgvCount()
        // A settled sensor must not be shown as warming again after a restart — the latch is durable.
        stagingWarmupDone = stagingStore.loadSlotWarmupDone()
        stagingWarming = !stagingWarmupDone
        refreshStagingLifecycle()
        refreshStagingState()
        refreshStagingEvidence()
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_STAGING: auto-resume queued=$queued alreadyRunning=$alreadyRunning " +
                "egvCount=$stagingValidEgvCount warmupDone=$stagingWarmupDone",
        )
        return true
    }

    /**
     * Stop and discard the staging sensor (no effect on production).
     *
     * Safe to call right after a promotion too: [stagingDriver] then resolves a brand new instance
     * (the promoted one was handed to [DexcomOnePlusHost.promoteStagingInstance] and is production
     * now), so there is nothing here to tear down.
     */
    fun cancelStaging() {
        runCatching { stagingDriver.removeWatcher(stagingWatcher) }
        runCatching { stagingDriver.disconnect() }
        runCatching { stagingDriver.shutdown() }
        // Clear the staging store so a re-started staging session gets a FRESH session start. Without
        // this, a stale session_start_ms would let a new sensor reach READY without a real 12 h soak
        // (safety-critical settle-guard bypass).
        runCatching { stagingStore.clearAll() }
        stagingPresent = false
        stagingWarming = false
        stagingWarmupDone = false
        stagingValidEgvCount = 0
        stagingLastValueMgdl = null
        stagingLastValueAtMs = null
        stagingBufferLock.withLock { stagingBuffer.clear() }
        _stagingLifecycle.value = null
        _stagingEvidence.value = null
        _stagingState.value = StagingState.ABSENT
        _stagingSettleRemainingMs.value = null
        // Cancelling the only pre-soak on a phone with no production sensor must give the privilege
        // back, otherwise the notification would stay for ever.
        refreshSessionService()
        aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_STAGING: cancelled")
    }

    /**
     * SAFETY-CRITICAL: promote the staging
     * sensor to production — the ONLY action that changes the loop's glucose source. Guarded on
     * [StagingState.READY]. On success: stops the old production sensor, resets the ingest dedup floor
     * (new EGV sequence space), migrates the staging identity into the production store (so a restart
     * durably resumes the promoted sensor), and hands the staging driver INSTANCE the production role
     * via [DexcomOnePlusHost.promoteStagingInstance] — its live BLE link keeps feeding the loop
     * with no gap, and every other reader of [DexcomOnePlusHost.productionDriver] (the
     * Status/Warmup screens, the reconnect watchdog) now sees it too.
     */
    override suspend fun promoteStagingToProduction(allowEarly: Boolean): PromotionResult =
        promoteMutex.withLock { promoteLocked(allowEarly, byAuto = false) }

    /** The promotion itself. Call it only while holding [promoteMutex]. */
    private suspend fun promoteLocked(allowEarly: Boolean, byAuto: Boolean): PromotionResult {
        if (!stagingPresent) return rejectPromotion(PromotionRejectReason.STAGING_ABSENT, allowEarly)
        if (stagingValidEgvCount < DexcomOnePlusStaging.STAGING_MIN_VALID_EGV)
            return rejectPromotion(PromotionRejectReason.STAGING_NO_VALID_GLUCOSE, allowEarly)
        // Defense-in-depth: re-verify the REAL soak time from the trusted persisted start rather than
        // trusting the cached _stagingState — a stale start (e.g. cancel/restage) must never authorise
        // an under-soaked promotion onto the loop.
        val startMs = stagingStore.loadSessionStart()
        if (startMs <= 0L) return rejectPromotion(PromotionRejectReason.STAGING_NOT_SETTLED, allowEarly)
        if (allowEarly) {
            // The user knowingly gives up the soak (production sensor stopped early). The evidence
            // gates stay: enough valid readings, and one of them recent — never a silent sensor.
            if (!DexcomOnePlusStaging.canPromoteEarly(stagingValidEgvCount, stagingLastValueAtMs, nowMs()))
                return rejectPromotion(PromotionRejectReason.STAGING_NO_RECENT_GLUCOSE, allowEarly)
        } else {
            if (nowMs() - startMs < DexcomOnePlusStaging.STAGING_MIN_SETTLE_MS)
                return rejectPromotion(PromotionRejectReason.STAGING_NOT_SETTLED, allowEarly)
            if (_stagingState.value != StagingState.READY)
                return rejectPromotion(PromotionRejectReason.STAGING_NOT_SETTLED, allowEarly)
        }

        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_PROMOTE: staging → production (by ${if (byAuto) "auto" else "user"}) early=$allowEarly " +
                "soakMs=${nowMs() - startMs} egvCount=$stagingValidEgvCount",
        )
        // The net must not ask for a link while the two instances are being swapped: it would ask the
        // instance that is about to be retired.
        cancelReconnectWatchdog()
        // Taken before the swap below: after it, `stagingDriver` would build a brand new instance,
        // and `driver` would already be the promoted one.
        val promoted = stagingDriver
        val outgoing = driver
        // 1) Retire the old production sensor — stop its callbacks (no more loop feed from it).
        runCatching { outgoing.removeWatcher(this) }
        runCatching { outgoing.disconnect() }
        // The live link is kept, so there is no gap in the glucose. Adding first and removing second
        // on purpose: a moment where both watchers fire costs one buffered reading (harmless, still
        // gated by [stagingPresent] below), while removing first would lose one.
        promoted.addWatcher(this)
        runCatching { promoted.removeWatcher(stagingWatcher) }
        // 2) New sensor = different EGV sequence space → reset the persistent dedup floor.
        DexcomOnePlusIngest.reset()
        // 3) Durability: migrate the staging identity into the production store so a later restart
        //    resumes the promoted sensor on the production driver.
        stagingStore.load()?.let { sensorStore.adopt(it, startMs) }
        // 4) Close the pre-soak slot BEFORE its file is wiped: the staging
        // callbacks are guarded on [stagingPresent], so a reading still in flight can no longer
        // write `slot_warmup_done` / `slot_present` back into the file that is about to be cleared.
        stagingPresent = false
        stagingWarming = false
        stagingWarmupDone = false
        stagingValidEgvCount = 0
        stagingLastValueMgdl = null
        stagingLastValueAtMs = null
        stagingBufferLock.withLock { stagingBuffer.clear() }
        _stagingLifecycle.value = null
        _stagingEvidence.value = null
        _stagingState.value = StagingState.ABSENT
        _stagingSettleRemainingMs.value = null
        // 5) Swap the registry: the host's production driver is now the promoted instance
        // everywhere — the Status/Warmup screens and the reconnect watchdog read it directly, and
        // before this swap they kept reading the retired instance for the rest of the session (the
        // "Status stuck at IDLE" and "no reconnect after a dropped link" bugs).
        // It also rebinds the promoted instance onto the production file, so it must happen BEFORE
        // the staging file is cleared: its live link reads that file on every reconnect.
        val retired = host.promoteStagingInstance()
        runCatching { retired?.shutdown() }
        runCatching { if (outgoing !== promoted && outgoing !== retired) outgoing.shutdown() }
        // 6) Only now retire the staging store — nothing points at it any more.
        stagingStore.clearAll()
        // The last reading belongs to the sensor just retired: a calibration must wait for the new one.
        lastProductionReading = null
        // The promoted sensor becomes the loop's sensor: its age must show on the overview from the
        // moment it was applied (its staging start, verified above), not from the promotion.
        logSensorChange(startMs)
        // That back-dated session start would otherwise pull every fingerstick taken during the
        // pre-soak into this sensor's fit — all of them paired against the sensor just retired.
        // A throw here does not roll the exchange above back: the result is OkBoundFailed and the
        // user is told. Same wall clock as the soak math in this file (`Clock.System`).
        val boundFailure: Throwable? = try {
            activePlugin.activeCalibration.ignoreEntriesBefore(nowMs())
            null
        } catch (thrown: Throwable) {
            thrown
        }
        if (boundFailure != null) {
            notifyPromotionProblem(SourceStrings.dexcom_oneplus_staging_promote_bound_failed, boundFailure)
        }
        // The promoted instance is now `default()`. Make sure this plugin watches it.
        runCatching { host.attachProductionDriver(watcher = this) }
        // The production status (_warmup) was last set by the OLD production driver and nothing else
        // pushes to it here: a steady-state promoted driver emits no new phase-change event, so
        // without this the Status screen keeps showing whatever phase production had before the
        // promotion (e.g. stuck at IDLE) until the app restarts and onStart() re-polls the driver.
        onWarmup(promoted.warmupState())
        refreshProductionLifecycle()
        // One sensor moved from the pre-soak slot into production, so the service is still wanted,
        // but the reason for it has changed. Asked again so the two slots are counted as they are.
        refreshSessionService()
        // A pre-soak whose link happened to be down at this exact moment must not leave the loop
        // without a sensor until the watchdog wakes up 5 min later.
        val followUpFailure: Throwable? = try {
            if (!promoted.isSessionUp()) promoted.resumeStoredSession()
            null
        } catch (thrown: Throwable) {
            thrown
        }
        if (followUpFailure != null) {
            // Same protection as refreshSessionService: a throw while reading the
            // store must not escape. The flag is whatever load() actually returned.
            val identityPresent = runCatching { sensorStore.load() != null }.getOrElse { thrown ->
                aapsLogger.error(
                    LTag.BGSOURCE,
                    "DEXCOM_ONEPLUS_PROMOTE: production identity unreadable, ${thrown.message}",
                    thrown,
                )
                false
            }
            val text = if (identityPresent) {
                SourceStrings.dexcom_oneplus_staging_promote_follow_up_failed
            } else {
                SourceStrings.dexcom_oneplus_staging_promote_follow_up_failed_no_identity
            }
            notifyPromotionProblem(text, followUpFailure)
            if (boundFailure != null) return PromotionResult.OkBoundFailed
            return PromotionResult.OkFollowUpFailed(productionIdentityPresent = identityPresent)
        }
        if (boundFailure != null) return PromotionResult.OkBoundFailed
        return PromotionResult.Ok
    }

    /** Error log plus the user-visible alert. Does not roll the exchange back. */
    private fun notifyPromotionProblem(text: TextRef, error: Throwable) {
        val message = resources.gs(text)
        aapsLogger.error(LTag.BGSOURCE, message, error)
        promotionAlerter(message)
    }

    /**
     * A refused promotion used to be visible only as a toast, so a support package could not say why
     * the user's "Promote" did nothing. Every refusal now leaves one line, with the state it saw.
     */
    private fun rejectPromotion(reason: PromotionRejectReason, allowEarly: Boolean): PromotionResult {
        val startMs = stagingStore.loadSessionStart()
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_PROMOTE: refused reason=$reason early=$allowEarly present=$stagingPresent " +
                "state=${_stagingState.value} egvCount=$stagingValidEgvCount " +
                "soakMs=${if (startMs > 0L) nowMs() - startMs else -1L} " +
                "lastValueAgeMs=${stagingLastValueAtMs?.let { nowMs() - it } ?: -1L}",
        )
        return PromotionResult.Rejected(reason)
    }

    private fun handleStagingWarmup(state: OnePlusWarmupState) {
        // A slot that is no longer there must not write its progress back into a file that was just
        // wiped — a promotion detaches this watcher from the promoted instance (see
        // promoteStagingToProduction), so from that moment this is only ever called for a genuinely
        // new pre-soak.
        if (!stagingPresent) return
        // Leaving warm-up is a latched EVENT, never re-derived from the live phase — see
        // [DexcomOnePlusStaging.applyWarmupPhase] for why.
        val decision = DexcomOnePlusStaging.applyWarmupPhase(
            warmupDoneBefore = stagingWarmupDone,
            readyPhase = state.phase == OnePlusWarmupState.Phase.READY,
        )
        if (decision.warmupDone) markStagingWarmupDone() else stagingWarming = decision.warming
        refreshStagingState()
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_STAGING: warmup phase=${state.phase} warmupDone=$stagingWarmupDone",
        )
    }

    /** Latch (and persist) "this staging sensor has left warm-up" — see [handleStagingWarmup]. */
    private fun markStagingWarmupDone() {
        if (stagingWarmupDone) return
        stagingWarmupDone = true
        stagingWarming = false
        stagingStore.saveSlotWarmupDone(true)
        aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_STAGING: warm-up done — slot is settling")
    }

    private fun handleStagingGlucose(sample: OnePlusGlucoseSample) {
        // A slot that is no longer there must not buffer a reading into state that was just reset —
        // a promotion detaches this watcher from the promoted instance (see
        // promoteStagingToProduction), so from that moment this is only ever called for a genuinely
        // new pre-soak.
        if (!stagingPresent) return
        // Collect-only: kept in the local buffer, NEVER passed to insertCgmSourceData.
        stagingBufferLock.withLock {
            stagingBuffer.addLast(sample.timestampMs to sample.mgdl)
            while (stagingBuffer.size > STAGING_BUFFER_CAP) stagingBuffer.removeFirst()
        }
        // A collected reading is proof warm-up is over, even if no READY phase was ever emitted.
        markStagingWarmupDone()
        stagingValidEgvCount++
        stagingLastValueMgdl = sample.mgdl
        stagingLastValueAtMs = sample.timestampMs
        // Survive a restart with the promotion gate intact (the count is the gate).
        stagingStore.saveSlotProgress(present = true, validEgvCount = stagingValidEgvCount)
        refreshStagingLifecycle()
        refreshStagingState()
        refreshStagingEvidence()
        aapsLogger.debug(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_STAGING: buffered ${sample.mgdl.toInt()} count=$stagingValidEgvCount (not published)",
        )
        maybeAutoPromote()
    }

    /**
     * Switches the pre-soak sensor to production by itself when [DexcomOnePlusBooleanKey.AutoPromote]
     * is on and [StagingAutoPromotion] agrees.
     *
     * Called on every staging reading, so it needs no timer and survives a restart. The switch runs
     * on [ioScope], never on the BLE thread that delivered the reading, and goes through the same
     * [promoteLocked] as the button, with all of its checks. Before the 12 hour settling time it uses
     * the button's "not settled" path.
     */
    private fun maybeAutoPromote() {
        // Named `askedAtMs`, not `nowMs`, so it cannot be mistaken for the `nowMs()` clock helper
        // that the rest of this file calls. One reading of the clock for the whole decision.
        val askedAtMs = nowMs()
        if (askedAtMs - lastAutoPromoteTryMs < StagingAutoPromotion.RETRY_INTERVAL_MS) return
        if (!preferences.get(DexcomOnePlusBooleanKey.AutoPromote)) return
        val delayHours = preferences.get(DexcomOnePlusIntKey.AutoPromoteHours)
        val startMs = stagingStore.loadSessionStart()
        val decision = StagingAutoPromotion.decide(
            enabled = true,
            delayHours = delayHours,
            startMs = startMs,
            readings = stagingBufferLock.withLock { stagingBuffer.toList() },
            minRecentReadings = AUTO_PROMOTE_MIN_RECENT_READINGS,
            nowMs = askedAtMs,
        )
        if (decision != StagingAutoPromotion.Decision.PROMOTE) return
        val claimed = autoPromoteLock.withLock {
            if (autoPromoteInFlight) false else { autoPromoteInFlight = true; true }
        }
        if (!claimed) return
        ioScope().launch {
            try {
                val allowEarly = askedAtMs - startMs < DexcomOnePlusStaging.STAGING_MIN_SETTLE_MS
                aapsLogger.info(
                    LTag.BGSOURCE,
                    "DEXCOM_ONEPLUS_PROMOTE: auto switch asked delayHours=$delayHours soakMs=${askedAtMs - startMs} early=$allowEarly",
                )
                // Not cancellable: a stop must not cut the exchange in half. It is a bounded piece
                // of work that schedules nothing new.
                val result = withContext(NonCancellable) {
                    promoteMutex.withLock { promoteLocked(allowEarly, byAuto = true) }
                }
                if (result is PromotionResult.Rejected) {
                    // Refused: the reason is already in the log, see rejectPromotion. Try again later.
                    lastAutoPromoteTryMs = nowMs()
                } else {
                    // Any other result means the swap happened, also when a follow-up step failed.
                    notificationManager.post(
                        NotificationId.CGM_STAGING_AUTO_PROMOTED,
                        resources.gs(SourceStrings.staging_auto_promote_done, resources.gs(CoreUiStrings.units_format_hours, delayHours)),
                    )
                }
            } finally {
                autoPromoteLock.withLock { autoPromoteInFlight = false }
            }
        }
    }

    private fun refreshProductionLifecycle() {
        val startMs = sensorStore.loadSessionStart()
        _lifecycle.value =
            DexcomOnePlusStaging.computeLifecycle(startMs, nowMs())
        if (startMs > 0L) ioScope().launch { healMissingSensorChange(startMs) }
    }

    /**
     * Put back the `SENSOR_CHANGE` of the running sensor when the database has none.
     *
     * The driver knows when its sensor started; the overview, the status line and the calibration
     * plugin all read a therapy event instead. When that event is missing, every one of them falls
     * back to the previous sensor.
     *
     * Healed once per session start: if the user deliberately deletes the event again, it is not
     * resurrected on the next reading.
     *
     * [DexcomOnePlusBooleanKey.RepairMissingSensorChange] is a user setting, on by default. Nothing
     * is written while "create sensor change" is off (see below). An occupied timestamp is stepped
     * by [DexcomOnePlusSensorStartCorrection.freeTimestamp].
     */
    private suspend fun healMissingSensorChange(startMs: Long) {
        if (!preferences.get(DexcomOnePlusBooleanKey.RepairMissingSensorChange)) return
        if (healedSensorChangeStartMs == startMs) return
        if (!preferences.get(BooleanKey.BgSourceCreateSensorChange)) {
            // Said once per session, because it explains an age that can never be right: with this
            // setting off nothing writes a sensor change, so every screen that reads the therapy
            // event keeps showing the sensor before this one.
            healedSensorChangeStartMs = startMs
            aapsLogger.info(
                LTag.BGSOURCE,
                "DEXCOM_ONEPLUS_SESSION: sensor age not repaired — 'create sensor change' is off " +
                    "(driver start $startMs)",
            )
            return
        }
        val last = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp
        // A valid event at or after this session's start means the age is already right.
        if (last != null && last >= startMs - SENSOR_CHANGE_MATCH_TOLERANCE_MS) {
            healedSensorChangeStartMs = startMs
            return
        }
        healedSensorChangeStartMs = startMs
        val taken = persistenceLayer.getTherapyEventDataIncludingInvalidFromTime(
            startMs - SENSOR_CHANGE_MATCH_TOLERANCE_MS,
            true,
        )
            .filter { it.type == TE.Type.SENSOR_CHANGE }
            .map { it.timestamp }
            .toSet()
        val stampMs = DexcomOnePlusSensorStartCorrection.freeTimestamp(startMs, taken)
        val written = writeSensorChange(stampMs)
        aapsLogger.info(
            LTag.BGSOURCE,
            "DEXCOM_ONEPLUS_SESSION: sensor age had no therapy event — rewritten at $stampMs " +
                "(driver start $startMs, previous last=$last, written=$written)",
        )
        if (written) rxBus.send(EventRefreshOverview(from = "DexcomOnePlus sensor age repair"))
    }

    private fun refreshStagingLifecycle() {
        _stagingLifecycle.value =
            if (stagingPresent) DexcomOnePlusStaging.computeLifecycle(stagingStore.loadSessionStart(), nowMs())
            else null
    }

    /**
     * Publish what the collect-only slot has actually gathered. Without it the user is asked to wait
     * ~12 h on a sensor that shows no reading anywhere, so a dead staging sensor is indistinguishable
     * from a healthy one.
     */
    private fun refreshStagingEvidence() {
        _stagingEvidence.value =
            if (!stagingPresent) null
            else CgmStagingEvidence(
                validCount = stagingValidEgvCount,
                lastValueMgdl = stagingLastValueMgdl,
                lastValueAtEpochMs = stagingLastValueAtMs,
            )
    }

    private fun refreshStagingState() {
        val sessionStartMs = stagingStore.loadSessionStart()
        val now = nowMs()
        _stagingState.value = DexcomOnePlusStaging.computeStagingState(
            present = stagingPresent,
            warming = stagingWarming,
            sessionStartMs = sessionStartMs,
            validEgvCount = stagingValidEgvCount,
            nowMs = now,
        )
        _stagingSettleRemainingMs.value = DexcomOnePlusStaging.settleRemainingMs(
            present = stagingPresent,
            sessionStartMs = sessionStartMs,
            nowMs = now,
        )
    }

    /**
     * Wall clock, in milliseconds since the epoch.
     *
     * One place, so every soak, promotion and lifecycle sum in this file reads the same clock.
     */
    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {

        /**
         * How far before the driver's own session start a therapy event may sit and still count as
         * this sensor's. Covers a manual entry made a few minutes before the sensor was paired.
         * A date correction may shift the stamp by a few seconds, see
         * [DexcomOnePlusSensorStartCorrection.freeTimestamp].
         */
        private const val SENSOR_CHANGE_MATCH_TOLERANCE_MS = 15L * 60L * 1000L

        /** How far back to seed the ingest dedup from the DB on start — wide enough to cover any
         *  plausible on-reconnect backfill, capped downstream by [DexcomOnePlusIngest] RECENT_CAP. */
        private const val INGEST_SEED_WINDOW_MS = 6L * 60L * 60L * 1000L

        /** Automatic switch: readings needed in the last 30 min (6 expected at a 5 min cadence). */
        private const val AUTO_PROMOTE_MIN_RECENT_READINGS = 4

        /** Staging QA buffer cap (~24 h at 5-min cadence). */
        private const val STAGING_BUFFER_CAP = 288

        /** How long the session may stay down before the plugin asks for a connection again. */
        private const val RECONNECT_WATCHDOG_MS = 5L * 60L * 1000L

        /**
         * Session-end reasons the user or the app asked for. The watchdog must not undo those; every
         * other reason is a link that went away on its own and is worth chasing.
         */
        private val DELIBERATE_STOPS = setOf("disconnect", "shutdown", "cancelled")
    }
}
