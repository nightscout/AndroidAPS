package info.nightscout.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.core.events.EventIobCalculationProgress
import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventUpdateOverviewGraph
import javax.inject.Inject

class UpdateGraphWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    override fun doWork(): Result {
        if (inputData.getString(CalculationWorkflow.JOB) == CalculationWorkflow.MAIN_CALCULATION)
            activePlugin.activeOverview.overviewBus.send(EventUpdateOverviewGraph("UpdateGraphWorker"))
        else
            rxBus.send(EventUpdateOverviewGraph("UpdateGraphWorker"))
        rxBus.send(EventIobCalculationProgress(CalculationWorkflow.ProgressData.DRAW, 100, null))
        return Result.success()
    }
}