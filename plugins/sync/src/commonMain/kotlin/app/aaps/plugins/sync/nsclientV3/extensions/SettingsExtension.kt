package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Parse JSON with a `runningConfig` object into a [NSRunningConfiguration] for `RunningConfiguration.apply()`: the
 * signed text of a settings doc (`{ signedAt, runningConfig }`), or an old unsigned doc
 * (`{ schemaVersion: 1, runningConfig: <…> }`). Docs from Nightscout go through `MasterDocVerifier` first.
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
