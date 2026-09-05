package app.aaps.implementation.scenes

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

/** WorkManager shim. The work itself is [SceneExpiryRunner], which is shared. */
class SceneExpiryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val runner: SceneExpiryRunner
) : RunnerWorker(context, params, Dispatchers.Default, aapsLogger, fabricPrivacy) {

    override suspend fun runBody(isStopped: () -> Boolean) =
        runner.run(inputData.getString(KEY_SCENE_NAME) ?: "Scene")

    /** Metro builds the worker through this - WorkManager supplies context and params. */
    @AssistedFactory
    abstract class Factory : WorkerInstanceFactory<SceneExpiryWorker>()

    companion object {

        const val KEY_SCENE_NAME = "scene_name"
    }
}
