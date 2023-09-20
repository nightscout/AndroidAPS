package info.nightscout.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.rx.events.Event
import info.nightscout.rx.events.EventNewBG
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class InvokeLoopWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var loop: Loop

    class InvokeLoopData(
        val cause: Event?
    )

    /*
     This method is triggered once autosens calculation has completed, so the LoopPlugin
     has current data to work with. However, autosens calculation can be triggered by multiple
     sources and currently only a new BG should trigger a loop run. Hence we return early if
     the event causing the calculation is not EventNewBG.
     <p>
    */
    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as InvokeLoopData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        if (data.cause !is EventNewBG) return Result.success(workDataOf("Result" to "no calculation needed"))
        val glucoseValue = iobCobCalculator.ads.actualBg() ?: return Result.success(workDataOf("Result" to "bg outdated"))
        if (glucoseValue.timestamp <= loop.lastBgTriggeredRun) return Result.success(workDataOf("Result" to "already looped with that value"))
        loop.lastBgTriggeredRun = glucoseValue.timestamp
        loop.invoke("Calculation for $glucoseValue", true)
        return Result.success()
    }
}