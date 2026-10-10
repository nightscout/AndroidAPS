package app.aaps.plugins.constraints.versionChecker

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object AllowedVersions {

    fun findByApi(definition: JsonObject, api: Int): String? = definition.stringOrNull(api.toString())

    fun findByVersion(definition: JsonObject, version: String): String? = definition.stringOrNull(version)

    /**
     * Missing key -> null, matching what `JsonHelper.safeGetString` did.
     *
     * `content` rather than a string-only check on purpose: org.json's `getString` coerces a number
     * or boolean to its text, so filtering to string primitives would reject values the old reader
     * accepted.
     */
    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.content

    fun endDateToMilliseconds(endDate: String): Long? =
        try {
            val date = LocalDate.parse(endDate)
            date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }
}
