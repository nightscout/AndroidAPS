package app.aaps.core.objects.extensions

import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

/**
 * `org.json` entry point for [pureProfileFromJson], kept while the callers still hold a `JSONObject`.
 *
 * The conversion goes via text because that is the only lossless thing both libraries agree on, and
 * it is paid once per profile rather than per entry. A document `org.json` cannot even render, or one
 * kotlinx refuses to parse, becomes null - an invalid profile - which is exactly what the callers
 * already expect from an unreadable profile.
 */
fun pureProfileFromJson(jsonObject: JSONObject, dateUtil: DateUtil, defaultUnits: String? = null): PureProfile? {
    val parsed = runCatching { Json.parseToJsonElement(jsonObject.toString()) as? JsonObject }.getOrNull() ?: return null
    return pureProfileFromJson(parsed, dateUtil, defaultUnits)
}
