package app.aaps.core.interfaces.utils

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** An observed device zone switch, with UTC offsets in seconds at that instant. */
@Serializable
data class TimeZoneChange(val timestamp: Long, val beforeSeconds: Int, val afterSeconds: Int)

/** Removes entries outside the past 24 hours from storage as well as from graph data. */
fun Preferences.readTimeZoneChanges(now: Long, logger: AAPSLogger): List<TimeZoneChange> =
    try {
        val history = Json.decodeFromString<List<TimeZoneChange>>(get(StringNonKey.TimeZoneChangeHistory))
        val recent = history.filter { it.timestamp in (now - 86_400_000L)..now }
        if (recent.size != history.size) put(StringNonKey.TimeZoneChangeHistory, Json.encodeToString(recent))
        recent
    } catch (e: IllegalArgumentException) {
        logger.error(LTag.CORE, "Could not read time zone change history", e)
        emptyList()
    }
