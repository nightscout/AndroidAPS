package app.aaps.ui.widget.glance

import androidx.compose.ui.graphics.toArgb
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.displayText
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.compose.DarkGeneralColors
import app.aaps.core.ui.compose.loopColor
import app.aaps.core.ui.compose.ttReasonColor
import app.aaps.ui.R
import app.aaps.ui.widget.directionToDrawableRes
import javax.inject.Inject
import kotlin.math.abs

class WidgetStateLoader @Inject constructor(
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val lastBgData: LastBgData,
    private val trendCalculator: TrendCalculator,
    private val rh: ResourceHelper,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val config: Config,
    private val preferences: Preferences,
    private val constraintChecker: ConstraintsChecker,
    private val decimalFormatter: DecimalFormatter,
    private val persistenceLayer: PersistenceLayer
) {

    suspend fun loadState(appWidgetId: Int): WidgetRenderState {
        val alpha = preferences.get(IntComposedKey.WidgetOpacity, appWidgetId)
        val useBlack = preferences.get(BooleanComposedKey.WidgetUseBlack, appWidgetId)
        val backgroundColor = resolveWidgetBackground(config, useBlack, alpha)

        val lastBg = lastBgData.lastBg()
        val bgText = lastBg?.let { profileUtil.fromMgdlToStringInUnits(it.recalculated) }
            ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
        val bgColor = when {
            lastBgData.isLow()  -> rh.gc(app.aaps.core.ui.R.color.widget_low)
            lastBgData.isHigh() -> rh.gc(app.aaps.core.ui.R.color.widget_high)
            else                -> rh.gc(app.aaps.core.ui.R.color.widget_inrange)
        }
        val strikeThrough = !lastBgData.isActualBg()

        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val arrowResId = lastBg?.let { (trendArrow ?: TrendArrow.FLAT).directionToDrawableRes() }

        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val unavailable = rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
        val deltaText = glucoseStatus?.let { profileUtil.fromMgdlToSignedStringInUnits(it.delta) } ?: unavailable
        val timeAgoText = dateUtil.minOrSecAgo(rh, lastBg?.timestamp)

        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val iobTotal = bolusIob.iob + basalIob.basaliob
        val iobText = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, iobTotal)
        val iobActive = abs(iobTotal) > 0.001

        val cobInfo = iobCobCalculator.getCobInfo("Overview COB")
        var cobText = cobInfo.displayText(rh, decimalFormatter) ?: unavailable
        var cobActive = (cobInfo.displayCob ?: 0.0) > 0.0
        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                val lastCarbsTime = persistenceLayer.getNewestCarbs()?.timestamp ?: 0L
                if (lastCarbsTime < lastRun.lastAPSRun) {
                    cobText += "\n" + constraintsProcessed.carbsReq + " " + rh.gs(app.aaps.core.ui.R.string.required)
                    cobActive = true
                }
            }
        }

        val units = profileFunction.getUnits()
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        val tempTargetIconResId = when (tempTarget?.reason) {
            TT.Reason.EATING_SOON  -> R.drawable.ic_widget_tt_eating_soon
            TT.Reason.ACTIVITY     -> R.drawable.ic_widget_tt_activity
            TT.Reason.HYPOGLYCEMIA -> R.drawable.ic_widget_tt_hypo
            else                   -> R.drawable.ic_widget_tt_manual
        }
        var tempTargetText = ""
        var tempTargetColor = WidgetTextMuted
        var tempTargetActive = false
        if (tempTarget != null) {
            tempTargetText = profileUtil.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units) +
                " " + dateUtil.untilString(tempTarget.end, rh)
            tempTargetColor = tempTarget.reason.ttReasonColor(DarkGeneralColors).toArgb()
            tempTargetActive = true
        } else {
            profileFunction.getProfile()?.let { profile ->
                val targetUsed = loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0
                if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                    tempTargetText = profileUtil.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units)
                    tempTargetColor = DarkGeneralColors.adjusted.toArgb()
                    tempTargetActive = true
                } else {
                    tempTargetText = profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units)
                }
            }
        }

        val profileText = profileFunction.getProfileNameWithRemainingTime()
        val profileModified = profileFunction.getProfile()?.let {
            it is ProfileSealed.EPS && (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
        } ?: false

        val lastAutosensData = iobCobCalculator.ads.getLastAutosensData("Widget", aapsLogger, dateUtil)
        val lastAutosensRatio = lastAutosensData?.let { it.autosensResult.ratio * 100 }
        val autosensEnabled = constraintChecker.isAutosensModeEnabled().value()
        val sensitivityIconResId = if (autosensEnabled)
            lastAutosensRatio?.let {
                when {
                    it > 100.0 -> R.drawable.ic_widget_as_above
                    it < 100.0 -> R.drawable.ic_widget_as_below
                    else       -> R.drawable.ic_widget_as_equal
                }
            } ?: R.drawable.ic_widget_as_equal
        else
            lastAutosensRatio?.let {
                when {
                    it > 100.0 -> R.drawable.ic_widget_as_x_above
                    it < 100.0 -> R.drawable.ic_widget_as_x_below
                    else       -> R.drawable.ic_widget_as_x_equal
                }
            } ?: R.drawable.ic_widget_as_x_equal
        val sensitivityText = lastAutosensData?.let {
            rh.gs(app.aaps.core.ui.R.string.autosens_short, it.autosensResult.ratio * 100)
        } ?: "—"

        val profile = profileFunction.getProfile()
        val tbrIconResId = if (profile == null) R.drawable.ic_widget_no_tbr
        else {
            val basalData = iobCobCalculator.getBasalData(profile, dateUtil.now())
            when {
                !basalData.isTempBasalRunning                             -> R.drawable.ic_widget_no_tbr
                abs(basalData.tempBasalAbsolute - basalData.basal) < 0.01 -> R.drawable.ic_widget_no_tbr
                basalData.tempBasalAbsolute > basalData.basal             -> R.drawable.ic_widget_tbr_high
                else                                                      -> R.drawable.ic_widget_tbr_low
            }
        }

        val mode = loop.runningMode()
        val runningModeText = rh.gs(
            when (mode) {
                RM.Mode.OPEN_LOOP         -> app.aaps.core.ui.R.string.openloop
                RM.Mode.CLOSED_LOOP       -> app.aaps.core.ui.R.string.closedloop
                RM.Mode.CLOSED_LOOP_LGS   -> R.string.widget_rm_lgs
                RM.Mode.DISABLED_LOOP     -> R.string.widget_rm_disabled
                RM.Mode.SUPER_BOLUS       -> app.aaps.core.ui.R.string.superbolus
                RM.Mode.DISCONNECTED_PUMP -> app.aaps.core.ui.R.string.disconnected
                RM.Mode.SUSPENDED_BY_PUMP,
                RM.Mode.SUSPENDED_BY_USER,
                RM.Mode.SUSPENDED_BY_DST  -> R.string.widget_rm_suspended

                RM.Mode.RESUME            -> R.string.widget_rm_resume
            }
        )
        val runningModeColor = mode.loopColor(DarkGeneralColors).toArgb()
        val runningModeIconResId = when (mode) {
            RM.Mode.OPEN_LOOP         -> R.drawable.ic_widget_loop_open
            RM.Mode.CLOSED_LOOP,
            RM.Mode.RESUME            -> R.drawable.ic_widget_loop_closed

            RM.Mode.CLOSED_LOOP_LGS   -> R.drawable.ic_widget_loop_lgs
            RM.Mode.DISABLED_LOOP     -> R.drawable.ic_widget_loop_disabled
            RM.Mode.SUPER_BOLUS       -> R.drawable.ic_widget_loop_superbolus
            RM.Mode.DISCONNECTED_PUMP -> R.drawable.ic_widget_loop_disconnected
            RM.Mode.SUSPENDED_BY_PUMP,
            RM.Mode.SUSPENDED_BY_USER,
            RM.Mode.SUSPENDED_BY_DST  -> R.drawable.ic_widget_loop_paused
        }
        val runningModeActive = mode != RM.Mode.CLOSED_LOOP

        return WidgetRenderState(
            bgText = bgText,
            bgColor = bgColor,
            strikeThrough = strikeThrough,
            arrowResId = arrowResId,
            deltaText = deltaText,
            timeAgoText = timeAgoText,
            iobText = iobText,
            iobActive = iobActive,
            cobText = cobText,
            cobActive = cobActive,
            tempTargetText = tempTargetText,
            tempTargetColor = tempTargetColor,
            tempTargetActive = tempTargetActive,
            tempTargetIconResId = tempTargetIconResId,
            profileText = profileText,
            profileModified = profileModified,
            profileIconResId = R.drawable.ic_widget_profile,
            iobIconResId = R.drawable.ic_widget_bolus,
            cobIconResId = R.drawable.ic_widget_carbs,
            runningModeText = runningModeText,
            runningModeColor = runningModeColor,
            runningModeIconResId = runningModeIconResId,
            runningModeActive = runningModeActive,
            sensitivityIconResId = sensitivityIconResId,
            sensitivityText = sensitivityText,
            tbrIconResId = tbrIconResId,
            backgroundColor = backgroundColor
        )
    }
}
