package app.aaps.plugins.sync.xdrip.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.WorkerInstanceFactory
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers

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

    /** Metro builds the worker through this - WorkManager supplies context and params. */
    @AssistedFactory
    abstract class Factory : WorkerInstanceFactory<XdripDataSyncWorker>()
}
