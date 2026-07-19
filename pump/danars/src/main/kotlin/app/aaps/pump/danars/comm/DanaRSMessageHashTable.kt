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

    /**
     * The unsolicited packet for [command], or null if none is registered.
     *
     * Returns null rather than throwing: this runs on the BLE callback thread, and an unexpected
     * packet (a stray/duplicate notification, a multi-response tail that arrives after its request
     * closed) must not crash the whole app from there. `BLEComm.processMessage` already handles null
     * by logging "Unknown message received" — throwing here left that branch dead and turned any
     * unrecognised packet into a process crash.
     */
    fun findMessage(command: Int): DanaRSPacket? = packets.find { it.command == command }
}