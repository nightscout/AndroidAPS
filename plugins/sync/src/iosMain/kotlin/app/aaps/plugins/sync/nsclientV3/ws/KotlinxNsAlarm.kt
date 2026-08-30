package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.nsclient.NSAlarm
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * An [NSAlarm] read from a kotlinx document.
 *
 * The Android counterpart, `NSAlarmObject`, reads the same fields out of `org.json`. Only the reader
 * differs - the field names, the defaults and the three `eventName` tests below are the Nightscout
 * alarm format and are the same on both.
 *
 * A missing or malformed field falls back rather than throwing: an alarm that failed to parse would
 * be an alarm that never sounds, which is worse than one shown with a placeholder title.
 */
class KotlinxNsAlarm(private val data: JsonObject) : NSAlarm {

    override val level: Int get() = int("level", 0)
    override val group: String get() = string("group", "N/A")
    override val title: String get() = string("title", "N/A")
    override val message: String get() = string("message", "N/A")

    override val low: Boolean get() = eventName == "low"
    override val high: Boolean get() = eventName == "high"
    override val timeAgo: Boolean get() = eventName == "timeago"

    private val eventName: String get() = string("eventName", "")

    private fun string(key: String, fallback: String): String =
        (data[key]?.jsonPrimitive?.contentOrNull) ?: fallback

    private fun int(key: String, fallback: Int): Int =
        data[key]?.jsonPrimitive?.intOrNull ?: fallback
}
