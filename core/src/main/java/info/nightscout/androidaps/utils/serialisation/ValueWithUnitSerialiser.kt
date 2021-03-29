package info.nightscout.androidaps.utils.serialisation

import info.nightscout.androidaps.database.entities.XXXValueWithUnit

object ValueWithUnitSerialiser {

    fun toSealedClassJson(list: List<XXXValueWithUnit>): String = list.map(::ValueWithUnitWrapper)
        .let(SealedClassHelper.gson::toJson)

    fun fromJson(string: String): List<XXXValueWithUnit> = SealedClassHelper.gson
        .fromJson<List<ValueWithUnitWrapper>>(string).map { it.wrapped }

    private class ValueWithUnitWrapper(val wrapped: XXXValueWithUnit)
}