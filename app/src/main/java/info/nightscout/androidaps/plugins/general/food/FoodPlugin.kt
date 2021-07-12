package info.nightscout.androidaps.plugins.general.food

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Food
import info.nightscout.androidaps.database.transactions.SyncNsFoodTransaction
import info.nightscout.androidaps.extensions.foodFromJson
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(FoodFragment::class.java.name)
    .pluginIcon(R.drawable.ic_food)
    .pluginName(R.string.food)
    .shortName(R.string.food_short)
    .description(R.string.description_food),
    aapsLogger, resourceHelper, injector
) {

    // cannot be inner class because of needed injection
    class FoodWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var sp: SP
        @Inject lateinit var dataWorker: DataWorker

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            val foods = dataWorker.pickupJSONArray(inputData.getLong(DataWorker.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            aapsLogger.debug(LTag.DATABASE, "Received Food Data: $foods")

            var ret = Result.success()

            for (index in 0 until foods.length()) {
                val jsonFood: JSONObject = foods.getJSONObject(index)

                if (JsonHelper.safeGetString(jsonFood, "type") != "food") continue

                when (JsonHelper.safeGetString(jsonFood, "action")) {
                    "remove" -> {
                        val delFood = Food(
                            name = "",
                            portion = 0.0,
                            carbs = 0,
                            isValid = false
                        ).also { it.interfaceIDs.nightscoutId = JsonHelper.safeGetString(jsonFood, "_id") }

                        repository.runTransactionForResult(SyncNsFoodTransaction(delFood, true))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while removing food", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also {
                                it.invalidated.forEach { f -> aapsLogger.debug(LTag.DATABASE, "Invalidated food ${f.interfaceIDs.nightscoutId}") }
                            }
                    }

                    else     -> {
                        val food = foodFromJson(jsonFood)
                        if (food != null) {
                            repository.runTransactionForResult(SyncNsFoodTransaction(food, false))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while adding/updating food", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted food $it") }
                                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated food $it") }
                                    result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated food $it") }
                                }
                        } else {
                            aapsLogger.error(LTag.DATABASE, "Error parsing food", jsonFood.toString())
                            ret = Result.failure(workDataOf("Error" to "Error parsing food"))
                        }
                    }
                }
            }
            return ret
        }
    }
}