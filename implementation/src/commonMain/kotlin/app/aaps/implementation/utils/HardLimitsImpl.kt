package app.aaps.implementation.utils

import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.max
import kotlin.math.min

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class HardLimitsImpl(
    private val aapsLogger: AAPSLogger,
    // A provider: the alert core reaches much of the app, and is needed only when a limit is hit
    private val alerts: () -> Alerts,
    private val preferences: Preferences,
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus
) : HardLimits {

    private fun loadAge(): HardLimits.AgeType {
        // Read the preference and build the value list once. This runs on every limit lookup, so it
        // must not do the work per candidate age.
        val stored = preferences.get(StringKey.SafetyAge)
        val values = ageEntryValues()
        return HardLimits.AgeType.entries.firstOrNull { values[it.ordinal] == stored } ?: HardLimits.AgeType.ADULT
    }

    // The maps below hold an entry for every AgeType and loadAge() always returns one, so getValue cannot fail.
    override fun maxBolus(): Double = HardLimits.MAX_BOLUS.getValue(loadAge())
    override fun maxIobAMA(): Double = HardLimits.MAX_IOB_AMA.getValue(loadAge())
    override fun maxIobSMB(): Double = HardLimits.MAX_IOB_SMB.getValue(loadAge())
    override fun maxBasal(): Double = HardLimits.MAX_BASAL.getValue(loadAge())
    override fun diaRange(): ClosedFloatingPointRange<Double> = HardLimits.LIMIT_DIA.getValue(loadAge())
    override fun peakRange(): IntRange = HardLimits.LIMIT_PEAK
    override fun icRange(): ClosedFloatingPointRange<Double> = HardLimits.LIMIT_IC.getValue(loadAge())

    // safety checks
    override fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    override fun checkHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    override fun verifyHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Double =
        verifyHardLimits(value, rh.gs(valueName), lowLimit, highLimit)

    // The resource id form, kept for the call sites that still pass R.string directly. Wrapping the id
    // in a TextRef is what lets this class live in commonMain: TextResolver has no gs(Int), and
    // TextRef.AndroidRes only carries the id, so nothing Android specific leaks in.
    override fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double =
        verifyHardLimits(value, rh.gs(TextRef.AndroidRes(valueName)), lowLimit, highLimit)

    /** Both public forms resolve their name first and share this, so the behaviour cannot drift. */
    private fun verifyHardLimits(value: Double, valueName: String, lowLimit: Double, highLimit: Double): Double {
        var newValue = value
        if (newValue !in lowLimit..highLimit) {
            newValue = max(newValue, lowLimit)
            newValue = min(newValue, highLimit)
            var msg = rh.gs(CoreUiStrings.valueoutofrange, valueName)
            msg += ".\n"
            msg += rh.gs(CoreUiStrings.valuelimitedto, value, newValue)
            aapsLogger.error(msg)
            rxBus.send(EventShowSnackbar(msg, EventShowSnackbar.Type.Warning))
            // One chime and a card. The alert core writes its announcement, on the master.
            CoreAlert.HARD_LIMIT.raiseEvent(alerts(), AlertSeverity.WARNING, TextRef.Literal(msg), dateUtil.now())
        }
        return newValue
    }

    override fun ageEntries() = arrayOf<CharSequence>(
        rh.gs(CoreUiStrings.child),
        rh.gs(CoreUiStrings.teenage),
        rh.gs(CoreUiStrings.adult),
        rh.gs(CoreUiStrings.resistant_adult),
        rh.gs(CoreUiStrings.pregnant),
    )

    override fun ageEntryValues() = arrayOf<CharSequence>(
        "child",
        "teenage",
        "adult",
        "resistantadult",
        "pregnant"
    )
}
