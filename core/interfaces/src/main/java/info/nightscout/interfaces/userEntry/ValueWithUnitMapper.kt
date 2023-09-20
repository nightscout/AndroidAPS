package info.nightscout.interfaces.userEntry

import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit

sealed class ValueWithUnitMapper {          //I use a sealed class because of StringResource that contains a listOf as second parameter

    object UNKNOWN : ValueWithUnitMapper() // formerly None used as fallback

    data class SimpleString(val value: String) : ValueWithUnitMapper() // formerly one usage of None

    data class SimpleInt(val value: Int) : ValueWithUnitMapper() // formerly one usage of None

    data class Mgdl(val value: Double) : ValueWithUnitMapper()

    data class Mmoll(val value: Double) : ValueWithUnitMapper()

    data class Timestamp(val value: Long) : ValueWithUnitMapper()

    data class Insulin(val value: Double) : ValueWithUnitMapper()

    data class UnitPerHour(val value: Double) : ValueWithUnitMapper()

    data class Gram(val value: Int) : ValueWithUnitMapper()

    data class Minute(val value: Int) : ValueWithUnitMapper()

    data class Hour(val value: Int) : ValueWithUnitMapper()

    data class Percent(val value: Int) : ValueWithUnitMapper()

    data class TherapyEventType(val value: TherapyEvent.Type) : ValueWithUnitMapper()

    data class TherapyEventMeterType(val value: TherapyEvent.MeterType) : ValueWithUnitMapper()

    data class TherapyEventTTReason(val value: TemporaryTarget.Reason) : ValueWithUnitMapper()

    fun db(): ValueWithUnit? {
        return when(this) {
            is Gram                  -> ValueWithUnit.Gram(this.value)
            is Hour                  -> ValueWithUnit.Hour(this.value)
            is Insulin               -> ValueWithUnit.Insulin(this.value)
            is Mgdl                  -> ValueWithUnit.Mgdl(this.value)
            is Minute                -> ValueWithUnit.Minute(this.value)
            is Mmoll                 -> ValueWithUnit.Mmoll(this.value)
            is Percent               -> ValueWithUnit.Percent(this.value)
            is SimpleInt             -> ValueWithUnit.SimpleInt(this.value)
            is SimpleString          -> ValueWithUnit.SimpleString(this.value)
            is TherapyEventMeterType -> ValueWithUnit.TherapyEventMeterType(this.value)
            is TherapyEventTTReason  -> ValueWithUnit.TherapyEventTTReason(this.value)
            is TherapyEventType      -> ValueWithUnit.TherapyEventType(this.value)
            is Timestamp             -> ValueWithUnit.Timestamp(this.value)
            is UnitPerHour           -> ValueWithUnit.UnitPerHour(this.value)
            UNKNOWN                  -> null
        }
    }

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
            is TherapyEventMeterType -> this.value
            is TherapyEventTTReason  -> this.value
            is TherapyEventType      -> this.value
            is Timestamp             -> this.value
            is UnitPerHour           -> this.value
            UNKNOWN                  -> null
        }
    }

    companion object {

        fun fromGlucoseUnit(value: Double, string: String): ValueWithUnitMapper? = when (string) {
            GlucoseUnit.MGDL.asText, "mgdl"   -> Mgdl(value)
            GlucoseUnit.MMOL.asText, "mmol/l" -> Mmoll(value)
            else           -> null
        }
    }
}
