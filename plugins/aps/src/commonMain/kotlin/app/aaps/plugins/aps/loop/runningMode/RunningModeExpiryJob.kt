package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil

/**
 * What happens when a temporary running mode reaches its declared end.
 *
 * Purpose: time-based safety net. The reconciler's flow-observer handles explicit RM changes
 * (cancel, overwrite, new mode), but natural expiry of a temporary RM produces no DB write -
 * the succeeding permanent mode simply becomes active at `timestamp + duration`. Without this,
 * a zero-TBR set for the mode window would outlive the mode (rounded up to pump step) while AAPS
 * considers the mode over.
 *
 * Idempotent and safe to run even if the RM was already canceled by the user - the pump state check
 * means we only cancel if there is actually a zero-TBR to clean up.
 *
 * **This holds the decision; the platform holds only the trigger.** On Android the trigger is
 * `RunningModeExpiryWorker`, a `@HiltWorker` in :app, scheduled by `RunningModeExpiryScheduler`.
 * Those cannot live in a multiplatform module because Hilt answers `@HiltWorker`/`@AssistedInject`
 * with generated Java - see `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken". Keeping the
 * decision here means another platform supplies a new trigger, not new behaviour.
 *
 * Note for whoever writes that trigger: WorkManager guarantees this survives process death and fires
 * under its constraints. `BGTaskScheduler` on iOS is best effort and may decline to run at all, so
 * the safety net is weaker there - that gap is real and is not closed by sharing this class.
 */
class RunningModeExpiryJob(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val commandQueue: CommandQueue
) {

    suspend fun run() {
        if (!config.APS) {
            aapsLogger.debug(LTag.APS, "RunningModeExpiry: config.APS=false, skipping")
            return
        }
        val now = dateUtil.now()
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (tbr != null && tbr.type == TB.Type.EMULATED_PUMP_SUSPEND) {
            aapsLogger.info(
                LTag.APS,
                "RunningModeExpiry: canceling EMULATED_PUMP_SUSPEND TBR at RM expiry (rate=${tbr.rate}, end=${tbr.end})"
            )
            commandQueue.cancelTempBasal(enforceNew = true)
        } else {
            aapsLogger.debug(LTag.APS, "RunningModeExpiry: no EMULATED_PUMP_SUSPEND TBR active, no-op")
        }
    }
}
