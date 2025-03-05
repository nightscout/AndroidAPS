package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.utils.JsonHelper
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AllowedVersions {

    fun findByApi(definition: JSONObject, api: Int): String? = JsonHelper.safeGetString(definition, api.toString())

    fun findByVersion(definition: JSONObject, version: String): String? = JsonHelper.safeGetString(definition, version)

    fun endDateToMilliseconds(endDate: String): Long? =
        try {
            val date = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            LocalDateTime.of(date, LocalTime.of(0, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
}
