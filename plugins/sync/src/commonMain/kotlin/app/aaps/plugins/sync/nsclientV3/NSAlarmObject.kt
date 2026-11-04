package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.interfaces.nsclient.NSAlarm
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A Nightscout alarm payload, read field by field.
 *
 * ```
 * {
 * "level":2,
 * "title":"Urgent HIGH",
 * "message":"BG Now: 5.2 -0.1 → mmol\/L\nIOB: 0.00U\nCOB: 0g",
 * "eventName":"high",
 * "plugin":{"name":"simplealarms","label":"Simple Alarms","pluginType":"notification","enabled":true},
 * "pushoverSound":"persistent",
 * "debug":{"lastSGV":5.2,"thresholds":{"bgHigh":80,"bgTargetTop":75,"bgTargetBottom":72,"bgLow":70}},
 * "group":"default",
 * "key":"simplealarms_2"
 * }
 * ```
 *
 * Deliberately a reader over the tree rather than an `@Serializable` class. A strict decoder would
 * throw on a field of the wrong type, and this parses whatever a Nightscout server sends: an alarm
 * that today degrades to "N/A" would instead fail to fire, which is the wrong way round for an urgent
 * high. It also means the four keys we ignore need no declaration.
 *
 * The accessors reproduce `JsonHelper.safeGetInt` / `safeGetString` exactly, including the coercions -
 * see `NSAlarmImplTest`, which pins them against the `org.json` original.
 */
class NSAlarmObject(private val data: JsonObject) : NSAlarm {

    override val level: Int get() = data.intOr("level", 0)
    override val group: String get() = data.stringOr("group", "N/A")
    override val title: String get() = data.stringOr("title", "N/A")
    override val message: String get() = data.stringOr("message", "N/A")
    override val low: Boolean get() = data.stringOr("eventName", "") == "low"
    override val high: Boolean get() = data.stringOr("eventName", "") == "high"
    override val timeAgo: Boolean get() = data.stringOr("eventName", "") == "timeago"
}

/**
 * As `org.json`'s `getString`: a number or boolean becomes its text, and an object or array becomes
 * its json text rather than falling back. Only a missing key or an explicit null gives [default].
 */
private fun JsonObject.stringOr(key: String, default: String): String {
    val element = this[key] ?: return default
    if (element is JsonNull) return default
    return if (element is JsonPrimitive) element.content else element.toString()
}

/**
 * As `org.json`'s `getInt`: parses a number sent as a string, truncates a decimal, and falls back for
 * anything that is not a single value.
 */
private fun JsonObject.intOr(key: String, default: Int): Int {
    val element = this[key]
    if (element !is JsonPrimitive || element is JsonNull) return default
    return element.content.toIntOrNull() ?: element.content.toDoubleOrNull()?.toInt() ?: default
}
