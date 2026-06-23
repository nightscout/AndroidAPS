package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Calendar
import java.util.TimeZone

/**
 * Shared little-endian / base64 encoders for the Eversense DMS upload protocol.
 *
 * These functions were previously duplicated, byte-for-byte, inside both
 * [EversenseHttp365Util] and [EversenseHttpE3Util]. Keeping two private copies meant a
 * protocol correction or bug fix had to be made in two places, and the E3 and 365 upload
 * paths could silently diverge. They now live here as a single source of truth.
 *
 * All functions are pure (no shared mutable state), so they are safe to call from any
 * thread. Their exact output is pinned by EversenseDmsBinaryCodecTest.
 */
internal object EversenseDmsBinaryCodec {

    fun int16LE(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    fun int24LE(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte())

    fun int32LE(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())

    /** Encode a UTC timestamp as the 2-byte date format used by the Eversense transmitter binary protocol. */
    fun calcDateBytes(tsMs: Long): ByteArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.timeInMillis = tsMs
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1 // 1-12
        val day = cal.get(Calendar.DAY_OF_MONTH)
        var b1 = (year - 2000) shl 1
        if (month > 7) b1 += 1
        val b0 = ((month and 7) shl 5) or day
        return byteArrayOf(b0.toByte(), b1.toByte())
    }

    /** Encode a UTC timestamp as the 2-byte time format used by the Eversense transmitter binary protocol. */
    fun calcTimeBytes(tsMs: Long): ByteArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.timeInMillis = tsMs
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val b0 = ((minute and 7) shl 5) or (second / 2)
        val b1 = (hour shl 3) or ((minute and 56) shr 3)
        return byteArrayOf(b0.toByte(), b1.toByte())
    }

    /**
     * Build the sgBytes base64 blob from a list of glucose readings.
     * Format: header (8C 00 01 00 00 + 3-byte LE count) followed by one record per reading.
     * Raw sensor ADC values are zeroed — only glucose, timestamp, and sensor ID are populated.
     */
    fun buildSgBytes(readings: List<EversenseCGMResult>): String {
        val baos = ByteArrayOutputStream()
        // Header: 8C 00 01 00 00 + count (3 bytes LE)
        baos.write(byteArrayOf(0x8C.toByte(), 0x00, 0x01, 0x00, 0x00))
        baos.write(int24LE(readings.size))
        readings.forEachIndexed { idx, r ->
            val sensorIdBytes = if (r.sensorId.isNotEmpty())
                r.sensorId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            else ByteArray(10)
            baos.write(int24LE(idx + 1))              // record number (1-based)
            baos.write(calcDateBytes(r.datetime))      // date (2 bytes)
            baos.write(calcTimeBytes(r.datetime))      // time (2 bytes)
            baos.write(int16LE(r.glucoseInMgDl))       // glucose (2 bytes LE)
            baos.write(0x00)                           // padding
            baos.write(sensorIdBytes)                  // sensor ID bytes
            // RAW_DATA_INDEX 1, 2, 3, 7, 8 — zeroed (no raw ADC data available)
            repeat(5) { baos.write(int16LE(0)) }
            // Accel values (2 bytes) — zeroed
            baos.write(int16LE(0))
            // AccelTemp (1 byte) — zeroed
            baos.write(0x00)
            // RAW_DATA_INDEX 4, 5, 6 — zeroed
            repeat(3) { baos.write(int16LE(0)) }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    /** Build mgBytes with actual calibration records for DMS upload. */
    fun buildMgBytes(calibrations: List<CalibrationHistoryItem>): String {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x98.toByte(), 0x01, 0x00))
        baos.write(int16LE(calibrations.size))
        baos.write(0x00)
        calibrations.forEachIndexed { idx, c ->
            baos.write(int16LE(idx + 1))            // record number (2 bytes)
            baos.write(calcDateBytes(c.datetime))   // date (2)
            baos.write(calcTimeBytes(c.datetime))   // time (2)
            baos.write(int16LE(c.glucoseInMgDl))    // glucose (2)
            baos.write(int16LE(0))                  // padding (2)
            baos.write(byteArrayOf(1, 0, 0, 0))     // custom field (4)
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    /** Build the mgBytes base64 blob with zero BGM/calibration records. */
    fun buildEmptyMgBytes(): String {
        // Header: 98 01 00 + count(2 bytes LE) + 00  → 0 records
        val bytes = byteArrayOf(0x98.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Build the patientBytes base64 blob with zero patient event records. */
    fun buildEmptyPatientBytes(): String {
        // Header: 9E 01 00 + count(2 bytes LE)  → 0 events
        val bytes = byteArrayOf(0x9E.toByte(), 0x01, 0x00, 0x00, 0x00)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Build the alertBytes base64 blob with zero alert records.
     * The header includes the raw sensor ID bytes followed by a zero-count field.
     */
    fun buildAlertBytes(sensorId: String, alerts: List<app.aaps.plugins.eversense.models.ActiveAlarm> = emptyList(), datetime: Long = 0, glucoseInMgDl: Int = 0): String {
        // Header: 93 01 00 + count(2 bytes LE) + sensorIdBytes + 00  → 0 alerts
        val uploadable = alerts.filter { it.code.dmsCode != 255 }
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x93.toByte(), 0x01, 0x00))
        baos.write(int16LE(uploadable.size))
        if (sensorId.isNotEmpty()) {
            val sensorIdBytes = sensorId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            baos.write(sensorIdBytes)
        }
        baos.write(0x00)
        uploadable.forEachIndexed { idx, a ->
            baos.write(int16LE(idx + 1))
            baos.write(calcDateBytes(datetime))
            baos.write(calcTimeBytes(datetime))
            baos.write(a.code.dmsCode)
            baos.write(int16LE(glucoseInMgDl))
            baos.write(int16LE(0))
            baos.write(int16LE(0))
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    fun signalStrengthOrdinal(percent: Int): Int = when {
        percent >= 75 -> 5
        percent >= 48 -> 4
        percent >= 30 -> 3
        percent >= 28 -> 2
        percent >= 25 -> 1
        else          -> 0
    }

    // Map EversenseTrendArrow to Eversense ARROW_TYPE ordinal (from decompiled app)
    // STALE=0, FALLING_FAST=1, FALLING=2, FLAT=3, RISING=4, RISING_FAST=5
    fun trendOrdinal(trend: EversenseTrendArrow): Int = when (trend) {
        EversenseTrendArrow.NONE            -> 0
        EversenseTrendArrow.SINGLE_DOWN     -> 1
        EversenseTrendArrow.FORTY_FIVE_DOWN -> 2
        EversenseTrendArrow.FLAT            -> 3
        EversenseTrendArrow.FORTY_FIVE_UP   -> 4
        EversenseTrendArrow.SINGLE_UP       -> 5
    }
}
