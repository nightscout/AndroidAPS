package app.aaps.core.data.ue

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT

sealed class ValueWithUnit {          //I use a sealed class because of StringResource that contains a listOf as second parameter

    object UNKNOWN : ValueWithUnit() // formerly None used as fallback

    data class SimpleString(val value: String) : ValueWithUnit() // formerly one usage of None

    data class SimpleInt(val value: Int) : ValueWithUnit() // formerly one usage of None

    data class Mgdl(val value: Double) : ValueWithUnit()

    data class Mmoll(val value: Double) : ValueWithUnit()

    data class Timestamp(val value: Long) : ValueWithUnit()

    data class Insulin(val value: Double) : ValueWithUnit()

    data class InsulinConcentration(val value: Int) : ValueWithUnit() // formerly one usage of None

    data class UnitPerHour(val value: Double) : ValueWithUnit()

    data class Gram(val value: Int) : ValueWithUnit()

    data class Minute(val value: Int) : ValueWithUnit()

    data class Hour(val value: Int) : ValueWithUnit()

    data class Percent(val value: Int) : ValueWithUnit()

    data class TEType(val value: TE.Type) : ValueWithUnit()

    data class TEMeterType(val value: TE.MeterType) : ValueWithUnit()

    data class TELocation(val value: TE.Location) : ValueWithUnit()

    data class TEArrow(val value: TE.Arrow) : ValueWithUnit()

    data class TETTReason(val value: TT.Reason) : ValueWithUnit()

    data class RMMode(val value: RM.Mode) : ValueWithUnit()
    companion object {

        fun fromGlucoseUnit(value: Double, glucoseUnit: GlucoseUnit): ValueWithUnit =
            when (glucoseUnit) {
                GlucoseUnit.MGDL -> Mgdl(value)
                GlucoseUnit.MMOL -> Mmoll(value)
            }
    }
}
