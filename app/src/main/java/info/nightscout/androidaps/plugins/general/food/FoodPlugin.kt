package info.nightscout.androidaps.plugins.general.food

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Food
import info.nightscout.androidaps.database.transactions.SyncFoodTransaction
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.receivers.BundleStore
import info.nightscout.androidaps.receivers.DataReceiver
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.extensions.foodFromJson
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONArray
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

    private val disposable = CompositeDisposable()

    // cannot be inner class because of needed injection
    class FoodWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var sp: SP
        @Inject lateinit var bundleStore: BundleStore
        @Inject lateinit var foodPlugin: FoodPlugin

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            aapsLogger.debug(LTag.DATAFOOD, "Received Food Data: $inputData}")
            val bundle = bundleStore.pickup(inputData.getLong(DataReceiver.STORE_KEY, -1))
                ?: return Result.failure()

            var ret = Result.success()

            val foodsString = bundle.getString("foods") ?: return Result.failure()
            val foods = JSONArray(foodsString)
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

                        foodPlugin.disposable += repository.runTransactionForResult(SyncFoodTransaction(delFood)).subscribe({ result ->
                            result.invalidated.forEach { aapsLogger.debug(LTag.DATAFOOD, "Invalidated food ${it.interfaceIDs.nightscoutId}") }
                        }, {
                            aapsLogger.error(LTag.DATAFOOD, "Error while removing food", it)
                            ret = Result.failure()
                        })
                    }

                    else     -> {
                        val food = foodFromJson(jsonFood)
                        if (food != null) {
                            foodPlugin.disposable += repository.runTransactionForResult(SyncFoodTransaction(food)).subscribe({ result ->
                                result.inserted.forEach { aapsLogger.debug(LTag.DATAFOOD, "Inserted food $it") }
                                result.updated.forEach { aapsLogger.debug(LTag.DATAFOOD, "Updated food $it") }
                                result.invalidated.forEach { aapsLogger.debug(LTag.DATAFOOD, "Invalidated food $it") }
                            }, {
                                aapsLogger.error(LTag.DATAFOOD, "Error while adding/updating food", it)
                                ret = Result.failure()
                            })
                        } else {
                            aapsLogger.error(LTag.DATAFOOD, "Error parsing food", jsonFood.toString())
                            ret = Result.failure()
                        }
                    }
                }
            }
            return ret
        }
    }
}