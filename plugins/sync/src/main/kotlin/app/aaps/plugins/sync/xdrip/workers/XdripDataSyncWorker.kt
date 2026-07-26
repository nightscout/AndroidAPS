package app.aaps.plugins.sync.xdrip.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers

@HiltWorker
class XdripDataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val dataSyncSelector: DataSyncSelectorXdrip,
    private val activePlugin: ActivePlugin,
    private val xdripMvvmRepository: XdripMvvmRepository
) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        xdripMvvmRepository.addLog("UPL", "Start")
        dataSyncSelector.doUpload()
        xdripMvvmRepository.addLog("UPL", "End")
        xdripMvvmRepository.updateQueueSize(dataSyncSelector.queueSize())
        return Result.success()
    }
}
