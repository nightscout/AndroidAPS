package app.aaps.plugins.constraints.bgQualityCheck

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _stateFlow = MutableStateFlow(BgQualityCheck.State.UNKNOWN)
    override val stateFlow: StateFlow<BgQualityCheck.State> = _stateFlow.asStateFlow()
    override var state: BgQualityCheck.State
        get() = _stateFlow.value
        set(value) {
            _stateFlow.value = value
        }
    override var message: String = ""

    // Fallback to LGS if BG values are doubled
    override suspend fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> =
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
        if (sizeRecords < 5) {
            aapsLogger.debug(LTag.APS, "BG_FLAT_CHECK: Not enough data ($sizeRecords < 5)")
            return null // not enough data
        }

        // 🚀 FAST FAIL: If recent data shows movement, assume valid.
        // Avoids false positives when smoothing hides short term noise but trend exists.
        val recentSubset = data.take(min(data.size, 4))
        val recentRange = recentSubset.maxOf { it.value } - recentSubset.minOf { it.value }
        if (recentRange > 2.9) {
             aapsLogger.debug(LTag.APS, "BG_FLAT_CHECK: Recent activity ($recentRange > 2.9), ALIVE.")
             return false
        }
        
        // 🔧 CRITICAL FIX: Correct flatness detection logic
        // We need TWO things:
        // 1. Recent data: newest BG (data[0]) must be fresh (< 12 min old)
        // 2. Historical coverage: we need data spanning back to our analysis window
        
        val newestTimestamp = data[0].timestamp
        val newestAgeMins = (now - newestTimestamp) / (60 * 1000)
        
        // Check 1: Is newest BG too old? (> 12 min = can't detect CURRENT flatness)
        if (newestTimestamp < now - 12 * 60 * 1000L) {
            aapsLogger.debug(LTag.APS, "BG_FLAT_CHECK: Newest BG too old ($newestAgeMins min)")
            return null
        }
        
        // Check 2: Do we have enough historical data?
        // We need BG readings going back at least 45 minutes
        // Find the oldest BG in our dataset
        val oldestBg = data.lastOrNull()
        if (oldestBg == null || oldestBg.timestamp > now - minutes * 60 * 1000L) {
            // Not enough historical coverage
            val oldestAgeMins = if (oldestBg != null) (now - oldestBg.timestamp) / (60 * 1000) else 0
            aapsLogger.debug(LTag.APS, "BG_FLAT_CHECK: Not enough history (oldest: $oldestAgeMins min, need: $minutes min)")
            return null
        }

        var bgmin: Double = lastBg
        var bgmax: Double = bgmin
        var countAnalyzed = 0
        
        // 🔧 CRITICAL: Use RAW values (bg.value) NOT smoothed values!
        // AdaptiveSmoothie can reduce variance artificially → false positive flat detection
        for (bg in data) {
            if (bg.timestamp < offset) break
            
            // ALWAYS use .value (RAW), NEVER .smoothed or .recalculated
            val rawValue = bg.value
            
            bgmin = min(bgmin, rawValue)
            bgmax = max(bgmax, rawValue)
            countAnalyzed++
            if (bgmax - bgmin > maxDelta) {
                aapsLogger.debug(LTag.APS, "BG_FLAT_CHECK: NOT FLAT - delta=${(bgmax - bgmin).toInt()} > $maxDelta (analyzed $countAnalyzed readings)")
                return false
            }
        }
        
        aapsLogger.warn(LTag.APS, "BG_FLAT_CHECK: ⚠️ FLAT DETECTED! delta=${(bgmax - bgmin).toInt()} ≤ $maxDelta over $minutes min ($countAnalyzed readings, min=${bgmin.toInt()}, max=${bgmax.toInt()})")
        return true
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
        const val staleBgMaxDeltaMgdl = 4.0
    }
}