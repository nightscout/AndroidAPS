package info.nightscout.androidaps.database.entities

import androidx.annotation.StringRes

sealed class XXXValueWithUnit {

    object UNKNOWN : XXXValueWithUnit() // formerly None used as fallback

    data class SimpleString(val value: String) : XXXValueWithUnit() // formerly one usage of None

    data class SimpleInt(val value: Int) : XXXValueWithUnit() // formerly one usage of None

    class Mgdl(val value: Double) : XXXValueWithUnit()

    class Mmoll(val value: Double) : XXXValueWithUnit()

    class Timestamp(val value: Long) : XXXValueWithUnit()

    class Insulin(val value: Double) : XXXValueWithUnit()

    class UnitPerHour(val value: Double) : XXXValueWithUnit()

    class Gram(val value: Int) : XXXValueWithUnit()

    class Minute(val value: Int) : XXXValueWithUnit()

    class Hour(val value: Int) : XXXValueWithUnit()

    class Percent(val value: Int) : XXXValueWithUnit()

    class TherapyEventType(val value: TherapyEvent.Type) : XXXValueWithUnit()

    class TherapyEventMeterType(val value: TherapyEvent.MeterType) : XXXValueWithUnit()

    class TherapyEventTTReason(val value: TemporaryTarget.Reason) : XXXValueWithUnit()

    class StringResource(@StringRes val value: Int, val params: List<XXXValueWithUnit> = listOf()) : XXXValueWithUnit()

    companion object {

        fun fromGlucoseUnit(value: Double, string: String): XXXValueWithUnit? = when (string) {
            "mg/dl", "mgdl" -> Mgdl(value)
            "mmol", "mmol/l" -> Mmoll(value)
            else             -> null
        }
    }
}

fun List<XXXValueWithUnit>.toPresentationString(translator: Translator) = concat

// TODO Move to destination module, then uncomment
fun XXXValueWithUnit.toPresentationString(translator: Translator) : String = when(this){
    is XXXValueWithUnit.Gram                  -> "$value ${translator.translate(UserEntry.Units.G)}"
    is XXXValueWithUnit.Hour                  -> "$value ${translator.translate(UserEntry.Units.H)}"
    is XXXValueWithUnit.Minute                -> "$value ${translator.translate(UserEntry.Units.G)}"
    is XXXValueWithUnit.Percent               -> "$value ${translator.translate(UserEntry.Units.Percent)}"
    is XXXValueWithUnit.Insulin               -> "" // DecimalFormatter.to2Decimal(value) + translator.translate(UserEntry.Units.U)
    is XXXValueWithUnit.UnitPerHour           -> "" // DecimalFormatter.to2Decimal(value) + translator.translate(UserEntry.Units.U_H)
    is XXXValueWithUnit.SimpleInt             -> value.toString()
    is XXXValueWithUnit.SimpleString          -> value
    is XXXValueWithUnit.StringResource -> "" //resourceHelper.gs(value, params.map { it.toPresentationString(translator) }) // recursively resolve params
    is XXXValueWithUnit.TherapyEventMeterType -> translator.translate(value)
    is XXXValueWithUnit.TherapyEventTTReason  -> translator.translate(value)
    is XXXValueWithUnit.TherapyEventType      -> translator.translate(value)
    is XXXValueWithUnit.Timestamp             -> "" // TODO dateUtil.dateAndTimeAndSecondsString(value)
    is XXXValueWithUnit.Mgdl -> {
        // if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(value) + translator.translate(UserEntry.Units.Mg_Dl)
        // else DecimalFormatter.to1Decimal(value/Constants.MMOLL_TO_MGDL) + translator.translate(UserEntry.Units.Mmol_L)
        ""
    }

    is XXXValueWithUnit.Mmoll -> {
        // if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(value) + translator.translate(UserEntry.Units.Mmol_L)
        // else DecimalFormatter.to1Decimal(value * Constants.MMOLL_TO_MGDL) + translator.translate(UserEntry.Units.Mg_Dl)
        ""
    }

    XXXValueWithUnit.UNKNOWN                  -> ""
}



/***
 * Idea: Leverage sealed classes for units
 * Advantage: it is clear what type of data a Unit contains. Still we are exhaustive on when
 *
 * The condition "condition" that is used to check if an item should be logged can be replaced by .takeIf { condition }.
 * The value then would not have to be handled but the logging could simply discard null value.
 *
 * [x] new sealed classes
 * [x] use entry type directly, not String
 * [ ] database
 * [ ] generate presentation string
 * [ ] update fragment
 * [ ] generate csv
 *
 */

// just do develop here
interface Translator {

    fun translate(action: UserEntry.Action): String
    fun translate(units: UserEntry.Units): String
    fun translate(meterType: TherapyEvent.MeterType): String
    fun translate(type: TherapyEvent.Type): String
    fun translate(reason: TemporaryTarget.Reason): String
}

/*
var valuesWithUnitString = ""
var rStringParam = 0
val separator = "  "
for(v in current.values) {
    if (rStringParam >0)
        rStringParam--
    else
        when (v.unit) {
            UserEntry.Units.Timestamp    -> valuesWithUnitString += dateUtil.dateAndTimeAndSecondsString(v.lValue) + separator
            UserEntry.Units.TherapyEvent -> valuesWithUnitString += translator.translate(v.sValue) + separator
            UserEntry.Units.R_String     -> {
                rStringParam = v.lValue.toInt()
                when (rStringParam) {   //
                    0 -> valuesWithUnitString += resourceHelper.gs(v.iValue) + separator
                    1 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value()) + separator
                    2 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value(), current.values[current.values.indexOf(v)+2].value()) + separator
                    3 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value(), current.values[current.values.indexOf(v)+2].value(), current.values[current.values.indexOf(v)+3].value()) + separator
                    4 -> rStringParam = 0
                }
            }
            UserEntry.Units.Mg_Dl        -> valuesWithUnitString += if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue) + translator.translate(UserEntry.Units.Mg_Dl) + separator else DecimalFormatter.to1Decimal(v.dValue/Constants.MMOLL_TO_MGDL) + translator.translate(UserEntry.Units.Mmol_L) + separator
            UserEntry.Units.Mmol_L       -> valuesWithUnitString += if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue*Constants.MMOLL_TO_MGDL) + translator.translate(UserEntry.Units.Mg_Dl) + separator else DecimalFormatter.to1Decimal(v.dValue) + translator.translate(UserEntry.Units.Mmol_L) + separator
            UserEntry.Units.U_H, UserEntry.Units.U
                                         -> valuesWithUnitString += DecimalFormatter.to2Decimal(v.dValue) + translator.translate(v.unit) + separator
            UserEntry.Units.G, UserEntry.Units.M, UserEntry.Units.H, UserEntry.Units.Percent
                                         -> valuesWithUnitString += v.iValue.toString() + translator.translate(v.unit) + separator
            else                         -> valuesWithUnitString += if (v.iValue != 0 || v.sValue != "") { v.value().toString() + separator } else ""
        }
}*/
