package info.nightscout.androidaps.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass

fun generateGson() = GsonBuilder().registerTypeAdapterFactory(
    object : TypeAdapterFactory {
        override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T> {
            val kclass = Reflection.getOrCreateKotlinClass(type.rawType)
            return if (kclass.sealedSubclasses.any()) {
                SealedClassTypeAdapter<T>(kclass, gson)
            } else
                gson.getDelegateAdapter(this, type)
        }
    }).create()

/*fun testSealedClass(): XXXValueWithUnit.SimpleString? {

    val gson = generateGson()
    val someValueWithUnit: Container = Container(XXXValueWithUnit.SimpleString("hello"))
    val serialised = gson.toJson(someValueWithUnit)

    val deserialised : Container = gson.fromJson(serialised, Container::class.java)

    return deserialised.c as? XXXValueWithUnit.SimpleString

}*/

fun toSealedClassJson(list: List<XXXValueWithUnit>): String =
    list.map { Container(it) }.let { generateGson().toJson(it) }

fun fromJson(string: String) : List<XXXValueWithUnit> {
    val itemType = object : TypeToken<List<Container>>() {}.type

    return generateGson().fromJson<List<Container>>(string, itemType).map { it.wrapped }
}

data class Container(val wrapped: XXXValueWithUnit)

class SealedClassTypeAdapter<T : Any>(private val kclass: KClass<Any>, val gson: Gson) : TypeAdapter<T>() {

    override fun read(jsonReader: JsonReader): T? {
        jsonReader.beginObject() //start reading the object
        val nextName = jsonReader.nextName() //get the name on the object
        val innerClass = kclass.sealedSubclasses.firstOrNull {
            it.simpleName!!.contains(nextName)
        }
            ?: throw Exception("$nextName is not found to be a data class of the sealed class ${kclass.qualifiedName}")
        val x = gson.fromJson<T>(jsonReader, innerClass.javaObjectType)
        jsonReader.endObject()
        //if there a static object, actually return that back to ensure equality and such!
        return innerClass.objectInstance as T? ?: x
    }

    override fun write(out: JsonWriter, value: T) {
        val jsonString = gson.toJson(value)
        out.beginObject()
        val name = value.javaClass.canonicalName
        out.name(name.splitToSequence(".").last()).jsonValue(jsonString)
        out.endObject()
    }

}