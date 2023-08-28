package info.nightscout.interfaces.aps

import info.nightscout.interfaces.iob.GlucoseStatus
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.iob.MealData
import info.nightscout.interfaces.profile.Profile

interface DetermineBasalAdapter {

    var currentTempParam: String?
    var iobDataParam: String?
    var glucoseStatusParam: String?
    var profileParam: String?
    var mealDataParam: String?
    var scriptDebug: String

    fun setData(
        profile: Profile,
        maxIob: Double,
        maxBasal: Double,
        minBg: Double,
        maxBg: Double,
        targetBg: Double,
        basalRate: Double,
        iobArray: Array<IobTotal>,
        glucoseStatus: GlucoseStatus,
        mealData: MealData,
        autosensDataRatio: Double,
        tempTargetSet: Boolean,
        microBolusAllowed: Boolean = false,
        uamAllowed: Boolean = false,
        advancedFiltering: Boolean = false,
        flatBGsDetected: Boolean = false,
        tdd1D: Double?,
        tdd7D: Double?,
        tddLast24H: Double?,
        tddLast4H: Double?,
        tddLast8to4H: Double?
    )

    operator fun invoke(): APSResult?
}