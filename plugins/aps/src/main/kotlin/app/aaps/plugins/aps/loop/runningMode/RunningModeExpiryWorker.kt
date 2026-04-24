package app.aaps.plugins.aps.loop.runningMode

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.workflow.LoggingWorker
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Fires when a temporary running mode reaches its declared end.
 *
 * Purpose: time-based safety net. The reconciler's flow-observer handles explicit RM changes
 * (cancel, overwrite, new mode), but natural expiry of a temporary RM produces no DB write —
 * the succeeding permanent mode simply becomes active at `timestamp + duration`. Without this
 * worker, a zero-TBR set for the mode window would outlive the mode (rounded up to pump step)
 * while AAPS considers the mode over.
 *
 * The worker is scheduled / rescheduled by [RunningModeExpiryScheduler] on every RM change.
 * On fire, if the pump has an EMULATED_PUMP_SUSPEND TBR, it is canceled; otherwise no-op.
 *
 * Idempotent and safe to fire even if the RM was already canceled by the user — the pump state
 * check ensures we only cancel if there is actually a zero-TBR to clean up.
 */
class RunningModeExpiryWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var processedTbrEbData: ProcessedTbrEbData
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config

    override suspend fun doWorkAndLog(): Result {
        if (!config.APS) {
            aapsLogger.debug(LTag.APS, "RunningModeExpiryWorker: config.APS=false, skipping")
            return Result.success()
        }
        val now = dateUtil.now()
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        if (tbr != null && tbr.type == TB.Type.EMULATED_PUMP_SUSPEND) {
            aapsLogger.info(
                LTag.APS,
                "RunningModeExpiryWorker: canceling EMULATED_PUMP_SUSPEND TBR at RM expiry (rate=${tbr.rate}, end=${tbr.end})"
            )
            commandQueue.cancelTempBasal(enforceNew = true, callback = null)
        } else {
            aapsLogger.debug(LTag.APS, "RunningModeExpiryWorker: no EMULATED_PUMP_SUSPEND TBR active, no-op")
        }
        return Result.success()
    }

    companion object {

        const val WORK_NAME = "RunningModeExpiry"
    }
}
