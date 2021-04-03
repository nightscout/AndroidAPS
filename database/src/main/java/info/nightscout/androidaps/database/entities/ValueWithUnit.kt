package info.nightscout.androidaps.database.entities

import androidx.annotation.StringRes

sealed class ValueWithUnit {

    object UNKNOWN : ValueWithUnit() // formerly None used as fallback

    data class SimpleString(val value: String) : ValueWithUnit() // formerly one usage of None

    data class SimpleInt(val value: Int) : ValueWithUnit() // formerly one usage of None

    data class Mgdl(val value: Double) : ValueWithUnit()

    data class Mmoll(val value: Double) : ValueWithUnit()

    data class Timestamp(val value: Long) : ValueWithUnit()

    data class Insulin(val value: Double) : ValueWithUnit()

    data class UnitPerHour(val value: Double) : ValueWithUnit()

    data class Gram(val value: Int) : ValueWithUnit()

    data class Minute(val value: Int) : ValueWithUnit()

    data class Hour(val value: Int) : ValueWithUnit()

    data class Percent(val value: Int) : ValueWithUnit()

    data class TherapyEventType(val value: TherapyEvent.Type) : ValueWithUnit()

    data class TherapyEventMeterType(val value: TherapyEvent.MeterType) : ValueWithUnit()

    data class TherapyEventTTReason(val value: TemporaryTarget.Reason) : ValueWithUnit()

    data class StringResource(@StringRes val value: Int, val params: List<ValueWithUnit> = listOf()) : ValueWithUnit()

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

        fun fromGlucoseUnit(value: Double, string: String): ValueWithUnit? = when (string) {
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
