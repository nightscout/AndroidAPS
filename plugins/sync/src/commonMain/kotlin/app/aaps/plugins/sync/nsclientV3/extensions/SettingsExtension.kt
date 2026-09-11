package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Parse a NS `settings` doc with shape `{ schemaVersion: 1, runningConfig: <…> }` into a
 * [NSRunningConfiguration] for `RunningConfiguration.apply()`.
 *
 * Returns null when the doc is missing `runningConfig`, the JSON is malformed, or
 * the `runningConfig` block fails to deserialize into [NSRunningConfiguration].
 */
fun String.toRunningConfiguration(): NSRunningConfiguration? = runCatching {
    val running = lenientJson.parseToJsonElement(this).jsonObject["runningConfig"] as? JsonObject
        ?: return@runCatching null
    lenientJson.decodeFromString<NSRunningConfiguration>(running.toString())
}.getOrNull()

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
