package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.utils.JsonHelper
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.json.JSONObject

object AllowedVersions {

    fun findByApi(definition: JSONObject, api: Int): String? = JsonHelper.safeGetString(definition, api.toString())

    fun findByVersion(definition: JSONObject, version: String): String? = JsonHelper.safeGetString(definition, version)

    fun endDateToMilliseconds(endDate: String): Long? =
        try {
            val date = LocalDate.parse(endDate)
            date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }
}
