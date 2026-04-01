package com.nightscout.eversense.packets.e3.util

import android.util.Log
import java.util.Calendar
import java.util.TimeZone

class EversenseE3Parser {
    companion object {
        fun readDate(data: UByteArray, start: Int): Long {
            val lowBit = data[start].toInt()
            val highBit = data[start+1].toInt()

            val day = lowBit and 31
            var month = lowBit shr 5
            val year = (highBit shr 1) + 2000

            if (highBit and 1 == 1) {
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
            val lowBit = data[start].toInt()
            val highBit = data[start+1].toInt()

            val hour = highBit shr 3
            val minute = ((highBit and 7) shl 3) or (lowBit shr 5)
            val second = (lowBit and 31) * 2


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

        fun readTimezone(data: UByteArray, start: Int): Long {
            var timezoneOffset = readTime(data, start)
            if (data[start + 2] != 0.toUByte()) {
                timezoneOffset *= -1
            }

            return timezoneOffset
        }

        fun readGlucose(data: UByteArray, start: Int): Int {
            val lowBit =  data[start].toInt()
            val highBit = data[start+1].toInt() shl 8
            return lowBit or highBit
        }
    }
}