package app.aaps.plugins.eversense.storage

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.keys.EversenseRuntimeKey
import app.aaps.plugins.eversense.util.StorageKeys
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
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The store of the first Eversense driver, before it moved into the AAPS preferences: the Android SharedPreferences
 * "EversenseCGMManager". Read once, to import it. Empty where it never existed (iOS).
 */
fun interface EversenseLegacyStore {

    fun read(): Map<String, Any?>
}

/**
 * The driver's state as one JSON object in a registered AAPS preference, and the cloud queue in its own preference.
 * Imports the whole legacy driver store once.
 *
 * The driver, the cloud upload and the pairing write from different threads; a write is a read, a change and a write
 * of the whole object, so the store lets one through at a time.
 */
@SingleIn(AppScope::class)
@Inject
class EversenseStateStore(legacy: EversenseLegacyStore, private val preferences: Preferences) : EversenseStore {

    @OptIn(ExperimentalAtomicApi::class)
    private val busy = AtomicBoolean(false)

    /** A short lock: what it guards is a JSON parse and a preference write, never a wait. */
    @OptIn(ExperimentalAtomicApi::class)
    private inline fun <T> locked(block: () -> T): T {
        while (!busy.compareAndSet(expectedValue = false, newValue = true)) Unit
        try {
            return block()
        } finally {
            busy.store(false)
        }
    }

    init {
        preferences.registerPreferences(EversenseRuntimeKey.entries)
        if (preferences.getIfExists(EversenseRuntimeKey.DeviceState) == null) {
            val imported = legacy.read().mapValues { (_, value) ->
                when (value) {
                    is String  -> JsonPrimitive(value)
                    is Number  -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Set<*>  -> JsonArray(value.map { JsonPrimitive(it?.toString()) })
                    else       -> JsonNull
                }
            }
            preferences.put(EversenseRuntimeKey.DeviceState, JsonObject(imported).toString())
        }
    }

    private fun read(): JsonObject = Json.parseToJsonElement(preferences.get(EversenseRuntimeKey.DeviceState)).jsonObject

    override fun getString(key: String, defaultValue: String?): String? = locked {
        if (key == StorageKeys.CLOUD_QUEUE) preferences.getIfExists(EversenseRuntimeKey.CloudQueue) ?: defaultValue
        else read()[key]?.jsonPrimitive?.contentOrNull ?: defaultValue
    }

    override fun getLong(key: String, defaultValue: Long): Long = locked { read()[key]?.jsonPrimitive?.longOrNull ?: defaultValue }
    override fun getInt(key: String, defaultValue: Int): Int = locked { read()[key]?.jsonPrimitive?.intOrNull ?: defaultValue }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = locked { read()[key]?.jsonPrimitive?.booleanOrNull ?: defaultValue }
    override fun contains(key: String): Boolean = locked {
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
            locked {
                if (clearAll || changes.keys.any { it != StorageKeys.CLOUD_QUEUE }) {
                    val updated = if (clearAll) mutableMapOf() else read().toMutableMap()
                    changes.filterKeys { it != StorageKeys.CLOUD_QUEUE }.forEach { (key, value) ->
                        if (value == null) updated.remove(key) else updated[key] = value
                    }
                    preferences.put(EversenseRuntimeKey.DeviceState, JsonObject(updated).toString())
                }
                if (clearAll || StorageKeys.CLOUD_QUEUE in changes) {
                    preferences.put(
                        EversenseRuntimeKey.CloudQueue,
                        changes[StorageKeys.CLOUD_QUEUE]?.jsonPrimitive?.contentOrNull ?: "[]"
                    )
                }
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
