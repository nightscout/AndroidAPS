package info.nightscout.androidaps.plugins.constraints.bgQualityCheck

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventBucketedDataCreated
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class BgQualityCheckPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val rxBus: RxBus,
    private val iobCobCalculator: IobCobCalculator,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.dst_plugin_name),
    aapsLogger, rh, injector
), Constraints {

    private var disposable: CompositeDisposable = CompositeDisposable()

    enum class State {
        UNKNOWN,
        FIVE_MIN_DATA,
        RECALCULATED,
        DOUBLED
    }

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ processBgData() }, fabricPrivacy::logException)
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
    }

    var state: State = State.UNKNOWN
    var message: String = ""

    // Return false if BG values are doubled
    @Suppress("ReplaceGetOrSet")
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (state == State.DOUBLED)
            value.set(aapsLogger, false, "Doubled values in BGSource", this)
        return value
    }

    @Suppress("CascadeIf")
    fun processBgData() {
        val readings = iobCobCalculator.ads.getBgReadingsDataTableCopy()
        for (i in readings.indices)
            if (i < readings.size - 2)
                if (abs(readings[i].timestamp - readings[i + 1].timestamp) <= T.secs(20).msecs()) {
                    state = State.DOUBLED
                    aapsLogger.debug(LTag.CORE, "BG similar. Turning on red state.\n${readings[i]}\n${readings[i+1]}")
                    message = rh.gs(R.string.bg_too_close, dateUtil.dateAndTimeAndSecondsString(readings[i].timestamp), dateUtil.dateAndTimeAndSecondsString(readings[i+1].timestamp))
                    return
                }
        if (iobCobCalculator.ads.lastUsed5minCalculation == true) {
            state = State.FIVE_MIN_DATA
            message = "Data is clean"
        } else if (iobCobCalculator.ads.lastUsed5minCalculation == false) {
            state = State.RECALCULATED
            message = rh.gs(R.string.recalculated_data_used)
        } else {
            state = State.UNKNOWN
            message = ""
        }
    }

    fun icon(): Int =
        when (state) {
            State.UNKNOWN       -> 0
            State.FIVE_MIN_DATA -> 0
            State.RECALCULATED  -> R.drawable.ic_baseline_warning_24_yellow
            State.DOUBLED       -> R.drawable.ic_baseline_warning_24_red
        }
}