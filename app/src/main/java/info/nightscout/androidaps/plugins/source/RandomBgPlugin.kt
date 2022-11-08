package info.nightscout.androidaps.plugins.source

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.extensions.isRunningTest
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class RandomBgPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val repository: AppRepository,
    private val xDripBroadcast: XDripBroadcast,
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val buildHelper: BuildHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(R.drawable.ic_dice)
        .pluginName(R.string.randombg)
        .shortName(R.string.randombg_short)
        .preferencesId(R.xml.pref_bgsource)
        .description(R.string.description_source_randombg),
    aapsLogger, rh, injector
), BgSource {

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    companion object {

        const val interval = 5L // minutes
        const val min = 70 // mgdl
        const val max = 190 // mgdl
        const val period = 120.0 // minutes
    }

    init {
        refreshLoop = Runnable {
            handler.postDelayed(refreshLoop, T.mins(interval).msecs())
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
        val cal = GregorianCalendar()
        cal[Calendar.MILLISECOND] = 0
        cal[Calendar.SECOND] = 0
        cal[Calendar.MINUTE] -= cal[Calendar.MINUTE] % 5
        handler.postAtTime(refreshLoop, SystemClock.uptimeMillis() + cal.timeInMillis + T.mins(5).msecs() + 1000 - System.currentTimeMillis())
        disposable.clear()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(refreshLoop)
    }

    override fun specialEnableCondition(): Boolean {
        return isRunningTest() || virtualPumpPlugin.isEnabled() && buildHelper.isEngineeringMode()
//        return true
    }

    private fun handleNewData() {
        if (!isEnabled()) return

        val cal = GregorianCalendar()
        val currentMinute = cal[Calendar.MINUTE] + (cal[Calendar.HOUR_OF_DAY] % 2) * 60
        val bgMgdl = min + ((max - min) + (max - min) * sin(currentMinute / period * 2 * PI)) / 2

        cal[Calendar.MILLISECOND] = 0
        cal[Calendar.SECOND] = 0
        cal[Calendar.MINUTE] -= cal[Calendar.MINUTE] % 5
        val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
        glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
            timestamp = cal.timeInMillis,
            value = bgMgdl,
            raw = 0.0,
            noise = null,
            smoothed = null,
            trendArrow = GlucoseValue.TrendArrow.NONE,
            sourceSensor = GlucoseValue.SourceSensor.RANDOM
        )
        disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
            .subscribe({ savedValues ->
                           savedValues.inserted.forEach {
                               xDripBroadcast.send(it)
                               aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                           }
                       }, { aapsLogger.error(LTag.DATABASE, "Error while saving values from Random plugin", it) }
            )
    }
}
