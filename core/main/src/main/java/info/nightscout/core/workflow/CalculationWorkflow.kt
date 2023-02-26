package info.nightscout.core.workflow

import info.nightscout.core.graph.OverviewData
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.rx.events.Event

interface CalculationWorkflow {
    companion object {

        const val MAIN_CALCULATION = "calculation"
        const val HISTORY_CALCULATION = "history_calculation"
        const val JOB = "job"
        const val PASS = "pass"
    }

    enum class ProgressData(val pass: Int, val percentOfTotal: Int) {
        DRAW_BG(0, 1),
        PREPARE_TREATMENTS_DATA(1, 2),
        PREPARE_BASAL_DATA(2, 6),
        PREPARE_TEMPORARY_TARGET_DATA(3, 6),
        DRAW_TT(4, 1),
        IOB_COB_OREF(5, 77),
        PREPARE_IOB_AUTOSENS_DATA(6, 5),
        DRAW_IOB(7, 1),
        DRAW_FINAL(8, 1);

        fun finalPercent(progress: Int): Int {
            var total = 0
            for (i in values()) if (i.pass < pass) total += i.percentOfTotal
            total += (percentOfTotal.toDouble() * progress / 100.0).toInt()
            return total
        }
    }

    fun stopCalculation(job: String, from: String)
    fun runCalculation(
        job: String,
        iobCobCalculator: IobCobCalculator,
        overviewData: OverviewData,
        reason: String,
        end: Long,
        bgDataReload: Boolean,
        cause: Event?
    )
}