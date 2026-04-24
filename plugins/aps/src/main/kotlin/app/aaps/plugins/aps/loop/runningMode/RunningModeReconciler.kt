package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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
 */
@Singleton
class RunningModeReconciler @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val profileFunction: ProfileFunction,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private var lastReconciledMode: RM.Mode? = null
    private var lastReconciledRowId: Long? = null
    private var lastReconciledDuration: Long = -1L
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        if (!config.APS) {
            aapsLogger.debug(LTag.APS, "RunningModeReconciler: config.APS=false, pump-side path disabled")
            return
        }
        appScope.launch {
            reconcileStartup()
            persistenceLayer.observeChanges(RM::class.java).collect { _ ->
                onAnyChange()
            }
        }
    }

    private suspend fun reconcileStartup() {
        val now = dateUtil.now()
        val active = persistenceLayer.getRunningModeActiveAt(now)
        // Treat startup as a transition from a synthetic working prev into the current mode.
        // Drift from real prior state (e.g. lingering zero-TBR when mode is now working) is
        // handled separately below.
        val action = ReconcilerDecision.decide(RM.Mode.CLOSED_LOOP, active.mode)
        executeAction(action, active, now)
        handleStartupDrift(active.mode, now)
        lastReconciledMode = active.mode
        lastReconciledRowId = active.id
        lastReconciledDuration = active.duration
        aapsLogger.debug(LTag.APS, "RunningModeReconciler: startup reconcile, mode=${active.mode}")
    }

    private suspend fun handleStartupDrift(activeMode: RM.Mode, now: Long) {
        // If we are in a zero-delivery mode, normal startup path already covers it.
        if (activeMode == RM.Mode.DISCONNECTED_PUMP || activeMode == RM.Mode.SUPER_BOLUS) return
        val currentTbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (currentTbr != null && currentTbr.type == TB.Type.EMULATED_PUMP_SUSPEND) {
            aapsLogger.info(
                LTag.APS,
                "RunningModeReconciler: startup drift — pump has EMULATED_PUMP_SUSPEND TBR but mode is $activeMode, canceling"
            )
            commandQueue.cancelTempBasal(enforceNew = true, callback = logCallback("startup-drift cancelTbr"))
        }
    }

    private suspend fun onAnyChange() {
        val now = dateUtil.now()
        val current = persistenceLayer.getRunningModeActiveAt(now)
        val prevMode = lastReconciledMode
        val prevId = lastReconciledRowId
        val prevDuration = lastReconciledDuration
        // A duration-only update on the same row (e.g. user extends a disconnect) must still
        // trigger reconciliation so the zero-TBR window is re-evaluated. Only skip when the
        // whole envelope is unchanged.
        if (prevMode == current.mode && prevId == current.id && prevDuration == current.duration) return
        val action = ReconcilerDecision.decide(prevMode ?: RM.Mode.CLOSED_LOOP, current.mode)
        executeAction(action, current, now)
        lastReconciledMode = current.mode
        lastReconciledRowId = current.id
        lastReconciledDuration = current.duration
    }

    private suspend fun executeAction(action: ReconcilerDecision.Action, activeMode: RM, now: Long) {
        when (action) {
            is ReconcilerDecision.Action.NoOp         -> Unit
            is ReconcilerDecision.Action.CancelTbr    -> cancelTbrIfActive(now)
            is ReconcilerDecision.Action.IssueZeroTbr -> issueZeroTbrIfNeeded(activeMode, action.cancelExtendedBolus, now)
        }
    }

    private fun cancelTbrIfActive(now: Long) {
        val currentTbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (currentTbr == null) {
            aapsLogger.debug(LTag.APS, "RunningModeReconciler: cancelTbr — no active TBR, skipping")
            return
        }
        aapsLogger.info(
            LTag.APS,
            "RunningModeReconciler: canceling active TBR (rate=${currentTbr.rate}, type=${currentTbr.type})"
        )
        commandQueue.cancelTempBasal(enforceNew = true, callback = logCallback("cancelTbr"))
    }

    private suspend fun issueZeroTbrIfNeeded(activeMode: RM, cancelEb: Boolean, now: Long) {
        val pump = activePlugin.activePump
        if (!pump.isInitialized()) {
            aapsLogger.warn(LTag.APS, "RunningModeReconciler: pump not initialized, skipping zero-TBR issue")
            return
        }
        val remainingMinutes = remainingMinutes(activeMode, now)
        if (remainingMinutes <= 0) {
            aapsLogger.debug(LTag.APS, "RunningModeReconciler: RM has no remaining time, skipping zero-TBR")
            return
        }
        // Cancel extended bolus if requested and actually active.
        if (cancelEb) {
            val eb = persistenceLayer.getExtendedBolusActiveAt(now)
            if (eb != null) {
                aapsLogger.info(LTag.APS, "RunningModeReconciler: canceling active extended bolus")
                commandQueue.cancelExtended(callback = logCallback("cancelExtended"))
            }
        }
        // Idempotency: skip if pump already zero-TBR for at least the remaining window.
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
                if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
                    commandQueue.tempBasalAbsolute(
                        absoluteRate = 0.0,
                        durationInMinutes = durationMinutes,
                        enforceNew = true,
                        profile = profile,
                        tbrType = PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND,
                        callback = logCallback("tempBasalAbsolute 0.0")
                    )
                } else {
                    commandQueue.tempBasalPercent(
                        percent = 0,
                        durationInMinutes = durationMinutes,
                        enforceNew = true,
                        profile = profile,
                        tbrType = PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND,
                        callback = logCallback("tempBasalPercent 0")
                    )
                }
            }
        }
    }

    private fun isEffectivelyZero(tbr: TB): Boolean = tbr.rate == 0.0

    private fun remainingMinutes(activeMode: RM, now: Long): Int {
        // Permanent / non-temporary modes (duration <= 0 or Long.MAX_VALUE) — treat as max.
        if (activeMode.duration <= 0L) return Int.MAX_VALUE
        val endMs = activeMode.timestamp + activeMode.duration
        if (endMs < 0L) return Int.MAX_VALUE // overflow guard
        val remainingMs = endMs - now
        return (remainingMs / 60_000L).toInt().coerceAtLeast(0)
    }

    private fun logCallback(label: String): Callback = object : Callback() {
        override fun run() {
            if (!result.success) {
                aapsLogger.warn(LTag.APS, "RunningModeReconciler: $label failed: ${result.comment}")
            }
        }
    }
}
