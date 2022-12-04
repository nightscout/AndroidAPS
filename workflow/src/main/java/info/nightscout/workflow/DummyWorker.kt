package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import info.nightscout.core.utils.worker.LoggingWorker

class DummyWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params) {

    override fun doWorkAndLog(): Result = Result.success()
}