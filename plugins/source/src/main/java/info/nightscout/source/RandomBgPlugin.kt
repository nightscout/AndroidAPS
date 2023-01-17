package info.nightscout.source

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.isRunningTest
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CgmSourceTransaction
import info.nightscout.database.transactions.TransactionGlucoseValue
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.pump.VirtualPump
import info.nightscout.interfaces.source.BgSource
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.lang.Math.random
import java.util.Calendar
import java.util.GregorianCalendar
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
    private val virtualPump: VirtualPump,
    private val config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(R.drawable.ic_dice)
        .pluginName(R.string.random_bg)
        .shortName(R.string.random_bg_short)
        .preferencesId(R.xml.pref_bgsource)
        .description(R.string.description_source_random_bg),
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

    override fun advancedFilteringSupported(): Boolean = true

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.RANDOM && sp.getBoolean(info.nightscout.core.utils.R.string.key_do_ns_upload, false)

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
        return isRunningTest() || config.isUnfinishedMode() || virtualPump.isEnabled() && config.isEngineeringMode()
    }

    private fun handleNewData() {
        if (!isEnabled()) return

        val cal = GregorianCalendar()
        val currentMinute = cal[Calendar.MINUTE] + (cal[Calendar.HOUR_OF_DAY] % 2) * 60
        val bgMgdl = min + ((max - min) + (max - min) * sin(currentMinute / period * 2 * PI)) / 2 + (random() - 0.5) * (max - min) * 0.4

        cal[Calendar.MILLISECOND] = 0
        cal[Calendar.SECOND] = 0
        cal[Calendar.MINUTE] -= cal[Calendar.MINUTE] % 5
        val glucoseValues = mutableListOf<TransactionGlucoseValue>()
        glucoseValues += TransactionGlucoseValue(
            timestamp = cal.timeInMillis,
            value = bgMgdl,
            raw = 0.0,
            noise = null,
            trendArrow = GlucoseValue.TrendArrow.NONE,
            sourceSensor = GlucoseValue.SourceSensor.RANDOM
        )
        disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
            .subscribe({ savedValues ->
                           savedValues.inserted.forEach {
                               xDripBroadcast.sendIn640gMode(it)
                               aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                           }
                       }, { aapsLogger.error(LTag.DATABASE, "Error while saving values from Random plugin", it) }
            )
    }
}
