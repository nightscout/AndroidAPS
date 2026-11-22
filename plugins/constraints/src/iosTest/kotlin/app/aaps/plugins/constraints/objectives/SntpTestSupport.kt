package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/** Where the three timestamps sit in an SNTP packet. Private in [SntpPacket], so repeated here. */
internal const val ORIGINATE_OFFSET = 24
internal const val RECEIVE_OFFSET = 32
internal const val TRANSMIT_OFFSET = 40

/**
 * What a server sends back for [request]: received and sent at [serverTime], with the client's own
 * transmit time copied into the originate field, as the protocol says.
 */
internal fun serverAnswer(request: ByteArray, serverTime: Long): ByteArray {
    val response = ByteArray(SntpPacket.NTP_PACKET_SIZE)
    // Version 3, mode 4 (server).
    response[0] = 0x1C.toByte()
    request.copyInto(response, destinationOffset = ORIGINATE_OFFSET, startIndex = TRANSMIT_OFFSET, endIndex = TRANSMIT_OFFSET + 8)
    SntpPacket.writeTimeStamp(response, RECEIVE_OFFSET, serverTime, 0)
    SntpPacket.writeTimeStamp(response, TRANSMIT_OFFSET, serverTime, 0)
    return response
}

/** A logger that drops everything. These tests check results, not log lines. */
internal class SilentLogger : AAPSLogger {

    override fun debug(message: String) {}
    override fun debug(enable: Boolean, tag: LTag, message: String) {}
    override fun debug(tag: LTag, message: String) {}
    override fun debug(tag: LTag, accessor: () -> String) {}
    override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun warn(tag: LTag, message: String) {}
    override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun info(tag: LTag, message: String) {}
    override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun error(tag: LTag, message: String) {}
    override fun error(tag: LTag, message: String, throwable: Throwable) {}
    override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun error(message: String) {}
    override fun error(message: String, throwable: Throwable) {}
    override fun error(format: String, vararg arguments: Any?) {}
    override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
}
