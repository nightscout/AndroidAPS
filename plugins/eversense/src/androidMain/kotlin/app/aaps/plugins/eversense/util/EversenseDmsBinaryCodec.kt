package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import app.aaps.plugins.eversense.protocol.BinaryFields
import java.io.ByteArrayOutputStream
import java.util.Base64

/** DMS record layouts use one writer, including the same UTC fields as the BLE protocol. */
internal object EversenseDmsBinaryCodec {
    private class RecordWriter {
        private val output = ByteArrayOutputStream()
        fun bytes(vararg octets: Int) { octets.forEach(output::write) }
        fun raw(value: ByteArray) { output.write(value) }
        fun number(value: Int, width: Int = 2) { raw(BinaryFields.littleEndian(value.toLong(), width)) }
        fun timestamp(value: Long) { raw(BinaryFields.date(value)); raw(BinaryFields.time(value)) }
        fun zeros(count: Int) { raw(ByteArray(count)) }
        fun sensorId(value: String, emptySize: Int = 0) {
            if (value.isEmpty()) zeros(emptySize)
            else value.chunked(2).forEach { output.write(it.toInt(16)) }
        }
        fun encoded(): String = Base64.getEncoder().encodeToString(output.toByteArray())
    }
    private fun record(body: RecordWriter.() -> Unit): String = RecordWriter().apply(body).encoded()

    fun int16LE(v: Int): ByteArray = BinaryFields.littleEndian(v.toLong(), 2)
    fun int24LE(v: Int): ByteArray = BinaryFields.littleEndian(v.toLong(), 3)
    fun int32LE(v: Int): ByteArray = BinaryFields.littleEndian(v.toLong(), 4)
    fun calcDateBytes(tsMs: Long): ByteArray = BinaryFields.date(tsMs)
    fun calcTimeBytes(tsMs: Long): ByteArray = BinaryFields.time(tsMs)

    fun buildSgBytes(readings: List<EversenseCGMResult>): String = record {
        bytes(0x8c, 0, 1, 0, 0)
        number(readings.size, 3)
        readings.forEachIndexed { index, reading ->
            number(index + 1, 3)
            timestamp(reading.datetime)
            number(reading.glucoseInMgDl)
            bytes(0)
            sensorId(reading.sensorId, emptySize = 10)
            zeros(19) // Unavailable ADC, acceleration and temperature fields.
        }
    }
    fun buildMgBytes(calibrations: List<CalibrationHistoryItem>): String = record {
        bytes(0x98, 1, 0)
        number(calibrations.size)
        bytes(0)
        calibrations.forEachIndexed { index, calibration ->
            number(index + 1)
            timestamp(calibration.datetime)
            number(calibration.glucoseInMgDl)
            zeros(2)
            bytes(1, 0, 0, 0)
        }
    }
    fun buildEmptyMgBytes(): String = buildMgBytes(emptyList())
    fun buildEmptyPatientBytes(): String = record { bytes(0x9e, 1, 0); zeros(2) }

    fun buildAlertBytes(sensorId: String, alerts: List<ActiveAlarm> = emptyList(), datetime: Long = 0, glucoseInMgDl: Int = 0): String = record {
        val supported = alerts.filter { it.code.dmsCode != 255 }
        bytes(0x93, 1, 0)
        number(supported.size)
        sensorId(sensorId)
        bytes(0)
        supported.forEachIndexed { index, alarm ->
            number(index + 1)
            timestamp(datetime)
            bytes(alarm.code.dmsCode)
            number(glucoseInMgDl)
            zeros(4)
        }
    }

    fun signalStrengthOrdinal(percent: Int): Int = when {
        percent >= 75 -> 5
        percent >= 48 -> 4
        percent >= 30 -> 3
        percent >= 28 -> 2
        percent >= 25 -> 1
        else -> 0
    }
    fun trendOrdinal(trend: EversenseTrendArrow): Int = when (trend) {
        EversenseTrendArrow.NONE -> 0
        EversenseTrendArrow.SINGLE_DOWN -> 1
        EversenseTrendArrow.FORTY_FIVE_DOWN -> 2
        EversenseTrendArrow.FLAT -> 3
        EversenseTrendArrow.FORTY_FIVE_UP -> 4
        EversenseTrendArrow.SINGLE_UP -> 5
    }
}
