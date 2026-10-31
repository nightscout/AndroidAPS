package app.aaps.implementation.overview

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.IsfStep
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
import app.aaps.core.objects.aps.IsfForCarbs
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.round

/**
 * Moved here from the Overview chips ViewModel so the watch can show the same lines without a
 * second copy of the rules. The master reads the loop's last run; a client reads the device status
 * the master uploaded.
 *
 * Every number here must be the one the algorithm used, not one worked out again from the profile.
 * The effective ISF is `profile ISF / sensitivity ratio`, the same way `DetermineBasalSMB` and
 * `DetermineBasalAutoISF` compute their `sens`. Multiplying instead of dividing was reported as
 * issue 5080: with a ratio under 100% the dialog showed a lower ISF while the algorithm used a
 * higher one.
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
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer
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
        // The ratio the algorithm really used. On the master it is taken from the APS result itself,
        // so a temp target that changes sensitivity is included - `autosensResult` alone would miss
        // that. On a client it is the master's value from the uploaded device status, because a
        // client runs its own autosens over Nightscout data and can land on a different number.
        //
        // 1.0 means "nothing to show": the algorithm kept the profile values, for example because
        // autosens is off. A value that is not above zero can only come from foreign device status
        // data; it is read as unknown rather than divided by.
        val ratioUsed = (
            if (config.APS) (request?.rawData() as? RT)?.sensitivityRatio ?: request?.autosensResult?.ratio
            else if (config.AAPSCLIENT) processedDeviceStatusData.openAPSData.suggested?.sensitivityRatio
            else null
            )?.takeIf { it > 0.0 } ?: 1.0
        // The algorithm's ratio is worth naming only when it reads differently than the autosens
        // value shown above. Both lines print whole percent, so comparing the raw numbers would
        // print the same percentage twice: autosens is computed again on every new reading, while
        // the loop ran up to five minutes ago.
        val namesOwnRatio = ratioUsed != 1.0 &&
            (lastAutosensPercent == null || round(ratioUsed * 100) != round(lastAutosensPercent))
        val units = profileFunction.getUnits()

        var asText = ""
        var isfFrom = ""
        var isfTo = ""
        var isfStep = IsfStep.SAME
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
            isfStep = isfStep(isfFrom, isfTo, profileIsfDisplayed, variableIsfDisplayed)
            lines.add(rh.gs(CoreUiStrings.isf_profile, profileIsfDisplayed))
            lines.add(rh.gs(CoreUiStrings.isf_variable, variableIsfDisplayed))
            if (namesOwnRatio)
                lines.add(rh.gs(CoreUiStrings.algorithm_long, ratioUsed * 100))
            val now = dateUtil.now()
            val isfForCarbs = IsfForCarbs.load(persistenceLayer, now, now).isfMgdl(profile, now)
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
                // The algorithm divides the profile ISF by the ratio, it does not multiply by it: a
                // ratio under 100% means the user is more sensitive, so one unit moves the glucose
                // further and the ISF goes up. This is the "ISF from ... to ..." line that
                // DetermineBasal writes. At a ratio of 1.0 the algorithm keeps the profile ISF, so
                // there is nothing to show - for example when autosens is turned off.
                if (ratioUsed != 1.0) {
                    val effectiveIsfDisplayed = profileUtil.fromMgdlToUnits(isfMgdl / ratioUsed, units)
                    // The chip shows the same step as the variable ISF branch does, so a user on
                    // plain autosens sees the two values and not only a percentage.
                    isfFrom = decimalFormatter.to1Decimal(profileIsfDisplayed)
                    isfTo = decimalFormatter.to1Decimal(effectiveIsfDisplayed)
                    isfStep = isfStep(isfFrom, isfTo, profileIsfDisplayed, effectiveIsfDisplayed)
                    lines.add(rh.gs(CoreUiStrings.isf_effective, effectiveIsfDisplayed))
                }
            }
            // A client computes its own autosens, and a temp target can take sensitivity over from
            // autosens on the master. Either way the effective ISF above was worked out from this
            // ratio, not from the autosens value, so the user is told which one it was.
            if (namesOwnRatio)
                lines.add(rh.gs(CoreUiStrings.algorithm_long, ratioUsed * 100))
        }

        return SensitivityOverviewData(
            asText = asText,
            isfFrom = isfFrom,
            isfTo = isfTo,
            isfStep = isfStep,
            lines = lines,
            ratio = lastAutosensRatio ?: 1.0,
            isEnabled = isEnabled,
            hasData = lastAutosensData != null
        )
    }

    /**
     * The step between the two ISF values the chip shows. The texts decide whether there is a step
     * to show at all - two values that round to the same text must not get an arrow - and the
     * numbers decide which way it points.
     */
    private fun isfStep(fromText: String, toText: String, from: Double, to: Double): IsfStep =
        when {
            fromText == toText -> IsfStep.SAME
            to > from          -> IsfStep.UP
            else               -> IsfStep.DOWN
        }
}
