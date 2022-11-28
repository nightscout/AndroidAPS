package info.nightscout.plugins.general.food

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.foodFromJson
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.database.entities.Food
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.SyncNsFoodTransaction
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(FoodFragment::class.java.name)
    .pluginIcon(R.drawable.ic_food)
    .pluginName(R.string.food)
    .shortName(R.string.food_short)
    .description(R.string.description_food),
    aapsLogger, rh, injector
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
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            val foods = dataWorkerStorage.pickupJSONArray(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
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

                        repository.runTransactionForResult(SyncNsFoodTransaction(delFood))
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
                            repository.runTransactionForResult(SyncNsFoodTransaction(food))
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