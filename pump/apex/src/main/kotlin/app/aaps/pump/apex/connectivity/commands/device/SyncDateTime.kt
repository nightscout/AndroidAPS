package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import org.joda.time.DateTime

/** Update system date and time.
 * Sent after every connection and every N pump heartbeats.
 *
 * * [dateTime] - new date and time.
 */
class SyncDateTime(
    info: ApexDeviceInfo,
    val dateTime: DateTime,
) : BaseValueCommand(info) {
    override val valueId = 0x31
    override val isWrite = true

    override val additionalData: ByteArray
        get() = byteArrayOf(
            (dateTime.year % 100).toByte(),
            dateTime.monthOfYear.toByte(),
            dateTime.dayOfMonth.toByte(),
            dateTime.hourOfDay.toByte(),
            dateTime.minuteOfHour.toByte(),
            dateTime.secondOfMinute.toByte()
        )

    override fun toString(): String = "SyncDateTime($dateTime)"
}
