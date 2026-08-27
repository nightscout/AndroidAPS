package app.aaps.plugins.automation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Test-only conveniences for the kotlinx JSON that triggers and actions now speak.
 *
 * The factories take a [JsonObject], so tests that used to write `instantiate(JSONObject(text))`
 * write `instantiate(text.asJsonObject())` instead. JSONAssert keeps working untouched - it compares
 * JSON *strings* and does not care who produced them.
 */
fun String.asJsonObject(): JsonObject = Json.parseToJsonElement(this).jsonObject

/** The nested `data` block of a serialized trigger or action, i.e. what its `fromJSON` is handed. */
fun String.jsonData(): JsonObject = asJsonObject()["data"] as JsonObject
