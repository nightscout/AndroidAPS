package info.nightscout.source

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CgmSourceTransaction
import info.nightscout.database.transactions.TransactionGlucoseValue
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.source.BgSource
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TomatoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_sensor)
        .preferencesId(R.xml.pref_bgsource)
        .pluginName(R.string.tomato)
        .shortName(R.string.tomato_short)
        .description(R.string.description_source_tomato),
    aapsLogger, rh, injector
), BgSource {

    // cannot be inner class because of needed injection
    class TomatoWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var tomatoPlugin: TomatoPlugin
        @Inject lateinit var sp: SP
        @Inject lateinit var repository: AppRepository

        @Suppress("SpellCheckingInspection")
        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!tomatoPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val glucoseValues = mutableListOf<TransactionGlucoseValue>()
            glucoseValues += TransactionGlucoseValue(
                timestamp = inputData.getLong("com.fanqies.tomatofn.Extras.Time", 0),
                value = inputData.getDouble("com.fanqies.tomatofn.Extras.BgEstimate", 0.0),
                raw = 0.0,
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.NONE,
                sourceSensor = GlucoseValue.SourceSensor.LIBRE_1_TOMATO
            )
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from Tomato App", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { savedValues ->
                    savedValues.inserted.forEach {
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }
            return ret
        }
    }
}