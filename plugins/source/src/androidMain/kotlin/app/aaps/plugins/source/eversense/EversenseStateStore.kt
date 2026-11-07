package app.aaps.plugins.source.eversense

import android.content.Context
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.source.keys.EversenseRuntimeKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Imports the whole legacy driver store once. New writes go through registered AAPS preferences. */
@SingleIn(AppScope::class)
@Inject
class EversenseStateStore(context: Context, private val preferences: Preferences) : EversenseStore {
    private val lock = Any()

    init {
        preferences.registerPreferences(EversenseRuntimeKey.entries)
        if (preferences.getIfExists(EversenseRuntimeKey.DeviceState) == null) {
            val legacy = context.getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE).all
            val imported = legacy.mapValues { (_, value) ->
                when (value) {
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Set<*> -> JsonArray(value.map { JsonPrimitive(it?.toString()) })
                    else -> JsonNull
                }
            }
            preferences.put(EversenseRuntimeKey.DeviceState, JsonObject(imported).toString())
        }
    }

    private fun read(): JsonObject = Json.parseToJsonElement(preferences.get(EversenseRuntimeKey.DeviceState)).jsonObject
    override fun getString(key: String, defaultValue: String?): String? = synchronized(lock) {
        if (key == StorageKeys.CLOUD_QUEUE) preferences.getIfExists(EversenseRuntimeKey.CloudQueue) ?: defaultValue
        else read()[key]?.jsonPrimitive?.contentOrNull ?: defaultValue
    }
    override fun getLong(key: String, defaultValue: Long): Long = synchronized(lock) { read()[key]?.jsonPrimitive?.longOrNull ?: defaultValue }
    override fun getInt(key: String, defaultValue: Int): Int = synchronized(lock) { read()[key]?.jsonPrimitive?.intOrNull ?: defaultValue }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = synchronized(lock) { read()[key]?.jsonPrimitive?.booleanOrNull ?: defaultValue }
    override fun contains(key: String): Boolean = synchronized(lock) {
        if (key == StorageKeys.CLOUD_QUEUE) preferences.getIfExists(EversenseRuntimeKey.CloudQueue) != null else key in read()
    }

    override fun edit(): EversenseStore.Editor = object : EversenseStore.Editor {
        private val changes = mutableMapOf<String, JsonElement?>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value?.let { JsonPrimitive(it) } }
        override fun putLong(key: String, value: Long) = apply { changes[key] = JsonPrimitive(value) }
        override fun putInt(key: String, value: Int) = apply { changes[key] = JsonPrimitive(value) }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = JsonPrimitive(value) }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean {
            synchronized(lock) {
                if (clearAll || changes.keys.any { it != StorageKeys.CLOUD_QUEUE }) {
                    val updated = if (clearAll) mutableMapOf() else read().toMutableMap()
                    changes.filterKeys { it != StorageKeys.CLOUD_QUEUE }.forEach { (key, value) ->
                        if (value == null) updated.remove(key) else updated[key] = value
                    }
                    preferences.put(EversenseRuntimeKey.DeviceState, JsonObject(updated).toString())
                }
                if (clearAll || StorageKeys.CLOUD_QUEUE in changes) {
                    preferences.put(EversenseRuntimeKey.CloudQueue,
                        changes[StorageKeys.CLOUD_QUEUE]?.jsonPrimitive?.contentOrNull ?: "[]")
                }
            }
            return true
        }
        override fun apply() { commit() }
    }
}
