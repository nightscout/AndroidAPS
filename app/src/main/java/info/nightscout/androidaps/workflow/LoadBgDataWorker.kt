package info.nightscout.androidaps.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.logging.AAPSLogger
import javax.inject.Inject

class LoadBgDataWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var repository: AppRepository

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    class LoadBgData(
        val iobCobCalculator: IobCobCalculator,
        val end: Long
    )

    override fun doWork(): Result {

        val data = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as LoadBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        data.iobCobCalculator.ads.loadBgData(data.end, repository, aapsLogger, dateUtil, rxBus)
        data.iobCobCalculator.clearCache()
        return Result.success()
    }
}