package app.aaps.implementation.scenes

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit

/**
 * WorkManager is what makes the [SceneExpiryScheduler] promise keepable on Android: the work is
 * persisted, so a scene still ends at the right time after the process is killed or the phone
 * reboots. That durability is the whole reason this is not a coroutine timer.
 *
 * `ExistingWorkPolicy.REPLACE` under one fixed unique name: only one scene is active at a time, so a
 * new scene must take over the pending expiry rather than queue behind it.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class WorkManagerSceneExpiryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aapsLogger: AAPSLogger
) : SceneExpiryScheduler {

    override fun schedule(sceneName: String, delayMs: Long) {
        try {
            val request = OneTimeWorkRequest.Builder(SceneExpiryWorker::class.java)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(SceneExpiryWorker.KEY_SCENE_NAME, sceneName)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_SCENE_EXPIRY, ExistingWorkPolicy.REPLACE, request)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to schedule scene expiry worker", e)
        }
    }

    override fun cancel() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_SCENE_EXPIRY)
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Failed to cancel scene expiry worker", e)
        }
    }

    companion object {

        private const val WORK_NAME_SCENE_EXPIRY = "SceneExpiry"
    }
}
