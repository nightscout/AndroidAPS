package info.nightscout.androidaps.plugins.source

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSource
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
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
    private val xDripBroadcast: XDripBroadcast,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger,
    private val fabricPrivacy: FabricPrivacy
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(R.drawable.ic_glunovo)
        .pluginName(R.string.glunovo)
        .preferencesId(R.xml.pref_bgsource)
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
                val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
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
                        glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                            timestamp = timestamp,
                            value = value * Constants.MMOLL_TO_MGDL,
                            raw = 0.0,
                            smoothed = null,
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
                                xDripBroadcast.send(it)
                                aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                            }
                            savedValues.calibrationsInserted.forEach { calibration ->
                                calibration.glucose?.let { glucosevalue ->
                                    uel.log(
                                        UserEntry.Action.CALIBRATION,
                                        UserEntry.Sources.Dexcom,
                                        ValueWithUnit.Timestamp(calibration.timestamp),
                                        ValueWithUnit.TherapyEventType(calibration.type),
                                        ValueWithUnit.fromGlucoseUnit(glucosevalue, calibration.glucoseUnit.toString)
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

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.GLUNOVO_NATIVE && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

    companion object {

        @Suppress("SpellCheckingInspection")
        const val AUTHORITY = "alexpr.co.uk.infinivocgm.cgm_db.CgmExternalProvider/"
        const val TABLE_NAME = "CgmReading"
        const val INTERVAL = 180000L // 3 min
    }
}
