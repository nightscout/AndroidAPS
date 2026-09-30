package app.aaps.plugins.aps.loop.runningMode

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.aps.ApsStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reconciles pump delivery state with the currently active running mode.
 *
 * Observes changes to the RunningMode table. Whenever the active mode changes, consults
 * [ReconcilerDecision] to compute the intended pump-side action and applies it — subject to
 * idempotency checks against the current pump TBR and extended-bolus state.
 *
 * Gated by `config.APS`: only the device that owns the pump drives it. Followers still
 * observe mode changes but issue no pump commands.
 *
 * On startup, reconciles the current active mode against the current pump state regardless of
 * whether a transition is observed — handles the "app killed mid-window, restarted, pump has
 * drifted from DB state" case.
 *
 * A mode change alone is not enough for the zero-delivery modes: a zero TBR that failed (or was
 * cancelled and then failed to be set again) would never be retried, and the pump would give
 * basal while AAPS shows it disconnected. [verifyZeroDelivery] is the periodic check for that.
 */

@SingleIn(AppScope::class)
@Inject
class RunningModeReconciler(
    private val persistenceLayer: PersistenceLayer,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val profileFunction: ProfileFunction,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rh: TextResolver,
    private val notificationManager: NotificationManager,
    private val appScope: CoroutineScope
) {

    // Serializes the change observer and [verifyZeroDelivery]. Both read the pump state and then send
    // commands, so without it the two could each send their own zero TBR.
    private val mutex = Mutex()

    // Guarded by [mutex]. When the zero TBR was first seen missing in a zero-delivery mode, and when the
    // alarm was last raised. Both null while everything is fine.
    private var zeroTbrMissingSince: Long? = null
    private var lastZeroTbrAlarm: Long? = null

    // The first two are written and read from different threads; [reconciledMode] / [reconciledRowId] expose them to
    // instrumented tests that poll from a different thread than the appScope coroutine writing them;
    // without it there is no happens-before edge and a poll may never observe the write.
    private var lastReconciledMode: RM.Mode? = null
    private var lastReconciledRowId: Long? = null
    private var lastReconciledDuration: Long = -1L
    private var started = false
    private var observerJob: Job? = null

    fun start() {
        if (started) return
        started = true
        if (!config.APS) {
            aapsLogger.debug(LTag.APS, "RunningModeReconciler: config.APS=false, pump-side path disabled")
            return
        }
        observerJob = appScope.launch {
            mutex.withLock { reconcileStartup() }
            persistenceLayer.observeChanges(RM::class).collect { _ ->
                mutex.withLock { onAnyChange() }
            }
        }
    }

    /**
     * Reset all in-memory state so the next [start] re-baselines from scratch. Test-only.
     *
     * The reconciler is a process-wide @Singleton, so a single instance is shared across every
     * instrumented test in the process while each test's `@Before` wipes the DB. Without this
     * reset the stale dedup baseline ([lastReconciledMode] / [lastReconciledRowId] /
     * [lastReconciledDuration]) and the still-running observer coroutine leak across tests: a
     * freshly inserted mode can collide with the previous test's baseline and [onAnyChange]
     * de-duplicates it, so the expected pump action is never issued. Cancels the observer and
     * clears `started` so the following [start] launches a clean observer + startup reconcile.
     */
    @VisibleForTesting
    fun resetState() {
        observerJob?.cancel()
        observerJob = null
        started = false
        lastReconciledMode = null
        lastReconciledRowId = null
        lastReconciledDuration = -1L
        zeroTbrMissingSince = null
        lastZeroTbrAlarm = null
    }

    /**
     * The mode the reconciler has most recently acted on — `null` until the first reconcile. Test-only.
     *
     * Non-null means [reconcileStartup] has completed, which is the last statement before the change
     * observer subscribes. Instrumented tests poll this instead of sleeping a fixed interval after
     * [start].
     */
    @VisibleForTesting
    fun reconciledMode(): RM.Mode? = lastReconciledMode

    /**
     * The id of the RM row the reconciler has most recently acted on — `null` until the first
     * reconcile. Test-only.
     *
     * A test that writes a baseline mode and then the mode under test must know the baseline was
     * actually *observed* first, otherwise both writes collapse into one [onAnyChange] and the
     * transition under test never happens. Because [start] subscribes to a replay-less change flow only
     * after [reconcileStartup] returns, a write landing in that window is silently never delivered.
     * Polling this against the id returned by the insert is the deterministic replacement for a sleep.
     */
    @VisibleForTesting
    fun reconciledRowId(): Long? = lastReconciledRowId

    private suspend fun reconcileStartup() {
        val now = dateUtil.now()
        val active = persistenceLayer.getRunningModeActiveAt(now)
        val action = ReconcilerDecision.decide(RM.Mode.CLOSED_LOOP, active.mode)
        executeAction(action, active, now)
        handleStartupDrift(active.mode, now)
        updateZeroTbrAlarm(active)
        lastReconciledMode = active.mode
        lastReconciledRowId = active.id
        lastReconciledDuration = active.duration
        aapsLogger.debug(LTag.APS, "RunningModeReconciler: startup reconcile, mode=${active.mode}")
    }

    private suspend fun handleStartupDrift(activeMode: RM.Mode, now: Long) {
        if (activeMode == RM.Mode.DISCONNECTED_PUMP || activeMode == RM.Mode.SUPER_BOLUS) return
        val currentTbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (currentTbr != null && currentTbr.type == TB.Type.EMULATED_PUMP_SUSPEND) {
            aapsLogger.info(
                LTag.APS,
                "RunningModeReconciler: startup drift — pump has EMULATED_PUMP_SUSPEND TBR but mode is $activeMode, canceling"
            )
            val result = commandQueue.cancelTempBasal(enforceNew = true)
            if (!result.success) {
                aapsLogger.warn(LTag.APS, "RunningModeReconciler: startup-drift cancelTbr failed: ${result.comment}")
                rxBus.send(EventShowSnackbar(rh.gs(CoreUiStrings.temp_basal_delivery_error), EventShowSnackbar.Type.Error))
            }
        }
    }

    private suspend fun onAnyChange() {
        val now = dateUtil.now()
        val current = persistenceLayer.getRunningModeActiveAt(now)
        val prevMode = lastReconciledMode
        val prevId = lastReconciledRowId
        val prevDuration = lastReconciledDuration
        if (prevMode == current.mode && prevId == current.id && prevDuration == current.duration) return
        val action = ReconcilerDecision.decide(prevMode ?: RM.Mode.CLOSED_LOOP, current.mode)
        executeAction(action, current, now)
        updateZeroTbrAlarm(current)
        lastReconciledMode = current.mode
        lastReconciledRowId = current.id
        lastReconciledDuration = current.duration
    }

    /**
     * Periodic check while a zero-delivery mode (DISCONNECTED_PUMP / SUPER_BOLUS) is active.
     *
     * Sends the zero TBR again when it is missing, or when it ends within [RENEW_BEFORE_END_MS] while
     * the mode goes on longer (a pump whose maximum TBR is shorter than the mode, or a mode without
     * end). Raises [NotificationId.ZERO_DELIVERY_NOT_SET] when the zero TBR stays missing for
     * [ALARM_AFTER_MS], and repeats it every [ALARM_REPEAT_MS] until it is in place.
     *
     * The running mode is never changed here. It is what the user asked for; the job is to make the
     * pump match it, and to say so loudly when that does not work.
     *
     * Does nothing until [start] has run on a device that drives the pump.
     */
    suspend fun verifyZeroDelivery() {
        if (observerJob == null) return
        mutex.withLock {
            val now = dateUtil.now()
            val active = persistenceLayer.getRunningModeActiveAt(now)
            if (ReconcilerDecision.bucketOf(active.mode) == ReconcilerDecision.Bucket.ZeroDelivery && zeroTbrNeedsRenewal(active, now)) {
                aapsLogger.info(LTag.APS, "RunningModeReconciler: zero-TBR missing or ending soon in ${active.mode}, sending it again")
                issueZeroTbrIfNeeded(active, cancelEb = true, now = now)
            }
            updateZeroTbrAlarm(active)
        }
    }

    private suspend fun zeroTbrNeedsRenewal(activeMode: RM, now: Long): Boolean {
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now) ?: return true
        if (!isEffectivelyZero(tbr)) return true
        val remaining = remainingMinutes(activeMode, now)
        val modeEnd = if (remaining == Int.MAX_VALUE) Long.MAX_VALUE else activeMode.timestamp + activeMode.duration
        return tbr.end < modeEnd && tbr.end - now < RENEW_BEFORE_END_MS
    }

    /**
     * Raises or clears [NotificationId.ZERO_DELIVERY_NOT_SET] for [activeMode]. The alarm is not raised
     * at the first miss: the next checks send the zero TBR again first, which fixes most short
     * connection problems.
     */
    private suspend fun updateZeroTbrAlarm(activeMode: RM) {
        val now = dateUtil.now()
        val pumpDescription = activePlugin.activePump.pumpDescription
        val zeroTbrExpected = ReconcilerDecision.bucketOf(activeMode.mode) == ReconcilerDecision.Bucket.ZeroDelivery &&
            remainingMinutes(activeMode, now) > 0 &&
            pumpDescription.isTempBasalCapable && pumpDescription.tempDurationStep > 0
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (!zeroTbrExpected || (tbr != null && isEffectivelyZero(tbr))) {
            zeroTbrMissingSince = null
            if (lastZeroTbrAlarm != null) {
                lastZeroTbrAlarm = null
                notificationManager.dismiss(NotificationId.ZERO_DELIVERY_NOT_SET)
            }
            return
        }
        val missingSince = zeroTbrMissingSince ?: now.also { zeroTbrMissingSince = it }
        val lastAlarm = lastZeroTbrAlarm
        if (now - missingSince >= ALARM_AFTER_MS && (lastAlarm == null || now - lastAlarm >= ALARM_REPEAT_MS)) {
            aapsLogger.error(LTag.APS, "RunningModeReconciler: no zero-TBR in ${activeMode.mode} for ${(now - missingSince) / 60_000L} min, raising alarm")
            lastZeroTbrAlarm = now
            notificationManager.post(NotificationId.ZERO_DELIVERY_NOT_SET, ApsStrings.zero_basal_not_set, sound = AlarmSound.ALARM)
        }
    }

    private suspend fun executeAction(action: ReconcilerDecision.Action, activeMode: RM, now: Long) {
        when (action) {
            is ReconcilerDecision.Action.NoOp         -> Unit
            is ReconcilerDecision.Action.CancelTbr    -> cancelTbrIfActive(now)
            is ReconcilerDecision.Action.IssueZeroTbr -> issueZeroTbrIfNeeded(activeMode, action.cancelExtendedBolus, now)
        }
    }

    private suspend fun cancelTbrIfActive(now: Long) {
        val currentTbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (currentTbr == null) {
            aapsLogger.debug(LTag.APS, "RunningModeReconciler: cancelTbr — no active TBR, skipping")
            return
        }
        aapsLogger.info(
            LTag.APS,
            "RunningModeReconciler: canceling active TBR (rate=${currentTbr.rate}, type=${currentTbr.type})"
        )
        val result = commandQueue.cancelTempBasal(enforceNew = true)
        if (!result.success) {
            aapsLogger.warn(LTag.APS, "RunningModeReconciler: cancelTbr failed: ${result.comment}")
            rxBus.send(EventShowSnackbar(rh.gs(CoreUiStrings.temp_basal_delivery_error), EventShowSnackbar.Type.Error))
        }
    }

    private suspend fun issueZeroTbrIfNeeded(activeMode: RM, cancelEb: Boolean, now: Long) {
        val pump = activePlugin.activePump
        if (!pump.isInitialized()) {
            aapsLogger.warn(LTag.APS, "RunningModeReconciler: pump not initialized, skipping zero-TBR issue")
            return
        }
        // Worse here than anywhere else, which is why it is checked before the first command rather
        // than before each one. This method cancels an extended bolus and THEN issues a zero temp
        // basal. A hold granted between those two leaves the extended bolus cancelled, the zero TBR
        // waiting in the queue, and FULL BASAL running - while the app believes the pump is suspended.
        // Skipping is safe: the reconciler runs again and re-derives the whole state from the mode.
        if (commandQueue.isHeld()) {
            aapsLogger.warn(LTag.APS, "RunningModeReconciler: queue is held (settings being applied), skipping zero-TBR issue")
            return
        }
        // Worse here than anywhere else, which is why it is checked before the first command rather
        // than before each one. This method cancels an extended bolus and THEN issues a zero temp
        // basal. A hold granted between those two leaves the extended bolus cancelled, the zero TBR
        // waiting in the queue, and FULL BASAL running - while the app believes the pump is suspended.
        // Skipping is safe: the reconciler runs again and re-derives the whole state from the mode.
        val remainingMinutes = remainingMinutes(activeMode, now)
        if (remainingMinutes <= 0) {
            aapsLogger.debug(LTag.APS, "RunningModeReconciler: RM has no remaining time, skipping zero-TBR")
            return
        }
        if (cancelEb) {
            val eb = persistenceLayer.getExtendedBolusActiveAt(now)
            if (eb != null) {
                aapsLogger.info(LTag.APS, "RunningModeReconciler: canceling active extended bolus")
                val ebResult = commandQueue.cancelExtended()
                if (!ebResult.success)
                    aapsLogger.warn(LTag.APS, "RunningModeReconciler: cancelExtended failed: ${ebResult.comment}")
            }
        }
        val currentTbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (currentTbr != null && isEffectivelyZero(currentTbr) &&
            currentTbr.end >= now + T.mins(remainingMinutes.toLong()).msecs()
        ) {
            aapsLogger.debug(LTag.APS, "RunningModeReconciler: pump already zero-TBR for sufficient duration, skipping")
            return
        }
        val rounded = DurationRounding.roundUpToPumpStep(
            remainingMinutes = remainingMinutes,
            pumpStepMinutes = pump.pumpDescription.tempDurationStep,
            pumpMaxDurationMinutes = pump.pumpDescription.tempMaxDuration
        )
        when (rounded) {
            is DurationRounding.Result.Skip  -> {
                aapsLogger.warn(
                    LTag.APS,
                    "RunningModeReconciler: duration rounding skipped issue (pump step=${pump.pumpDescription.tempDurationStep}, remaining=$remainingMinutes)"
                )
            }

            is DurationRounding.Result.Issue -> {
                val profile = profileFunction.getProfile()
                if (profile == null) {
                    aapsLogger.warn(LTag.APS, "RunningModeReconciler: no profile, cannot issue zero-TBR")
                    return
                }
                val durationMinutes = rounded.minutes
                aapsLogger.info(
                    LTag.APS,
                    "RunningModeReconciler: issuing zero-TBR for ${durationMinutes}m (mode=${activeMode.mode}, remaining=${remainingMinutes}m)"
                )
                val result = if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
                    commandQueue.tempBasalAbsolute(
                        absoluteRate = 0.0,
                        durationInMinutes = durationMinutes,
                        enforceNew = true,
                        profile = profile,
                        tbrType = PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
                    )
                } else {
                    commandQueue.tempBasalPercent(
                        percent = 0,
                        durationInMinutes = durationMinutes,
                        enforceNew = true,
                        profile = profile,
                        tbrType = PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
                    )
                }
                if (!result.success) {
                    aapsLogger.warn(LTag.APS, "RunningModeReconciler: zero-TBR issue failed: ${result.comment}")
                    rxBus.send(EventShowSnackbar(rh.gs(CoreUiStrings.temp_basal_delivery_error), EventShowSnackbar.Type.Error))
                }
            }
        }
    }

    private fun isEffectivelyZero(tbr: TB): Boolean = tbr.rate == 0.0

    private fun remainingMinutes(activeMode: RM, now: Long): Int {
        if (activeMode.duration <= 0L) return Int.MAX_VALUE
        val endMs = activeMode.timestamp + activeMode.duration
        if (endMs < 0L) return Int.MAX_VALUE
        val remainingMs = endMs - now
        return (remainingMs / 60_000L).toInt().coerceAtLeast(0)
    }

    companion object {

        // The checks come about every 5 minutes (KeepAlive), and can be late. Renewing a zero TBR
        // that ends within 15 minutes means the pump never gets back to basal between two checks.
        private val RENEW_BEFORE_END_MS = T.mins(15).msecs()

        // Two more checks try to send the zero TBR again before the alarm rings.
        private val ALARM_AFTER_MS = T.mins(10).msecs()
        private val ALARM_REPEAT_MS = T.mins(15).msecs()
    }
}
