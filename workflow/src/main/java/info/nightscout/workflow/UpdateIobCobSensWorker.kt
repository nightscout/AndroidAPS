package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.rx.bus.RxBus
import app.aaps.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.interfaces.rx.events.EventUpdateOverviewSensitivity
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