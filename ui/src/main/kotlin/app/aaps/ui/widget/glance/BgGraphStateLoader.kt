package app.aaps.ui.widget.glance

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.widget.directionToDrawableRes
import javax.inject.Inject
import javax.inject.Provider

data class BgGraphRenderState(
    val input: BgGraphInput,
    val colors: BgGraphColors,
    val backgroundColor: Int,
    val bgText: String,
    val bgColor: Int,
    val arrowResId: Int?,
    val strikeThrough: Boolean
)

/**
 * Snapshots [OverviewDataCache.bucketedDataFlow] + current BG/trend for the BG-dots widget.
 *
 * The cache is wrapped in [Provider] so its construction (which subscribes to DB
 * flows that touch [PluginStore]) is deferred until [loadState] runs — by then the
 * widget has already awaited `config.appInitialized`, so all lateinit plugin state
 * is ready.
 */
class BgGraphStateLoader @Inject constructor(
    private val cacheProvider: Provider<OverviewDataCache>,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val lastBgData: LastBgData,
    private val trendCalculator: TrendCalculator,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val config: Config
) {

    suspend fun loadState(appWidgetId: Int): BgGraphRenderState {
        val cache = cacheProvider.get()
        val alpha = preferences.get(IntComposedKey.WidgetOpacity, appWidgetId)
        val useBlack = preferences.get(BooleanComposedKey.WidgetUseBlack, appWidgetId)
        val backgroundColor = resolveWidgetBackground(config, useBlack, alpha)

        val now = dateUtil.now()
        val fromTime = now - HISTORY_WINDOW_MS

        val units = profileFunction.getUnits()
        val (yMinFloor, yMaxFloor) = when (units) {
            GlucoseUnit.MGDL -> Y_MIN_MGDL to Y_MAX_MGDL
            GlucoseUnit.MMOL -> Y_MIN_MMOL to Y_MAX_MMOL
        }

        val bucketed = cache.bucketedDataFlow.value
        val dataMax = bucketed.asSequence()
            .filter { it.timestamp in fromTime..now }
            .maxOfOrNull { it.value } ?: yMaxFloor
        val yMax = maxOf(yMaxFloor, dataMax + dataMax * 0.05)

        val input = BgGraphInput(
            bucketed = bucketed,
            fromTimeMs = fromTime,
            toTimeMs = now,
            yMinUserUnits = yMinFloor,
            yMaxUserUnits = yMax
        )

        val colors = BgGraphColors(
            veryLow = rh.gc(app.aaps.core.ui.R.color.widget_very_low),
            low = rh.gc(app.aaps.core.ui.R.color.widget_low),
            inRange = rh.gc(app.aaps.core.ui.R.color.widget_inrange),
            high = rh.gc(app.aaps.core.ui.R.color.widget_high),
            veryHigh = rh.gc(app.aaps.core.ui.R.color.widget_very_high)
        )

        // Current BG + trend (same logic as WidgetStateLoader)
        val lastBg = lastBgData.lastBg()
        val bgText = lastBg?.let { profileUtil.fromMgdlToStringInUnits(it.recalculated) }
            ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
        val bgColor = when {
            lastBgData.isVeryLow()  -> colors.veryLow
            lastBgData.isLow()      -> colors.low
            lastBgData.isVeryHigh() -> colors.veryHigh
            lastBgData.isHigh()     -> colors.high
            else                    -> colors.inRange
        }
        val strikeThrough = !lastBgData.isActualBg()
        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val arrowResId = lastBg?.let { (trendArrow ?: TrendArrow.FLAT).directionToDrawableRes() }

        return BgGraphRenderState(
            input = input,
            colors = colors,
            backgroundColor = backgroundColor,
            bgText = bgText,
            bgColor = bgColor,
            arrowResId = arrowResId,
            strikeThrough = strikeThrough
        )
    }

    private companion object {

        const val HISTORY_WINDOW_MS = 6L * 60 * 60 * 1000

        const val Y_MIN_MGDL = 40.0
        const val Y_MAX_MGDL = 220.0
        const val Y_MIN_MMOL = 2.2
        const val Y_MAX_MMOL = 12.2
    }
}
