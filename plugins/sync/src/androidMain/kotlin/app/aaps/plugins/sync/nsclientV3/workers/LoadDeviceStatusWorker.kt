package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.RunnerWorker
import app.aaps.core.objects.workflow.WorkerInstanceFactory
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers

/** WorkManager shim. The work itself is [LoadDeviceStatusRunner], which is shared. */
class LoadDeviceStatusWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val runner: LoadDeviceStatusRunner
) : RunnerWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    override suspend fun runBody(isStopped: () -> Boolean) = runner.run()

    /** Metro builds the worker through this - WorkManager supplies context and params. */
    @AssistedFactory
    abstract class Factory : WorkerInstanceFactory<LoadDeviceStatusWorker>()
}
