package app.aaps.plugins.eversense.protocol

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets

/** Immutable wire contract, supplied explicitly rather than discovered through annotations. */
data class PacketSpec(
    val requestId: Byte,
    val responseId: Byte,
    val typeId: Byte,
    val securityType: EversenseSecurityType
) {
    internal val responseOffset: Int get() = when (responseId) {
        EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId,
        EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId,
        EversenseE3Packets.ReadFourByteSerialFlashRegisterResponseId -> 4
        else -> 1
    }
    companion object {
        fun e3(command: Byte, reply: Byte) = PacketSpec(command, reply, 0, EversenseSecurityType.None)
        fun v2(command: Byte, reply: Byte, operation: Byte) = PacketSpec(command, reply, operation, EversenseSecurityType.SecureV2)
    }
}
