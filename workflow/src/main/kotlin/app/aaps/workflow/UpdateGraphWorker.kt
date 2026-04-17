package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class UpdateGraphWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage

    class UpdateGraphData(
        val signals: CalculationSignalsEmitter,
        val pass: CalculationWorkflow.ProgressData
    )

    override suspend fun doWorkAndLog(): Result {
        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as? UpdateGraphData
            ?: return Result.failure(workDataOf("Error" to "missing input data"))
        data.signals.emitGraphUpdate("UpdateGraphWorker")
        data.signals.emitProgress(data.pass, 100)
        return Result.success()
    }
}
