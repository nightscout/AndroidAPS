package app.aaps.core.objects.extensions

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.json.JSONArray

/**
 * `org.json` adapters for the schedule readers and writers in `BlockExtension`.
 *
 * The rules themselves live on kotlinx types in commonMain. Only these thin boundary adapters stay
 * Android-only, because `org.json` is part of the Android platform and has no iOS counterpart. They
 * go away with the callers that still hold `JSONArray`.
 */

/**
 * Bridge from `org.json` to kotlinx at the module boundary.
 *
 * Goes via text because that is the only lossless thing both libraries agree on, and the cost is
 * paid once per schedule rather than per entry. Returns null rather than throwing so callers keep the
 * existing "unreadable means invalid profile" behaviour.
 */
private fun JSONArray?.toKotlinxOrNull(): JsonArray? =
    this?.let { runCatching { Json.parseToJsonElement(it.toString()) as? JsonArray }.getOrNull() }

/** `org.json` entry point. Converts once at the boundary and delegates to [blockFromJson]. */
fun blockFromJsonArray(jsonArray: JSONArray?, dateUtil: DateUtil): List<Block>? =
    blockFromJson(jsonArray.toKotlinxOrNull(), dateUtil)

/** `org.json` entry point. Converts once at the boundary and delegates to [targetBlockFromJson]. */
fun targetBlockFromJsonArray(jsonArray1: JSONArray?, jsonArray2: JSONArray?, dateUtil: DateUtil): List<TargetBlock>? =
    targetBlockFromJson(jsonArray1.toKotlinxOrNull(), jsonArray2.toKotlinxOrNull(), dateUtil)

/** `org.json` adapter for [toJsonArray], mirroring [blockFromJsonArray] on the read side. */
fun List<Block>.toJSONArray(): JSONArray = JSONArray(toJsonArray().toString())

/** `org.json` adapter for [lowToJsonArray]. */
fun List<TargetBlock>.lowToJSONArray(): JSONArray = JSONArray(lowToJsonArray().toString())

/** `org.json` adapter for [highToJsonArray]. */
fun List<TargetBlock>.highToJSONArray(): JSONArray = JSONArray(highToJsonArray().toString())
