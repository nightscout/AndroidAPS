package app.aaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class LoadFoodsWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var nsClientRepository: NSClientRepository

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        // Food database doesn't provide last record modification
        // Read full collection every 5th attempt
        try {
            if (nsClientV3Plugin.lastLoadedSrvModified.collections.foods++ % 5 == 0L) {
                val foods: List<NSFood> = nsAndroidClient.getFoods(1000).values
                aapsLogger.debug(LTag.NSCLIENT, "FOODS: $foods")
                nsClientRepository.addLog("◄ RCV", "${foods.size} FOODs")
                // Schedule processing of fetched data
                nsIncomingDataProcessor.processFood(foods)
                storeDataForDb.storeFoodsToDb()
            } else {
                nsClientRepository.addLog("● RCV FOOD", "skipped")
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.localizedMessage)
            nsClientV3Plugin.lastOperationError = error.localizedMessage
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        nsClientV3Plugin.lastOperationError = null
        return Result.success()
    }
}