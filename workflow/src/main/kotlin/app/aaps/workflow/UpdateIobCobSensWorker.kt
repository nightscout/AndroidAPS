package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewSensitivity
import app.aaps.core.main.utils.worker.LoggingWorker
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class UpdateIobCobSensWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin

    override suspend fun doWorkAndLog(): Result {
        activePlugin.activeOverview.overviewBus.send(EventUpdateOverviewIobCob("UpdateIobCobSensWorker"))
        activePlugin.activeOverview.overviewBus.send(EventUpdateOverviewSensitivity("UpdateIobCobSensWorker"))
        return Result.success()
    }
}