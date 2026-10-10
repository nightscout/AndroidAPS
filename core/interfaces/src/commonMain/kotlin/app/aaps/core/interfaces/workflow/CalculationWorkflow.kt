package app.aaps.core.interfaces.workflow

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.HISTORY_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import kotlin.time.Duration

interface CalculationWorkflow {
    companion object {

        const val MAIN_CALCULATION = "calculation"
        const val HISTORY_CALCULATION = "history_calculation"
        const val UPDATE_PREDICTIONS = "update_predictions"
    }

    enum class ProgressData(val pass: Int, val percentOfTotal: Int) {
        DRAW_BG(0, 1),
        IOB_COB_OREF(1, 52),
        PREPARE_IOB_AUTOSENS_DATA(2, 45),
        DRAW_IOB(3, 1),
        DRAW_FINAL(4, 1);

        fun finalPercent(progress: Int): Int {
            var total = 0
            for (i in entries) if (i.pass < pass) total += i.percentOfTotal
            total += (percentOfTotal.toDouble() * progress / 100.0).toInt()
            return total
        }
    }

    suspend fun stopCalculation(job: String, from: String)

    /**
     * Block the caller until the data-producing (autosens/IOB/COB) stage of [job] has finished,
     * bounded by an internal timeout so it can never hang the caller (e.g. the dosing path).
     *
     * Only the calculation stage is awaited, never the whole [job] chain: [MAIN_CALCULATION] also
     * contains the loop-invoking post stage, so a loop-triggered caller waiting on the full chain
     * would stall on the very worker running it. Returns immediately when nothing is calculating.
     *
     * @param job [MAIN_CALCULATION] or [HISTORY_CALCULATION]
     * @param reason for logging
     */
    suspend fun waitForCalculationFinish(job: String, reason: String)

    /**
     * Wait until no run of [job] is going, the post stage included, or until [timeout].
     *
     * For a history change that arrives while a calculation runs: stopping that run throws away all
     * of its work, and when a full recalculation takes longer than the time between two glucose values
     * it then never finishes. Waiting for it and calculating the change afterwards always ends.
     *
     * Do not call it from the post stage of [job] (the loop): it would wait for itself until [timeout].
     *
     * A run that was started a moment ago can still be missing here, because starting is asynchronous.
     *
     * @param job [MAIN_CALCULATION] or [HISTORY_CALCULATION]
     * @return true when nothing is running, false when [timeout] ran out first
     */
    suspend fun awaitCalculationIdle(job: String, timeout: Duration): Boolean

    /**
     * Start calculation of data needed for displaying graphs
     *
     * @param job [MAIN_CALCULATION] or [HISTORY_CALCULATION]
     * @param iobCobCalculator different instance for the history browser
     * @param overviewData different instance for the history browser
     * @param cache per-scope Compose data cache — workers write graph data
     *   into this instance (the owning scope reads from it).
     * @param signals per-scope signals emitter — workers emit progress and
     *   graph-update events into it so the owning scope (main/history) gets them
     *   without any job-name filtering.
     */
    suspend fun runCalculation(
        job: String,
        iobCobCalculator: IobCobCalculator,
        overviewData: OverviewData,
        cache: OverviewDataCache,
        signals: CalculationSignalsEmitter,
        reason: String,
        end: Long,
        bgDataReload: Boolean,
        triggeredByNewBG: Boolean
    )

    /**
     * Update predictions in graph ofter new data from device status
     */
    suspend fun runOnReceivedPredictions(overviewData: OverviewData)
}