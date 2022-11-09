package info.nightscout.androidaps.utils.serialisation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.serialisation.SealedClassHelper

object ValueWithUnitSerializer {

    fun toSealedClassJson(list: List<ValueWithUnit>): String = list.map(::ValueWithUnitWrapper)
        .let(SealedClassHelper.gson::toJson)

    fun fromJson(string: String): List<ValueWithUnit> = SealedClassHelper.gson
        .fromJson<List<ValueWithUnitWrapper>>(string).map { it.wrapped }

    private class ValueWithUnitWrapper(val wrapped: ValueWithUnit)
}

inline fun <reified T> Gson.fromJson(json: String): T = fromJson(json, object : TypeToken<T>() {}.type)
