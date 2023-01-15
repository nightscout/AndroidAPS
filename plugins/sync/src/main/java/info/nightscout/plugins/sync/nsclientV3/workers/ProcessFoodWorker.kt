package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.foodFromJson
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.Food
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.sync.nsclientV3.extensions.toFood
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.localmodel.food.NSFood
import info.nightscout.shared.sharedPreferences.SP
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ProcessFoodWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var sp: SP
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var rxBus: RxBus

    override suspend fun doWorkAndLog(): Result {
        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
            ?: return Result.failure(workDataOf("Error" to "missing input data"))
        aapsLogger.debug(LTag.DATABASE, "Received Food Data: $data")

        try {
            val foods = mutableListOf<Food>()
            if (data is JSONArray) {
                for (index in 0 until data.length()) {
                    val jsonFood: JSONObject = data.getJSONObject(index)

                    if (JsonHelper.safeGetString(jsonFood, "type") != "food") continue

                    when (JsonHelper.safeGetString(jsonFood, "action")) {
                        "remove" -> {
                            val delFood = Food(
                            name = "",
                            portion = 0.0,
                            carbs = 0,
                            isValid = false
                        ).also { it.interfaceIDs.nightscoutId = JsonHelper.safeGetString(jsonFood, "_id") }
                        foods += delFood
                    }

                    else     -> {
                        val food = foodFromJson(jsonFood)
                        if (food != null) foods += food
                        else aapsLogger.error(LTag.DATABASE, "Error parsing food", jsonFood.toString())
                    }
                    }
                }
            } else if (data is List<*>) {
                for (i in 0 until data.size)
                    foods += (data[i] as NSFood).toFood()
            }
            storeDataForDb.foods.addAll(foods)
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("ERROR", error.localizedMessage))
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }

        return Result.success()
    }
}
