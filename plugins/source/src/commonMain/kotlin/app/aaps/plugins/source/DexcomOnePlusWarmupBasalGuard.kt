package app.aaps.plugins.source

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.objects.extensions.convertedToAbsolute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dev.zacsweers.metro.Inject
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlin.time.Clock

/**
 * Cancels a high temp basal while the Dexcom ONE+ / G7 gives no glucose.
 *
 * When: the plugin calls [ensureProfileBasalDuringWarmup] each time its warm-up / connection state
 * changes while that state is active: sensor warm-up, and also CONNECTING, RECONNECTING and PAIRING,
 * which is every time the Bluetooth link to the sensor is lost and comes back.
 *
 * Why: with no glucose newer than 7 minutes the APS does not run (the oref glucose status is null
 * for older data), so the loop sets nothing new and a temp basal that is already running keeps
 * going until its own end. During a 30 min warm-up, or a long signal loss, a high temp could run
 * for its whole duration with no glucose to correct it.
 *
 * What: cancel the running temp basal, so the pump goes back to the profile basal. Only when ALL of
 * these hold ([shouldCancelResidualTemp]):
 *  - this plugin is the active BG source (never act for another source);
 *  - the loop is enabled and the running mode is CLOSED_LOOP or CLOSED_LOOP_LGS
 *    (`isClosedLoopOrLgs()`). Open loop, disabled loop and every paused mode (suspended,
 *    disconnected, super bolus) are left alone, so a protective zero temp is never lifted;
 *  - the last glucose value is older than [GLUCOSE_FRESHNESS_MIN] minutes. With fresher glucose the
 *    loop may have set the temp on purpose, and this guard must not fight it;
 *  - a temp basal is running and its absolute rate is ABOVE the profile basal by more than
 *    [RATE_EPSILON_U_PER_H]. A low or zero temp is kept, so this can only lower delivery, down to
 *    the profile basal, never raise it.
 *
 * This is ANY temp basal above the profile basal, also one the user set by hand. With no glucose
 * there is nothing that tells a manual high temp from a loop temp that is now running blind, so
 * both are ended. A manual temp at or below the profile basal is kept, like every low temp.
 *
 * How: `CommandQueue.cancelTempBasal(enforceNew = false, autoForced = false)`, the same call the
 * loop makes in `LoopPlugin.applyAPSRequest`. A cancel the pump refuses stays a failure, so AAPS
 * keeps counting the temp. The conditions are checked again inside the launched job, just before
 * the command, so a fresh reading and a new loop temp that arrive in between are never cancelled.
 * Each cancel is written to the user entry log (`CANCEL_TEMP_BASAL`, source `DexcomOnePlus`), so the
 * user can see that this BG source ended the temp and why.
 *
 * Cancelling goes back to the profile schedule, so no limit is bypassed, and it does nothing when
 * repeated, so calling it on every state change is safe. Software behaviour only, not medical
 * advice.
 */
@Inject
class DexcomOnePlusWarmupBasalGuard(
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue,
    private val uel: UserEntryLogger,
    private val loopProvider: () -> Loop,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
) {

    /**
     * Evaluate the guards and, if warranted, revert the pump to profile basal. The pump command is
     * launched fire-and-forget on [scope] (per the CommandQueue deadlock guidance — never await a
     * queue command from a queue/BLE execution context). The guards are checked again in the
     * launched job, with a new [now], right before the command: a reading and a new loop temp may
     * have arrived since the first check.
     */
    suspend fun ensureProfileBasalDuringWarmup(
        owner: BgSource,
        scope: CoroutineScope,
        now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ) {
        if (!shouldCancelResidualTemp(owner, now())) return
        scope.launch {
            if (!shouldCancelResidualTemp(owner, now())) {
                aapsLogger.info(LTag.APS, "ONEPLUS_WARMUP_BASAL: state changed before the cancel, nothing sent")
                return@launch
            }
            uel.log(Action.CANCEL_TEMP_BASAL, Sources.DexcomOnePlus)
            val result = commandQueue.cancelTempBasal(enforceNew = false, autoForced = false)
            if (!result.success) {
                aapsLogger.error(LTag.APS, "ONEPLUS_WARMUP_BASAL: cancel failed, the temp basal stays counted: ${result.comment}")
            }
        }
    }

    /**
     * Pure decision (all guards) — extracted for unit testing. Returns true only when a residual
     * temp **above** profile basal should be cancelled under safe conditions.
     */
    internal suspend fun shouldCancelResidualTemp(owner: BgSource, nowMs: Long): Boolean {
        if (activePlugin.activeBgSource !== owner) return false

        val loop = loopProvider()
        if (!loop.isEnabled()) return false

        val mode = loop.runningMode()
        if (!mode.isClosedLoopOrLgs()) {
            aapsLogger.debug(LTag.APS, "ONEPLUS_WARMUP_BASAL: skip — mode=$mode (not closed-loop/LGS)")
            return false
        }

        // Recent-glucose guard: if the loop still has fresh glucose it may have set this temp
        // legitimately — do not fight it. Act only once glucose is as stale as the APS considers it.
        val lastGv = persistenceLayer.getLastGlucoseValue()
        if (lastGv != null && nowMs - lastGv.timestamp < GLUCOSE_FRESHNESS_MIN * 60_000L) {
            aapsLogger.debug(LTag.APS, "ONEPLUS_WARMUP_BASAL: skip — recent glucose present, loop owns dosing")
            return false
        }

        val tb = persistenceLayer.getTemporaryBasalActiveAt(nowMs)
        if (tb == null) {
            aapsLogger.debug(LTag.APS, "ONEPLUS_WARMUP_BASAL: no active temp — pump already on profile")
            return false
        }

        val profile = profileFunction.getProfile()
        if (profile == null) {
            aapsLogger.debug(LTag.APS, "ONEPLUS_WARMUP_BASAL: no profile — skip")
            return false
        }

        val profileBasal = profile.getBasal()
        val tempAbsolute = tb.convertedToAbsolute(nowMs, profile)

        // Never raise delivery while there is no glucose: only cancel a temp ABOVE profile.
        if (tempAbsolute <= profileBasal + RATE_EPSILON_U_PER_H) {
            aapsLogger.info(
                LTag.APS,
                "ONEPLUS_WARMUP_BASAL: keep temp ${rate(tempAbsolute)}U/h (<= profile ${rate(profileBasal)}U/h) — " +
                    "not raising delivery during warm-up",
            )
            return false
        }

        aapsLogger.info(
            LTag.APS,
            "ONEPLUS_WARMUP_BASAL: cancel high temp ${rate(tempAbsolute)}U/h (> profile ${rate(profileBasal)}U/h) → " +
                "revert to profile basal (warm-up, no glucose)",
        )
        return true
    }

    /**
     * A basal rate for the log, with three decimals.
     *
     * `String.format` is JVM only, and this class is shared code now. The rate is only ever read by
     * a person in a log line, so a plain round is enough and it needs no locale.
     */
    private fun rate(uPerHour: Double): String {
        val milliUnits = (uPerHour * 1000.0).roundToLong()
        val whole = milliUnits / 1000L
        val fraction = (milliUnits % 1000L).absoluteValue
        return "$whole.${fraction.toString().padStart(3, '0')}"
    }

    companion object {

        /** Margin so a temp equal to (or within noise of) profile basal is treated as "not above". */
        private const val RATE_EPSILON_U_PER_H = 0.005

        /**
         * Glucose older than this counts as "no fresh glucose". Same 7 min as the oref glucose status
         * (`GlucoseStatusCalculatorSMB`, `GlucoseStatusCalculatorAutoIsf`), so this guard acts exactly
         * when the APS itself stops running. Kept local because `:plugins:aps` is not a dependency of
         * this module; the two must stay aligned.
         */
        private const val GLUCOSE_FRESHNESS_MIN = 7L
    }
}
