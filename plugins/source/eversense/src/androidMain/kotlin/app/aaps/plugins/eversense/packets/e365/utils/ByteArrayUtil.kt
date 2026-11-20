package app.aaps.plugins.eversense.packets.e365.utils

import app.aaps.plugins.eversense.protocol.BinaryFields

fun UByteArray.toUtfString(): String = buildString {
    this@toUtfString.filter { it.toInt() != 0 }.forEach { append(it.toInt().toChar()) }
}
fun Short.toByteArray(): ByteArray = BinaryFields.littleEndian(toLong(), 2)
fun Int.toTimeZone(): ByteArray {
    val minutes = this / 60_000
    return BinaryFields.clockFields(minutes / 60, minutes % 60)
}
fun UByteArray.toShort(): Short = BinaryFields.unsigned(this, width = 2).toShort()
fun UByteArray.toInt(): Int = BinaryFields.unsigned(this, width = 2).toInt()
fun UByteArray.toLong(): Long = BinaryFields.unsigned(this)
fun ByteArray.toLong(): Long = BinaryFields.unsigned(toUByteArray())
const val UNIX = 946_684_800_000
fun UByteArray.toUnix(): Long = BinaryFields.sensorTimestamp(this)
fun Long.toUnixArray(): ByteArray = BinaryFields.sensorTimestamp(this)
