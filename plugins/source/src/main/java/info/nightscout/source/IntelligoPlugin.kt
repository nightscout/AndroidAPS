package info.nightscout.source

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CgmSourceTransaction
import info.nightscout.database.transactions.TransactionGlucoseValue
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.source.BgSource
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.safeGetInstalledPackages
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntelligoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val context: Context,
    private val repository: AppRepository,
    private val xDripBroadcast: XDripBroadcast,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger,
    private val fabricPrivacy: FabricPrivacy
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_intelligo)
        .pluginName(R.string.intelligo)
        .preferencesId(R.xml.pref_bgsource)
        .shortName(R.string.intelligo)
        .description(R.string.description_source_intelligo),
    aapsLogger, resourceHelper, injector
), BgSource {

    private val handler = Handler(HandlerThread(this::class.java.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    private val contentUri: Uri = Uri.parse("content://$AUTHORITY/$TABLE_NAME")

    init {
        refreshLoop = Runnable {
            try {
                handleNewData()
            } catch (e: Exception) {
                fabricPrivacy.logException(e)
                aapsLogger.error("Error while processing data", e)
            }
            val lastReadTimestamp = sp.getLong(R.string.key_last_processed_intelligo_timestamp, 0L)
            val differenceToNow = INTERVAL - (dateUtil.now() - lastReadTimestamp) % INTERVAL + T.secs(10).msecs()
            handler.postDelayed(refreshLoop, differenceToNow)
        }
    }

    private val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        handler.postDelayed(refreshLoop, T.secs(30).msecs()) // do not start immediately, app may be still starting
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(refreshLoop)
        disposable.clear()
    }

    private fun handleNewData() {
        if (!isEnabled()) return

        for (pack in context.packageManager.safeGetInstalledPackages(PackageManager.GET_PROVIDERS)) {
            val providers = pack.providers
            if (providers != null) {
                for (provider in providers) {
                    Log.d("Example", "provider: " + provider.authority)
                }
            }
        }

        context.contentResolver.query(contentUri, null, null, null, null)?.let { cr ->
            val glucoseValues = mutableListOf<TransactionGlucoseValue>()
            val calibrations = mutableListOf<CgmSourceTransaction.Calibration>()
            cr.moveToFirst()

            while (!cr.isAfterLast) {
                val timestamp = cr.getLong(0)
                val value = cr.getDouble(1) //value in mmol/l...
                val curr = cr.getDouble(2)

                // bypass already processed
                if (timestamp < sp.getLong(R.string.key_last_processed_intelligo_timestamp, 0L)) {
                    cr.moveToNext()
                    continue
                }

                if (timestamp > dateUtil.now() || timestamp == 0L) {
                    aapsLogger.error(LTag.BGSOURCE, "Error in received data date/time $timestamp")
                    cr.moveToNext()
                    continue
                }

                if (value < 2 || value > 25) {
                    aapsLogger.error(LTag.BGSOURCE, "Error in received data value (value out of bounds) $value")
                    cr.moveToNext()
                    continue
                }

                if (curr != 0.0)
                    glucoseValues += TransactionGlucoseValue(
                        timestamp = timestamp,
                        value = value * Constants.MMOLL_TO_MGDL,
                        raw = 0.0,
                        noise = null,
                        trendArrow = GlucoseValue.TrendArrow.NONE,
                        sourceSensor = GlucoseValue.SourceSensor.INTELLIGO_NATIVE
                    )
                else
                    calibrations.add(
                        CgmSourceTransaction.Calibration(
                            timestamp = timestamp,
                            value = value,
                            glucoseUnit = TherapyEvent.GlucoseUnit.MMOL
                        )
                    )
                sp.putLong(R.string.key_last_processed_intelligo_timestamp, timestamp)
                cr.moveToNext()
            }
            cr.close()

            if (glucoseValues.isNotEmpty() || calibrations.isNotEmpty())
                repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, calibrations, null))
                    .doOnError {
                        aapsLogger.error(LTag.DATABASE, "Error while saving values from IntelliGO App", it)
                    }
                    .blockingGet()
                    .also { savedValues ->
                        savedValues.inserted.forEach {
                            xDripBroadcast.sendIn640gMode(it)
                            aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                        }
                        savedValues.calibrationsInserted.forEach { calibration ->
                            calibration.glucose?.let { glucoseValue ->
                                uel.log(
                                    UserEntry.Action.CALIBRATION,
                                    UserEntry.Sources.Dexcom,
                                    ValueWithUnit.Timestamp(calibration.timestamp),
                                    ValueWithUnit.TherapyEventType(calibration.type),
                                    ValueWithUnit.fromGlucoseUnit(glucoseValue, calibration.glucoseUnit.toString)
                                )
                            }
                            aapsLogger.debug(LTag.DATABASE, "Inserted calibration $calibration")
                        }
                    }
        }
    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.INTELLIGO_NATIVE && sp.getBoolean(info.nightscout.core.utils.R.string.key_do_ns_upload, false)

    companion object {

        const val AUTHORITY = "alexpr.co.uk.infinivocgm.intelligo.cgm_db.CgmExternalProvider"

        //const val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
        const val TABLE_NAME = "CgmReading"
        const val INTERVAL = 180000L // 3 min
    }
}
