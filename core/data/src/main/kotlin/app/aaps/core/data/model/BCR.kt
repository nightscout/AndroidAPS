package app.aaps.core.data.model

import java.util.TimeZone

data class BCR(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var targetBGLow: Double,
    var targetBGHigh: Double,
    var isf: Double,
    var ic: Double,
    var bolusIOB: Double,
    var wasBolusIOBUsed: Boolean,
    var basalIOB: Double,
    var wasBasalIOBUsed: Boolean,
    var glucoseValue: Double,
    var wasGlucoseUsed: Boolean,
    var glucoseDifference: Double,
    var glucoseInsulin: Double,
    var glucoseTrend: Double,
    var wasTrendUsed: Boolean,
    var trendInsulin: Double,
    var cob: Double,
    var wasCOBUsed: Boolean,
    var cobInsulin: Double,
    var carbs: Double,
    var wereCarbsUsed: Boolean,
    var carbsInsulin: Double,
    var otherCorrection: Double,
    var wasSuperbolusUsed: Boolean,
    var superbolusInsulin: Double,
    var wasTempTargetUsed: Boolean,
    var totalInsulin: Double,
    var percentageCorrection: Int,
    var profileName: String,
    var note: String
) : HasIDs {

    fun contentEqualsTo(other: BCR): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            targetBGLow == other.targetBGLow &&
            targetBGHigh == other.targetBGHigh &&
            isf == other.isf &&
            ic == other.ic &&
            bolusIOB == other.bolusIOB &&
            wasBolusIOBUsed == other.wasBolusIOBUsed &&
            basalIOB == other.basalIOB &&
            wasBasalIOBUsed == other.wasBasalIOBUsed &&
            glucoseValue == other.glucoseValue &&
            wasGlucoseUsed == other.wasGlucoseUsed &&
            glucoseDifference == other.glucoseDifference &&
            glucoseInsulin == other.glucoseInsulin &&
            glucoseTrend == other.glucoseTrend &&
            wasTrendUsed == other.wasTrendUsed &&
            trendInsulin == other.trendInsulin &&
            cob == other.cob &&
            wasCOBUsed == other.wasCOBUsed &&
            cobInsulin == other.cobInsulin &&
            carbs == other.carbs &&
            wereCarbsUsed == other.wereCarbsUsed &&
            carbsInsulin == other.carbsInsulin &&
            otherCorrection == other.otherCorrection &&
            wasSuperbolusUsed == other.wasSuperbolusUsed &&
            superbolusInsulin == other.superbolusInsulin &&
            wasTempTargetUsed == other.wasTempTargetUsed &&
            totalInsulin == other.totalInsulin &&
            percentageCorrection == other.percentageCorrection &&
            profileName == other.profileName &&
            note == other.note

    fun onlyNsIdAdded(previous: BCR): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    companion object
}