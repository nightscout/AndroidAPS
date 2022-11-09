package info.nightscout.database.impl.serialisation

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass

object SealedClassHelper {

    val gson: Gson = GsonBuilder().registerTypeAdapterFactory(
        object : TypeAdapterFactory {
            override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T> {
                val kClass = Reflection.getOrCreateKotlinClass(type.rawType)
                return if (kClass.sealedSubclasses.any()) {
                    SealedClassTypeAdapter(kClass, gson)
                } else
                    gson.getDelegateAdapter(this, type)
            }
        }).create()

    private class SealedClassTypeAdapter<T : Any>(private val kClass: KClass<Any>, val gson: Gson) : TypeAdapter<T>() {

        override fun read(jsonReader: JsonReader): T? {
            jsonReader.beginObject()
            val nextName = jsonReader.nextName()
            val innerClass = kClass.sealedSubclasses.firstOrNull { it.simpleName == nextName }
                ?: throw Exception("$nextName is not a child of the sealed class ${kClass.qualifiedName}")
            val x = gson.fromJson<T>(jsonReader, innerClass.javaObjectType)
            jsonReader.endObject()
            // if there a static object, actually return that
            @Suppress("UNCHECKED_CAST")
            return innerClass.objectInstance as T? ?: x
        }

        override fun write(out: JsonWriter, value: T) {
            val jsonString = gson.toJson(value)
            val name = value.javaClass.canonicalName
            if (name != null) {
                out.beginObject()
                out.name(name.splitToSequence(".").last()).jsonValue(jsonString)
                out.endObject()
            }
        }
    }
}

inline fun <reified T> Gson.fromJson(json: String): T = fromJson(json, object : TypeToken<T>() {}.type)
