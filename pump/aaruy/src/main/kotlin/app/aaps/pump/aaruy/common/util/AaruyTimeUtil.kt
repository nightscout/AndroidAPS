package app.aaps.pump.aaruy.common.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object AaruyTimeUtil {
    // 毫秒时间戳
    fun isToday(timestampMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val date = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()
        return date == LocalDate.now(zone)
    }
}