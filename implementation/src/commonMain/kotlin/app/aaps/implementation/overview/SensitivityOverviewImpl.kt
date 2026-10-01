package app.aaps.implementation.overview

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.overview.SensitivityOverviewData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Moved here from the Overview chips ViewModel, unchanged in what it computes, so the watch can
 * show the same lines without a second copy of the rules. The master reads the loop's last run;
 * a client reads the device status the master uploaded.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class SensitivityOverviewImpl(
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val config: Config,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : SensitivityOverview {

    override suspend fun build(): SensitivityOverviewData {
        val lastAutosensData = iobCobCalculator.ads.getLastAutosensData("Overview", aapsLogger, dateUtil)
        val lastAutosensRatio = lastAutosensData?.autosensResult?.ratio
        val lastAutosensPercent = lastAutosensRatio?.let { it * 100 }

        val isEnabled = if (config.AAPSCLIENT) preferences.get(BooleanNonKey.AutosensUsedOnMainPhone)
        else constraintChecker.isAutosensModeEnabled().value()

        val profile = profileFunction.getProfile()
        val request = loop.lastRun?.request
        val isfMgdl = profile?.getProfileIsfMgdl()
        val variableSens =
            if (config.APS) request?.variableSens ?: 0.0
            else if (config.AAPSCLIENT) processedDeviceStatusData.getAPSResult()?.variableSens ?: 0.0
            else 0.0
        val ratioUsed =
            if (config.APS) request?.autosensResult?.ratio ?: 1.0
            else if (config.AAPSCLIENT) processedDeviceStatusData.openAPSData.suggested?.sensitivityRatio ?: 1.0
            else 1.0
        val units = profileFunction.getUnits()

        var asText = ""
        var isfFrom = ""
        var isfTo = ""
        val lines = ArrayList<String>()

        if (variableSens != isfMgdl && variableSens != 0.0 && isfMgdl != null) {
            // Variable ISF branch — hide "AS: 100%" from overview when ratio is exactly 100%
            lastAutosensPercent?.let {
                if (it != 100.0)
                    asText = rh.gs(CoreUiStrings.autosens_short, it)
                lines.add(rh.gs(CoreUiStrings.autosens_long, it))
            }
            val profileIsfDisplayed = profileUtil.fromMgdlToUnits(isfMgdl, units)
            val variableIsfDisplayed = profileUtil.fromMgdlToUnits(variableSens, units)
            isfFrom = decimalFormatter.to1Decimal(profileIsfDisplayed)
            isfTo = decimalFormatter.to1Decimal(variableIsfDisplayed)
            lines.add(rh.gs(CoreUiStrings.isf_profile, profileIsfDisplayed))
            lines.add(rh.gs(CoreUiStrings.isf_variable, variableIsfDisplayed))
            if (ratioUsed != 1.0 && ratioUsed != lastAutosensRatio)
                lines.add(rh.gs(CoreUiStrings.algorithm_long, ratioUsed * 100))
            val isfForCarbs = profile.getIsfMgdlForCarbs(dateUtil.now(), "Overview", config, processedDeviceStatusData)
            lines.add(rh.gs(CoreUiStrings.isf_for_carbs, profileUtil.fromMgdlToUnits(isfForCarbs, units)))
            if (config.APS) {
                activePlugin.activeAPS?.getSensitivityOverviewString()?.let { lines.add(it) }
            }
        } else {
            // Standard autosens-only branch — hide "AS: 100%" from chip but always show in dialog
            lastAutosensData?.let {
                val pct = it.autosensResult.ratio * 100
                if (pct != 100.0)
                    asText = rh.gs(CoreUiStrings.autosens_short, pct)
                lines.add(rh.gs(CoreUiStrings.autosens_long, pct))
            }
            if (isfMgdl != null) {
                val profileIsfDisplayed = profileUtil.fromMgdlToUnits(isfMgdl, units)
                lines.add(rh.gs(CoreUiStrings.isf_profile, profileIsfDisplayed))
                lastAutosensRatio?.let { ratio ->
                    lines.add(rh.gs(CoreUiStrings.isf_effective, profileUtil.fromMgdlToUnits(isfMgdl * ratio, units)))
                }
            }
        }

        return SensitivityOverviewData(
            asText = asText,
            isfFrom = isfFrom,
            isfTo = isfTo,
            lines = lines,
            ratio = lastAutosensRatio ?: 1.0,
            isEnabled = isEnabled,
            hasData = lastAutosensData != null
        )
    }
}
