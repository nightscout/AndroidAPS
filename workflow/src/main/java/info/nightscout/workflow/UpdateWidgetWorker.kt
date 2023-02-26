package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.ui.UiInteraction
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class UpdateWidgetWorker(
    private val context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var uiInteraction: UiInteraction

    override suspend fun doWorkAndLog(): Result {
        uiInteraction.updateWidget(context, "WorkFlow")
        return Result.success()
    }
}