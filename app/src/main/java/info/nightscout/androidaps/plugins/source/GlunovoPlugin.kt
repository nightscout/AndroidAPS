package info.nightscout.androidaps.plugins.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSource
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlunovoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
                   .mainType(PluginType.BGSOURCE)
                   .fragmentClass(BGSourceFragment::class.java.name)
                   .pluginIcon(R.drawable.ic_glunovo)
                   .pluginName(R.string.glunovo)
                   .preferencesId(R.xml.pref_bgsource)
                   .shortName(R.string.glunovo)
                   .description(R.string.description_source_glunovo),
               aapsLogger, resourceHelper, injector
), BgSource {

    // cannot be inner class because of needed injection
    public class GlunovoWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var glunovoPlugin: GlunovoPlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var sp: SP
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var broadcastToXDrip: XDripBroadcast

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        @Suppress("SpellCheckingInspection")
        override fun doWork(): Result {
            var ret = Result.success()

            Log.d("GlunovoPlugin","GlunovoPlugin")
            if (!glunovoPlugin.isEnabled(PluginType.BGSOURCE)) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = inputData.getLong("com.glunovoservice.BgEstimate", 0),
                value = inputData.getDouble("com.glunovoservice.BgEstimate", 0.0),
                raw = 0.0,
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.NONE,
                sourceSensor = GlucoseValue.SourceSensor.GLUNOVO_NATIVE
            )
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from Glunovo App", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { savedValues ->
                    savedValues.inserted.forEach {
                        broadcastToXDrip(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }
            return ret
        }
    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.GLUNOVO_NATIVE && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

    fun startGSer():Boolean = true
}
