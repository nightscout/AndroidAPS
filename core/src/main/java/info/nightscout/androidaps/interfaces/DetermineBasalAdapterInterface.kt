package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus

interface DetermineBasalAdapterInterface {

    var currentTempParam: String?
    var iobDataParam: String?
    var glucoseStatusParam: String?
    var profileParam: String?
    var mealDataParam: String?
    var scriptDebug: String

    fun setData(profile: Profile,
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
                isSaveCgmSource: Boolean = false
    ) {}

    operator fun invoke(): APSResult?
}