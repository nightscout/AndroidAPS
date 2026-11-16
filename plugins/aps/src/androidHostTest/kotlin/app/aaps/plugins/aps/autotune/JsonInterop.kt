package app.aaps.plugins.aps.autotune

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/*
 * The Autotune data classes read and write kotlinx JSON since they moved to common code. Their tests
 * were written with org.json and keep it for the input they build and the output they check, so only
 * these two conversions at the edge changed.
 */

internal fun JSONObject.toKotlinx(): JsonObject = Json.parseToJsonElement(toString()).jsonObject

internal fun JsonObject.toOrgJson(): JSONObject = JSONObject(toString())

internal fun parseJsonObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
