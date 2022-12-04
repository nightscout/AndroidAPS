package info.nightscout.core.workflow

import info.nightscout.core.graph.OverviewData
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.rx.events.Event

interface CalculationWorkflow {
    companion object {

        const val MAIN_CALCULATION = "calculation"
        const val HISTORY_CALCULATION = "history_calculation"
        const val JOB = "job"
    }

    enum class ProgressData(private val pass: Int, val percentOfTotal: Int) {
        PREPARE_BASAL_DATA(0, 5),
        PREPARE_TEMPORARY_TARGET_DATA(1, 5),
        PREPARE_TREATMENTS_DATA(2, 5),
        IOB_COB_OREF(3, 74),
        PREPARE_IOB_AUTOSENS_DATA(4, 10),
        DRAW(5, 1);

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