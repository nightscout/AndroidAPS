package app.aaps.ui.compose.profileManagement

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R
import java.text.DecimalFormat

/**
 * Pre-computed data for profile comparison (base vs effective, or any two profiles).
 * Contains both Profile objects (for graphs) and pre-built rows (for tables).
 */
data class ProfileCompareData(
    val baseProfile: Profile,
    val effectiveProfile: Profile,
    val basalRows: List<ProfileCompareRow>,
    val icRows: List<ProfileCompareRow>,
    val isfRows: List<ProfileCompareRow>,
    val targetRows: List<ProfileCompareRow>,
    val baseName: String,
    val effectiveName: String,
    val shortHourUnit: String,
    val icUnits: String,
    val isfUnits: String,
    val basalUnits: String,
    val targetUnits: String
)

/**
 * Build a complete [ProfileCompareData] from two profiles.
 * Shared by ProfileHelperScreen, ProfileViewerActivity, and ProfileManagementViewModel.
 */
internal fun buildProfileCompareData(
    profile1: Profile,
    profile2: Profile,
    profileName1: String,
    profileName2: String,
    rh: ResourceHelper,
    dateUtil: DateUtil,
    profileUtil: ProfileUtil,
    profileFunction: ProfileFunction
): ProfileCompareData {
    val units = profileFunction.getUnits()
    return ProfileCompareData(
        baseProfile = profile1,
        effectiveProfile = profile2,
        basalRows = buildBasalRows(profile1, profile2, dateUtil),
        icRows = buildIcRows(profile1, profile2, dateUtil),
        isfRows = buildIsfRows(profile1, profile2, profileUtil, dateUtil),
        targetRows = buildTargetRows(profile1, profile2, dateUtil, profileUtil),
        baseName = profileName1,
        effectiveName = profileName2,
        shortHourUnit = rh.gs(app.aaps.core.interfaces.R.string.shorthour),
        icUnits = rh.gs(R.string.profile_carbs_per_unit),
        isfUnits = "${units.asText} ${rh.gs(R.string.profile_per_unit)}",
        basalUnits = rh.gs(R.string.profile_ins_units_per_hour),
        targetUnits = units.asText
    )
}

internal fun buildBasalRows(profile1: Profile, profile2: Profile, dateUtil: DateUtil): List<ProfileCompareRow> {
    var prev1 = -1.0
    var prev2 = -1.0
    val rows = mutableListOf<ProfileCompareRow>()
    val formatter = DecimalFormat("0.00")
    for (hour in 0..23) {
        val val1 = profile1.getBasalTimeFromMidnight(hour * 60 * 60)
        val val2 = profile2.getBasalTimeFromMidnight(hour * 60 * 60)
        if (val1 != prev1 || val2 != prev2) rows.add(ProfileCompareRow(time = dateUtil.formatHHMM(hour * 60 * 60), value1 = formatter.format(val1), value2 = formatter.format(val2)))
        prev1 = val1; prev2 = val2
    }
    rows.add(ProfileCompareRow(time = "∑", value1 = formatter.format(profile1.percentageBasalSum()), value2 = formatter.format(profile2.percentageBasalSum())))
    return rows
}

internal fun buildIcRows(profile1: Profile, profile2: Profile, dateUtil: DateUtil): List<ProfileCompareRow> {
    var prev1 = -1.0
    var prev2 = -1.0
    val rows = mutableListOf<ProfileCompareRow>()
    val formatter = DecimalFormat("0.0")
    for (hour in 0..23) {
        val val1 = profile1.getIcTimeFromMidnight(hour * 60 * 60)
        val val2 = profile2.getIcTimeFromMidnight(hour * 60 * 60)
        if (val1 != prev1 || val2 != prev2) rows.add(ProfileCompareRow(time = dateUtil.formatHHMM(hour * 60 * 60), value1 = formatter.format(val1), value2 = formatter.format(val2)))
        prev1 = val1; prev2 = val2
    }
    return rows
}

internal fun buildIsfRows(profile1: Profile, profile2: Profile, profileUtil: ProfileUtil, dateUtil: DateUtil): List<ProfileCompareRow> {
    var prev1 = -1.0
    var prev2 = -1.0
    val rows = mutableListOf<ProfileCompareRow>()
    val formatter = DecimalFormat("0.0")
    val units = profile1.units
    for (hour in 0..23) {
        val val1 = profileUtil.fromMgdlToUnits(profile1.getIsfMgdlTimeFromMidnight(hour * 60 * 60), units)
        val val2 = profileUtil.fromMgdlToUnits(profile2.getIsfMgdlTimeFromMidnight(hour * 60 * 60), units)
        if (val1 != prev1 || val2 != prev2) rows.add(ProfileCompareRow(time = dateUtil.formatHHMM(hour * 60 * 60), value1 = formatter.format(val1), value2 = formatter.format(val2)))
        prev1 = val1; prev2 = val2
    }
    return rows
}

internal fun buildTargetRows(profile1: Profile, profile2: Profile, dateUtil: DateUtil, profileUtil: ProfileUtil): List<ProfileCompareRow> {
    var prev1l = -1.0
    var prev1h = -1.0
    var prev2l = -1.0
    var prev2h = -1.0
    val rows = mutableListOf<ProfileCompareRow>()
    val units = profile1.units
    val formatter = if (units == GlucoseUnit.MMOL) DecimalFormat("0.0") else DecimalFormat("0")
    for (hour in 0..23) {
        val val1l = profileUtil.fromMgdlToUnits(profile1.getTargetLowMgdlTimeFromMidnight(hour * 60 * 60), units)
        val val1h = profileUtil.fromMgdlToUnits(profile1.getTargetHighMgdlTimeFromMidnight(hour * 60 * 60), units)
        val val2l = profileUtil.fromMgdlToUnits(profile2.getTargetLowMgdlTimeFromMidnight(hour * 60 * 60), units)
        val val2h = profileUtil.fromMgdlToUnits(profile2.getTargetHighMgdlTimeFromMidnight(hour * 60 * 60), units)
        if (val1l != prev1l || val1h != prev1h || val2l != prev2l || val2h != prev2h) rows.add(
            ProfileCompareRow(
                time = dateUtil.formatHHMM(hour * 60 * 60),
                value1 = "${formatter.format(val1l)} - ${formatter.format(val1h)}",
                value2 = "${formatter.format(val2l)} - ${formatter.format(val2h)}"
            )
        )
        prev1l = val1l; prev1h = val1h; prev2l = val2l; prev2h = val2h
    }
    return rows
}
