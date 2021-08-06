package info.nightscout.androidaps.plugins.source

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
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class RandomBgPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val buildHelper: BuildHelper,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val repository: AppRepository,
    private val xDripBroadcast: XDripBroadcast
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_dice)
    .pluginName(R.string.randombg)
    .shortName(R.string.randombg_short)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_randombg),
    aapsLogger, resourceHelper, injector
), BgSource {

    private val loopHandler: Handler = Handler(HandlerThread(RandomBgPlugin::class.java.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    companion object {

        const val interval = 5L // minutes
        const val min = 70 // mgdl
        const val max = 190 // mgdl
        const val period = 90.0 // minutes
    }

    init {
        refreshLoop = Runnable {
            loopHandler.postDelayed(refreshLoop, T.mins(interval).msecs())
            handleNewData()
        }
    }

    private val disposable = CompositeDisposable()

    override fun advancedFilteringSupported(): Boolean {
        return true
    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.RANDOM && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

    override fun onStart() {
        super.onStart()
        loopHandler.postDelayed(refreshLoop, T.mins(interval).msecs())
        disposable.clear()
    }

    override fun onStop() {
        super.onStop()
        loopHandler.removeCallbacks(refreshLoop)
    }

    override fun specialEnableCondition(): Boolean {
//        return isRunningTest() || virtualPumpPlugin.isEnabled(PluginType.PUMP) && buildHelper.isEngineeringMode()
        return true
    }

    private fun handleNewData() {
        if (!isEnabled(PluginType.BGSOURCE)) return

        val cal = GregorianCalendar()
        val currentMinute = cal.get(Calendar.MINUTE) + (cal.get(Calendar.HOUR_OF_DAY) % 2) * 60
        val bgMgdl = min + ((max - min) + (max - min) * sin(currentMinute / period * 2 * PI)) / 2

        val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
        glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
            timestamp = dateUtil.now(),
            value = bgMgdl,
            raw = 0.0,
            noise = null,
            trendArrow = GlucoseValue.TrendArrow.NONE,
            sourceSensor = GlucoseValue.SourceSensor.RANDOM
        )
        disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
            .subscribe({ savedValues ->
                savedValues.inserted.forEach {
                    xDripBroadcast(it)
                    aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                }
            }, { aapsLogger.error(LTag.DATABASE, "Error while saving values from Random plugin", it) }
            )
    }
}
