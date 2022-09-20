package info.nightscout.androidaps.plugins.sync.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.SyncNsTherapyEventTransaction
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.sync.nsclient.data.NSMbg
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.androidaps.utils.extensions.therapyEventFromNsMbg
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class NSClientMbgWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var config: Config

    override fun doWork(): Result {
        var ret = Result.success()

        val acceptNSData = sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT
        if (!acceptNSData) return Result.success(workDataOf("Result" to "Sync not enabled"))

        val mbgArray = dataWorker.pickupJSONArray(inputData.getLong(DataWorker.STORE_KEY, -1))
            ?: return Result.failure(workDataOf("Error" to "missing input data"))
        for (i in 0 until mbgArray.length()) {
            val nsMbg = NSMbg(mbgArray.getJSONObject(i))
            if (!nsMbg.isValid()) continue
            repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEventFromNsMbg(nsMbg)))
                .doOnError {
                    aapsLogger.error("Error while saving therapy event", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also {
                    aapsLogger.debug(LTag.DATABASE, "Saved therapy event $it")
                }
        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}