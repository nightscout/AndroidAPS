package app.aaps.implementation.utils

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
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
    // A provider: the notifications reach much of the app, and are needed only when a limit is hit or ends
    private val notificationManager: () -> NotificationManager,
    private val preferences: Preferences,
    private val rh: TextResolver
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
        value == limit(value, rh.gs(TextRef.AndroidRes(valueName)), lowLimit, highLimit, loopStops = true)

    override fun checkHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Boolean =
        value == limit(value, rh.gs(valueName), lowLimit, highLimit, loopStops = true)

    override fun verifyHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Double =
        limit(value, rh.gs(valueName), lowLimit, highLimit, loopStops = false)

    // The resource id form, kept for the call sites that still pass R.string directly. Wrapping the id
    // in a TextRef is what lets this class live in commonMain: TextResolver has no gs(Int), and
    // TextRef.AndroidRes only carries the id, so nothing Android specific leaks in.
    override fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double =
        limit(value, rh.gs(TextRef.AndroidRes(valueName)), lowLimit, highLimit, loopStops = false)

    /** What is outside the limits now, by the name of the value: the loop stops for it, or it is limited. */
    private val stopping = mutableMapOf<String, String>()
    private val limited = mutableMapOf<String, String>()
    private val lock = AapsLock()

    /**
     * Both public forms resolve their name first and share this, so the behaviour cannot drift.
     *
     * A value outside the limits is a setting the user just changed in almost every case (patient age, profile, profile
     * switch, imported settings), so it is a card, not an alarm (decided 2026-11-26). The loop stops on a check
     * ([loopStops]): a red card with no sound, which says what to change. A value the caller limits and goes on with (a
     * target): a plain card. The loop checks its values at every run, so a card lasts while one of its values is
     * outside and goes by itself when they are all back. A card is posted again only when its text changes: once per
     * cause, not at every run.
     */
    private fun limit(value: Double, valueName: String, lowLimit: Double, highLimit: Double, loopStops: Boolean): Double {
        val newValue = min(max(value, lowLimit), highLimit)
        val message = when {
            newValue == value -> null
            loopStops         -> rh.gs(CoreUiStrings.hard_limit_loop_stopped, valueName, value, lowLimit, highLimit)
            else              -> rh.gs(CoreUiStrings.hard_limit_value_limited, valueName, value, newValue)
        }
        val problems = if (loopStops) stopping else limited
        val changed = lock.withLock {
            val before = problems[valueName]
            if (message == null) problems.remove(valueName) else problems[valueName] = message
            if (message == before) null else problems.values.toList()
        }
        if (changed != null) {
            message?.let { aapsLogger.error(LTag.CORE, it) }
            val id = if (loopStops) NotificationId.HARD_LIMIT_LOOP_STOPPED else NotificationId.HARD_LIMIT_VALUE_LIMITED
            // Each its own sentence, one under the other
            if (changed.isEmpty()) notificationManager().dismiss(id) else notificationManager().post(id, changed.joinToString("\n"))
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
