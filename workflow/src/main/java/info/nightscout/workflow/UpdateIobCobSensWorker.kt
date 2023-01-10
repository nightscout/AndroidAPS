package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventUpdateOverviewIobCob
import info.nightscout.rx.events.EventUpdateOverviewSensitivity
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