package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import info.nightscout.core.utils.worker.LoggingWorker
import kotlinx.coroutines.Dispatchers

class DummyWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    override suspend fun doWorkAndLog(): Result = Result.success()
}