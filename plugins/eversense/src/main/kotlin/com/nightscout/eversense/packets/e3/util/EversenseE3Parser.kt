package com.nightscout.eversense.packets.e3.util

import java.util.Calendar
import java.util.TimeZone

class EversenseE3Parser {
    companion object {
        fun readDate(data: UByteArray, start: Int): Long {
            require(data.size >= start + 2) { "readDate: data too short (size=${data.size}, start=$start)" }
            val lowByte = data[start].toInt()
            val highByte = data[start + 1].toInt()

            val day = lowByte and 31
            var month = lowByte shr 5
            val year = (highByte shr 1) + 2000

            if (highByte and 1 == 1) {
                month += 8
            }

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month - 1)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        }

        fun readTime(data: UByteArray, start: Int): Long {
            require(data.size >= start + 2) { "readTime: data too short (size=${data.size}, start=$start)" }
            val lowByte = data[start].toInt()
            val highByte = data[start + 1].toInt()

            val hour = highByte shr 3
            val minute = ((highByte and 7) shl 3) or (lowByte shr 5)
            val second = (lowByte and 31) * 2

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            calendar.set(Calendar.YEAR, 1970)
            calendar.set(Calendar.MONTH, 0)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, second)
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        }

        /**
         * Reads a timezone offset from 3 bytes: 2 bytes of time (HH:MM encoded) + 1 sign byte.
         * Returns the offset in milliseconds (positive = east of UTC, negative = west).
         */
        fun readTimezone(data: UByteArray, start: Int): Long {
            require(data.size >= start + 3) { "readTimezone: data too short (size=${data.size}, start=$start)" }
            val lowByte = data[start].toInt()
            val highByte = data[start + 1].toInt()

            val hour = highByte shr 3
            val minute = ((highByte and 7) shl 3) or (lowByte shr 5)
            val offsetMs = (hour * 60L + minute) * 60L * 1000L

            return if (data[start + 2] != 0.toUByte()) -offsetMs else offsetMs
        }

        fun readGlucose(data: UByteArray, start: Int): Int {
            require(data.size >= start + 2) { "readGlucose: data too short (size=${data.size}, start=$start)" }
            val lowByte = data[start].toInt() and 0xFF
            val highByte = (data[start + 1].toInt() and 0xFF) shl 8
            return lowByte or highByte
        }
    }
}
