package info.nightscout.androidaps.utils.serialisation

import info.nightscout.androidaps.database.entities.ValueWithUnit

object ValueWithUnitSerialiser {

    fun toSealedClassJson(list: List<ValueWithUnit>): String = list.map(::ValueWithUnitWrapper)
        .let(SealedClassHelper.gson::toJson)

    fun fromJson(string: String): List<ValueWithUnit> = SealedClassHelper.gson
        .fromJson<List<ValueWithUnitWrapper>>(string).map { it.wrapped }

    private class ValueWithUnitWrapper(val wrapped: ValueWithUnit)
}