package info.nightscout.androidaps.database.entities

import androidx.annotation.StringRes

sealed class XXXValueWithUnit {

    object UNKNOWN : XXXValueWithUnit() // formerly None used as fallback

    data class SimpleString(val value: String) : XXXValueWithUnit() // formerly one usage of None

    data class SimpleInt(val value: Int) : XXXValueWithUnit() // formerly one usage of None

    data class Mgdl(val value: Double) : XXXValueWithUnit()

    data class Mmoll(val value: Double) : XXXValueWithUnit()

    data class Timestamp(val value: Long) : XXXValueWithUnit()

    data class Insulin(val value: Double) : XXXValueWithUnit()

    data class UnitPerHour(val value: Double) : XXXValueWithUnit()

    data class Gram(val value: Int) : XXXValueWithUnit()

    data class Minute(val value: Int) : XXXValueWithUnit()

    data class Hour(val value: Int) : XXXValueWithUnit()

    data class Percent(val value: Int) : XXXValueWithUnit()

    data class TherapyEventType(val value: TherapyEvent.Type) : XXXValueWithUnit()

    data class TherapyEventMeterType(val value: TherapyEvent.MeterType) : XXXValueWithUnit()

    data class TherapyEventTTReason(val value: TemporaryTarget.Reason) : XXXValueWithUnit()

    data class StringResource(@StringRes val value: Int, val params: List<XXXValueWithUnit> = listOf()) : XXXValueWithUnit()

    fun value(): Any? {
        return when(this) {
            is Gram                  -> this.value
            is Hour                  -> this.value
            is Insulin               -> this.value
            is Mgdl                  -> this.value
            is Minute                -> this.value
            is Mmoll                 -> this.value
            is Percent               -> this.value
            is SimpleInt             -> this.value
            is SimpleString          -> this.value
            is StringResource        -> this.value
            is TherapyEventMeterType -> this.value
            is TherapyEventTTReason  -> this.value
            is TherapyEventType      -> this.value
            is Timestamp             -> this.value
            is UnitPerHour           -> this.value
            UNKNOWN                  -> null
        }
    }
    companion object {

        fun fromGlucoseUnit(value: Double, string: String): XXXValueWithUnit? = when (string) {
            "mg/dl", "mgdl" -> Mgdl(value)
            "mmol", "mmol/l" -> Mmoll(value)
            else             -> null
        }
    }
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
 * [x] generate presentation string
 * [ ] update fragment
 * [ ] generate csv
 *
 */

// just do develop in this file. Remove when done.
/*
interface Translator {

    fun translate(units: UserEntry.Units): String
    fun translate(meterType: TherapyEvent.MeterType): String
    fun translate(type: TherapyEvent.Type): String
    fun translate(reason: TemporaryTarget.Reason): String
}
*/
