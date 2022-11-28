package info.nightscout.pump.combov2

import info.nightscout.comboctl.main.BasalProfile
import info.nightscout.comboctl.main.NUM_COMBO_BASAL_PROFILE_FACTORS
import info.nightscout.interfaces.profile.Profile as AAPSProfile

// Utility extension functions for clearer conversion between
// ComboCtl units and AAPS units. ComboCtl uses integer-encoded
// decimals. For basal values, the last 3 digits of an integer
// make up the decimal, so for example, 1568 actually means
// 1.568 IU, and 419 means 0.419 IU etc. Similarly, for bolus
// values, the last digit makes up the decimal. 57 means 5.7 IU,
// 4 means 0.4 IU etc.
// To emphasize better that such a conversion is taking place,
// these extension functions are put in place.
internal fun Int.cctlBasalToIU() = this.toDouble() / 1000.0
internal fun Int.cctlBolusToIU() = this.toDouble() / 10.0
internal fun Double.iuToCctlBolus() = (this * 10.0).toInt()

fun AAPSProfile.toComboCtlBasalProfile(): BasalProfile {
    val factors = IntRange(0, NUM_COMBO_BASAL_PROFILE_FACTORS - 1).map { hour ->
        (this.getBasalTimeFromMidnight(hour * 60 * 60) * 1000.0).toInt()
    }
    return BasalProfile(factors)
}