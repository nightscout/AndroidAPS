package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.rx.bus.RxBus
import app.aaps.interfaces.rx.events.EventUpdateOverviewGraph
import info.nightscout.core.events.EventIobCalculationProgress
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.core.workflow.CalculationWorkflow
import kotlinx.coroutines.Dispatchers
import java.security.spec.InvalidParameterSpecException
import javax.inject.Inject

class UpdateGraphWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin

    override suspend fun doWorkAndLog(): Result {
        val pass = inputData.getInt(CalculationWorkflow.PASS, -1)
        if (inputData.getString(CalculationWorkflow.JOB) == CalculationWorkflow.MAIN_CALCULATION)
            activePlugin.activeOverview.overviewBus.send(EventUpdateOverviewGraph("UpdateGraphWorker"))
        else
            rxBus.send(EventUpdateOverviewGraph("UpdateGraphWorker"))
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.values().find { it.pass == pass } ?: throw InvalidParameterSpecException(), 100, null))
        return Result.success()
    }
}