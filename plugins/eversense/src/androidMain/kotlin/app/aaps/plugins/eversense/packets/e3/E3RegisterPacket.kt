package app.aaps.plugins.eversense.packets.e3

import app.aaps.plugins.eversense.enums.EversenseE3Memory
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.protocol.PacketSpec

/** Register addressing is shared by all E3 register readers. */
abstract class E3RegisterPacket(spec: PacketSpec, private val register: EversenseE3Memory) : EversenseBasePacket(spec) {
    final override fun getRequestData(): ByteArray = register.getRequestData()
}
