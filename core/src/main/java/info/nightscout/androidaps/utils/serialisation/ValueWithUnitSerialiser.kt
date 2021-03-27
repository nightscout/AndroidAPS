package info.nightscout.androidaps.utils.serialisation

import com.google.gson.reflect.TypeToken
import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import info.nightscout.androidaps.utils.ValueWithUnitWrapper

object ValueWithUnitSerialiser {

    fun toSealedClassJson(list: List<XXXValueWithUnit>): String =
        list.map { ValueWithUnitWrapper(it) }.let { SealedClassHelper.gson.toJson(it) }

    fun fromJson(string: String): List<XXXValueWithUnit> {
        val itemType = object : TypeToken<List<ValueWithUnitWrapper>>() {}.type

        return SealedClassHelper.gson.fromJson<List<ValueWithUnitWrapper>>(string, itemType).map { it.wrapped }
    }

}