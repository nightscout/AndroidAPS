package info.nightscout.androidaps.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.receivers.DataWorker
import javax.inject.Inject

class InvokeLoopWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var loop: Loop

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    class InvokeLoopData(
        val cause: Event?
    )

    /*
     This method is triggered once autosens calculation has completed, so the LoopPlugin
     has current data to work with. However, autosens calculation can be triggered by multiple
     sources and currently only a new BG should trigger a loop run. Hence we return early if
     the event causing the calculation is not EventNewBg.
     <p>
    */
    override fun doWork(): Result {

        val data = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as InvokeLoopData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        if (data.cause !is EventNewBG) return Result.success(workDataOf("Result" to "no calculation needed"))
        val glucoseValue = iobCobCalculator.ads.actualBg() ?: return Result.success(workDataOf("Result" to "bg outdated"))
        if (glucoseValue.timestamp <= loop.lastBgTriggeredRun) return Result.success(workDataOf("Result" to "already looped with that value"))
        loop.lastBgTriggeredRun = glucoseValue.timestamp
        loop.invoke("Calculation for $glucoseValue", true)
        return Result.success()
    }
}