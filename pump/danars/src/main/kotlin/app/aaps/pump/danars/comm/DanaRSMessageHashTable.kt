package app.aaps.pump.danars.comm

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
/**
 * Used to get an instance of a packet by received command code
 */
class DanaRSMessageHashTable @Inject constructor(
    /**
     * Only packets which are not respond to sent packet are listed
     */
    private val packets: Set<@JvmSuppressWildcards DanaRSPacket>
) {

    fun findMessage(command: Int): DanaRSPacket = packets.find { it.command == command } ?: error("Packet not found")
}