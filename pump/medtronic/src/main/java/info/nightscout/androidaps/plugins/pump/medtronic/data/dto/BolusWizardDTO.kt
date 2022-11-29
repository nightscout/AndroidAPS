package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.core.utils.DateTimeUtil
import java.util.Locale

/**
 * Created by andy on 18.05.15.
 */
class BolusWizardDTO : PumpTimeStampedRecord() {

    // bloodGlucose and bgTarets are in mg/dL
    var bloodGlucose = 0 // mg/dL
    var carbs = 0
    var chUnit = "g"
    var carbRatio = 0.0f
    var insulinSensitivity = 0.0f
    var bgTargetLow = 0
    var bgTargetHigh = 0
    var bolusTotal = 0.0f
    var correctionEstimate = 0.0f
    var foodEstimate = 0.0f
    var unabsorbedInsulin = 0.0f//

    val value: String
        get() = String.format(Locale.ENGLISH, "BG=%d;CH=%d;CH_UNIT=%s;CH_INS_RATIO=%5.3f;BG_INS_RATIO=%5.3f;"
            + "BG_TARGET_LOW=%d;BG_TARGET_HIGH=%d;BOLUS_TOTAL=%5.3f;"
            + "BOLUS_CORRECTION=%5.3f;BOLUS_FOOD=%5.3f;UNABSORBED_INSULIN=%5.3f",  //
            bloodGlucose, carbs, chUnit, carbRatio, insulinSensitivity, bgTargetLow,  //
            bgTargetHigh, bolusTotal, correctionEstimate, foodEstimate, unabsorbedInsulin)

    //
    //
    val displayableValue: String
        get() = String.format(Locale.ENGLISH, "Bg=%d, CH=%d %s, Ch/Ins Ratio=%5.3f, Bg/Ins Ratio=%5.3f;"
            + "Bg Target(L/H)=%d/%d, Bolus: Total=%5.3f, "
            + "Correction=%5.3f, Food=%5.3f, IOB=%5.3f",  //
            bloodGlucose, carbs, chUnit, carbRatio, insulinSensitivity, bgTargetLow,  //
            bgTargetHigh, bolusTotal, correctionEstimate, foodEstimate, unabsorbedInsulin)

    override fun toString(): String {
        return "BolusWizardDTO [dateTime=" + DateTimeUtil.toString(atechDateTime) + ", " + value + "]"
    }
}
