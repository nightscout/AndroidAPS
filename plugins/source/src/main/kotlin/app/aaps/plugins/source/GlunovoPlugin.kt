package app.aaps.plugins.source

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CgmSourceTransaction
import app.aaps.database.transactions.TransactionGlucoseValue
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlunovoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val context: Context,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger,
    private val fabricPrivacy: FabricPrivacy
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_glunovo)
        .preferencesId(R.xml.pref_bgsource)
        .pluginName(R.string.glunovo)
        .shortName(R.string.glunovo)
        .description(R.string.description_source_glunovo),
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
            val lastReadTimestamp = sp.getLong(R.string.key_last_processed_glunovo_timestamp, 0L)
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

        try {
            context.contentResolver.query(contentUri, null, null, null, null)?.let { cr ->
                val glucoseValues = mutableListOf<TransactionGlucoseValue>()
                val calibrations = mutableListOf<CgmSourceTransaction.Calibration>()
                cr.moveToFirst()

                while (!cr.isAfterLast) {
                    val timestamp = cr.getLong(0)
                    val value = cr.getDouble(1) //value in mmol/l...
                    val curr = cr.getDouble(2)

                    // bypass already processed
                    if (timestamp < sp.getLong(R.string.key_last_processed_glunovo_timestamp, 0L)) {
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
                            sourceSensor = GlucoseValue.SourceSensor.GLUNOVO_NATIVE
                        )
                    else
                        calibrations.add(
                            CgmSourceTransaction.Calibration(
                                timestamp = timestamp,
                                value = value,
                                glucoseUnit = TherapyEvent.GlucoseUnit.MMOL
                            )
                        )
                    sp.putLong(R.string.key_last_processed_glunovo_timestamp, timestamp)
                    cr.moveToNext()
                }
                cr.close()

                if (glucoseValues.isNotEmpty() || calibrations.isNotEmpty())
                    repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, calibrations, null))
                        .doOnError {
                            aapsLogger.error(LTag.DATABASE, "Error while saving values from Glunovo App", it)
                        }
                        .blockingGet()
                        .also { savedValues ->
                            savedValues.inserted.forEach {
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
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.CORE, "Exception", e)
        }
    }

    companion object {

        const val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
        const val TABLE_NAME = "CgmReading"
        const val INTERVAL = 180000L // 3 min
    }
}
