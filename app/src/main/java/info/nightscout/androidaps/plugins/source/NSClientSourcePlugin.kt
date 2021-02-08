package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv
import info.nightscout.androidaps.receivers.BundleStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientSourcePlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_nsclient_bg)
    .pluginName(R.string.nsclientbg)
    .description(R.string.description_source_ns_client),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private var lastBGTimeStamp: Long = 0
    private var isAdvancedFilteringEnabled = false

    init {
        if (config.NSCLIENT) {
            pluginDescription
                .alwaysEnabled(true)
                .setDefault()
        }
    }

    private val disposable = CompositeDisposable()

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun advancedFilteringSupported(): Boolean {
        return isAdvancedFilteringEnabled
    }

    private fun detectSource(glucoseValue: GlucoseValue) {
        if (glucoseValue.timestamp > lastBGTimeStamp) {
            isAdvancedFilteringEnabled = arrayOf(
                GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN,
                GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE,
                GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE,
                GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
                GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP
            ).any { it == glucoseValue.sourceSensor }
            lastBGTimeStamp = glucoseValue.timestamp
        }
    }

    // cannot be inner class because of needed injection
    class NSClientSourceWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var nsClientSourcePlugin: NSClientSourcePlugin
        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var sp: SP
        @Inject lateinit var nsUpload: NSUpload
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var bundleStore: BundleStore
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var broadcastToXDrip: XDripBroadcast
        @Inject lateinit var dexcomPlugin: DexcomPlugin

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        private fun toGv(jsonObject: JSONObject): CgmSourceTransaction.TransactionGlucoseValue {
            val sgv = NSSgv(jsonObject)
            return CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = sgv.mills,
                value = sgv.mgdl.toDouble(),
                noise = null,
                raw = if (sgv.filtered != null) sgv.filtered.toDouble() else sgv.mgdl.toDouble(),
                trendArrow = GlucoseValue.TrendArrow.fromString(sgv.direction),
                nightscoutId = sgv.id,
                sourceSensor = GlucoseValue.SourceSensor.fromString(sgv.device)
            )
        }

        override fun doWork(): Result {
            if (!nsClientSourcePlugin.isEnabled() && !sp.getBoolean(R.string.key_ns_autobackfill, true) && !dexcomPlugin.isEnabled()) return Result.failure()
            try {
                val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
                inputData.getString("sgv")?.let { sgvString ->
                    aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvString")
                    glucoseValues += toGv(JSONObject(sgvString))
                }
                inputData.getString("sgvs")?.let { sgvString ->
                    aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvString")
                    val jsonArray = JSONArray(sgvString)
                    for (i in 0 until jsonArray.length())
                        glucoseValues += toGv(jsonArray.getJSONObject(i))
                }
                nsClientSourcePlugin.disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null, !nsClientSourcePlugin.isEnabled())).subscribe({ result ->
                    result.updated.forEach {
                        //aapsLogger.debug("XXXXX: Updated $it")
                        broadcastToXDrip(it)
                        nsClientSourcePlugin.detectSource(it)
                    }
                    result.inserted.forEach {
                        //aapsLogger.debug("XXXXX: Inserted $it")
                        broadcastToXDrip(it)
                        nsClientSourcePlugin.detectSource(it)
                    }
                }, {
                    aapsLogger.error(LTag.BGSOURCE, "Error while saving values from NSClient App", it)
                })
            } catch (e: Exception) {
                aapsLogger.error("Unhandled exception", e)
                return Result.failure()
            }
            // Objectives 0
            sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
            return Result.success()
        }
    }
}