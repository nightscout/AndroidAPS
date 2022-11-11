package info.nightscout.database.entities

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

    data class OfflineEventReason(val value: OfflineEvent.Reason) : ValueWithUnit()

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
            is OfflineEventReason    -> this.value
            is TherapyEventType      -> this.value
            is Timestamp             -> this.value
            is UnitPerHour           -> this.value
            UNKNOWN                  -> null
        }
    }
    companion object {

        const val MGDL = "mg/dl" // This is Nightscout's representation
        const val MMOL = "mmol"

        fun fromGlucoseUnit(value: Double, string: String): ValueWithUnit? = when (string) {
            MGDL, "mgdl"   -> Mgdl(value)
            MMOL, "mmol/l" -> Mmoll(value)
            else           -> null
        }
    }
}
