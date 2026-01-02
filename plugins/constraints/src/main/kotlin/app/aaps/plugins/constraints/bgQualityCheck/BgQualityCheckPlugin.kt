package app.aaps.plugins.constraints.bgQualityCheck

import androidx.annotation.DrawableRes
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.constraints.R
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Singleton
class BgQualityCheckPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val rxBus: RxBus,
    private val iobCobCalculator: IobCobCalculator,
    private val aapsSchedulers:
    AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.bg_quality),
    aapsLogger, rh
), PluginConstraints, BgQualityCheck {

    private var disposable: CompositeDisposable = CompositeDisposable()

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

    override var state: BgQualityCheck.State = BgQualityCheck.State.UNKNOWN
    override var message: String = ""

    // Fallback to LGS if BG values are doubled
    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> =
        if (state == BgQualityCheck.State.DOUBLED)
            maxIob.set(0.0, "Limiting max IOB to 0 U due to doubled values in BG Source", this)
        else
            maxIob

    fun processBgData() {
        val readings = iobCobCalculator.ads.getBgReadingsDataTableCopy()
        val lastBg = iobCobCalculator.ads.lastBg()
        for (i in readings.indices)
        // Deltas are calculated from last ~50 min. Detect RED state only on this interval
            if (i < min(readings.size - 2, 10))
                if (abs(readings[i].timestamp - readings[i + 1].timestamp) <= T.secs(20).msecs()) {
                    state = BgQualityCheck.State.DOUBLED
                    aapsLogger.debug(LTag.CORE, "BG similar. Turning on red state.\n${readings[i]}\n${readings[i + 1]}")
                    message = rh.gs(R.string.bg_too_close, dateUtil.dateAndTimeAndSecondsString(readings[i].timestamp), dateUtil.dateAndTimeAndSecondsString(readings[i + 1].timestamp))
                    return
                }
        if (lastBg?.sourceSensor?.isLibre1() == true && isBgFlatForInterval(staleBgCheckPeriodMinutes, staleBgMaxDeltaMgdl) == true) {
            state = BgQualityCheck.State.FLAT
            message = rh.gs(R.string.a11y_bg_quality_flat)
        } else if (iobCobCalculator.ads.lastUsed5minCalculation == true) {
            state = BgQualityCheck.State.FIVE_MIN_DATA
            message = "Data is clean"
        } else if (iobCobCalculator.ads.lastUsed5minCalculation == false) {
            state = BgQualityCheck.State.RECALCULATED
            message = rh.gs(R.string.recalculated_data_used)
        } else {
            state = BgQualityCheck.State.UNKNOWN
            message = ""
        }
    }

    // inspired by @justmara
    @Suppress("SpellCheckingInspection", "SameParameterValue")
    private fun isBgFlatForInterval(minutes: Long, maxDelta: Double): Boolean? {
        val data = iobCobCalculator.ads.getBgReadingsDataTableCopy()
        val lastBg = iobCobCalculator.ads.lastBg()?.value
        val now = dateUtil.now()
        val offset = now - T.mins(minutes).msecs()
        val sizeRecords = data.size

        lastBg ?: return null
        if (sizeRecords < 5) return null // not enough data
        if (data[data.size - 1].timestamp > now - 45 * 60 * 1000L) return null // data too fresh to detect
        if (data[0].timestamp < now - 7 * 60 * 1000L) return null // data is old

        var bgmin: Double = lastBg
        var bgmax: Double = bgmin
        for (bg in data) {
            if (bg.timestamp < offset) break
            bgmin = min(bgmin, bg.value)
            bgmax = max(bgmax, bg.value)
            if (bgmax - bgmin > maxDelta) return false
        }
        return true
    }

    @DrawableRes override fun icon(): Int =
        when (state) {
            BgQualityCheck.State.UNKNOWN       -> 0
            BgQualityCheck.State.FIVE_MIN_DATA -> 0
            BgQualityCheck.State.RECALCULATED  -> R.drawable.ic_baseline_warning_24_yellow
            BgQualityCheck.State.DOUBLED       -> R.drawable.ic_baseline_warning_24_red
            BgQualityCheck.State.FLAT          -> R.drawable.ic_baseline_trending_flat_24
        }

    override fun stateDescription(): String =
        when (state) {
            BgQualityCheck.State.RECALCULATED -> rh.gs(R.string.a11y_bg_quality_recalculated)
            BgQualityCheck.State.DOUBLED      -> rh.gs(R.string.a11y_bg_quality_doubles)
            BgQualityCheck.State.FLAT         -> rh.gs(R.string.a11y_bg_quality_flat)
            else                              -> ""
        }

    companion object {

        const val staleBgCheckPeriodMinutes = 45L
        const val staleBgMaxDeltaMgdl = 2.0
    }
}