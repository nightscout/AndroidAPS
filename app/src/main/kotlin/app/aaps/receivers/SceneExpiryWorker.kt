package app.aaps.receivers

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.ui.compose.scenes.ActiveSceneManager
import app.aaps.ui.compose.scenes.SceneExecutor
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * WorkManager worker that fires when a scene's duration expires.
 * Reverts non-duration actions (SMB), marks scene as expired, and posts a notification.
 */
class SceneExpiryWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var activeSceneManager: ActiveSceneManager
    @Inject lateinit var sceneExecutor: SceneExecutor

    override suspend fun doWorkAndLog(): Result {
        val sceneName = inputData.getString(KEY_SCENE_NAME) ?: "Scene"
        val activeState = activeSceneManager.getActiveState()

        // Only act if the scene is still active
        if (activeState != null) {
            aapsLogger.info(LTag.UI, "Scene '$sceneName' duration expired — reverting non-duration actions")
            sceneExecutor.onExpiry()
            postNotification(sceneName)
        }

        return Result.success()
    }

    private fun postNotification(sceneName: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(app.aaps.core.ui.R.drawable.ic_notif_aaps)
            .setContentTitle(applicationContext.getString(app.aaps.core.ui.R.string.scene_ended))
            .setContentText(sceneName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {

        const val KEY_SCENE_NAME = "scene_name"
        private const val CHANNEL_ID = "scene_expiry"
        private const val NOTIFICATION_ID = 7654
    }
}
