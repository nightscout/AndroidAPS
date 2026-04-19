package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.objects.workflow.LoggingWorker
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class UpdateWidgetWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var widgetUpdater: WidgetUpdater

    override suspend fun doWorkAndLog(): Result {
        widgetUpdater.update("WorkFlow")
        return Result.success()
    }
}
