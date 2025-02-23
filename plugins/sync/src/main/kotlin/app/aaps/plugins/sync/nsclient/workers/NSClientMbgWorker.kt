package app.aaps.plugins.sync.nsclient.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsclient.data.NSMbg
import app.aaps.plugins.sync.nsclient.extensions.therapyEventFromNsMbg
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class NSClientMbgWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var storeDataForDb: StoreDataForDb

    override suspend fun doWorkAndLog(): Result {
        val ret = Result.success()

        val acceptNSData = preferences.get(BooleanKey.NsClientAcceptTherapyEvent) || config.AAPSCLIENT
        if (!acceptNSData) return Result.success(workDataOf("Result" to "Sync not enabled"))

        val mbgArray = dataWorkerStorage.pickupJSONArray(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
            ?: return Result.failure(workDataOf("Error" to "missing input data"))
        for (i in 0 until mbgArray.length()) {
            val nsMbg = NSMbg(mbgArray.getJSONObject(i))
            if (!nsMbg.isValid()) continue
            storeDataForDb.addToTherapyEvents(therapyEventFromNsMbg(nsMbg))
        }
        // storeDataForDb.storeTreatmentsToDb() don't do this. It will be stored along with other treatments
        return ret
    }
}