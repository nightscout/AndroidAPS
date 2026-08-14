package app.aaps.core.interfaces.aps

import app.aaps.core.data.model.PS
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.EffectiveProfile

interface Sensitivity {

    enum class SensitivityType(val value: Int) {
        UNKNOWN(-1),
        SENSITIVITY_AAPS(0),
        SENSITIVITY_WEIGHTED(1),
        SENSITIVITY_OREF1(2);

        companion object {

            private val map = entries.associateBy(SensitivityType::value)
            fun fromInt(type: Int) = map[type]
        }
    }

    val id: SensitivityType

    /**
     * Detect insulin sensitivity from the autosens data.
     *
     * [profile], [siteChanges] and [profileSwitches] are passed in by the caller instead of being read
     * inside this call. When sensitivity is computed for every data point of a long history, these three
     * inputs depend only on the fixed detection start, so reading them once in the caller avoids repeating
     * the same profile lookup and database queries for every point.
     *
     * @param profile the current profile, or null when none is available (result is the neutral default)
     * @param siteChanges cannula changes at or after the detection start (reset deviations on a site change)
     * @param profileSwitches profile switches at or after the detection start (reset deviations on a switch)
     */
    fun detectSensitivity(
        ads: AutosensDataStore,
        fromTime: Long,
        toTime: Long,
        profile: EffectiveProfile?,
        siteChanges: List<TE>,
        profileSwitches: List<PS>
    ): AutosensResult
    fun maxAbsorptionHours(): Double

    val isMinCarbsAbsorptionDynamic: Boolean
    val isOref1: Boolean

    companion object {

        const val MIN_HOURS = 1.0
        const val MIN_HOURS_FULL_AUTOSENS = 4.0
    }
}