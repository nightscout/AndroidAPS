package info.nightscout.androidaps.plugins.source

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
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
import io.reactivex.disposables.CompositeDisposable
import java.util.*
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
    private val broadcastToXDrip: XDripBroadcast
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

    private val loopHandler: Handler = Handler(HandlerThread(this::class.java.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    private val contentUri: Uri = Uri.parse("content://$AUTHORITY/$TABLE_NAME")

    init {
        refreshLoop = Runnable {
            loopHandler.postDelayed(refreshLoop, INTERVAL)
            handleNewData()
        }
    }

    private val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        loopHandler.postDelayed(refreshLoop, INTERVAL)
    }

    override fun onStop() {
        super.onStop()
        loopHandler.removeCallbacks(refreshLoop)
        disposable.clear()
    }

    private fun handleNewData() {
        if (!isEnabled()) return

        context.contentResolver.query(contentUri, null, null, null, null)?.let { cr ->
            cr.moveToLast()
            val curTime = Calendar.getInstance().timeInMillis
            val time = cr.getLong(0)
            val value = cr.getDouble(1) //value in mmol/l...
            if (time > curTime || time == 0L) {
                aapsLogger.error(LTag.BGSOURCE, "Error in received data date/time $time")
                return
            }

            if (value < 2 || value > 25) {
                aapsLogger.error(LTag.BGSOURCE, "Error in received data value (value out of bounds) $value")
                return
            }

            val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = time,
                value = value,
                raw = 0.0,
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.NONE,
                sourceSensor = GlucoseValue.SourceSensor.GLUNOVO_NATIVE
            )
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from Glunovo App", it)
                }
                .blockingGet()
                .also { savedValues ->
                    savedValues.inserted.forEach {
                        broadcastToXDrip(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }

            cr.close()
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
