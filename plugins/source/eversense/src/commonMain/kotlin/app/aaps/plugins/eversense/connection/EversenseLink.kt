package app.aaps.plugins.eversense.connection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.eversense.packets.EversenseBasePacket

/** What the protocol needs from one live link to a transmitter: send a packet and wait for its answer. */
interface EversenseLink {

    val aapsLogger: AAPSLogger

    /**
     * Sends [packet] and waits for its answer, one packet at a time. Throws
     * [app.aaps.plugins.eversense.exceptions.EversenseWriteException] when the transmitter rejects it, gives no answer
     * in [timeoutMs] (the link is closed then, so a late answer cannot be taken for the next packet's), or the link is
     * lost.
     */
    suspend fun <T : EversenseBasePacket.Response> writePacket(packet: EversenseBasePacket, timeoutMs: Long = WRITE_TIMEOUT_MS): T

    /** Closes this link; the driver connects again after its usual wait. */
    fun disconnectAndScheduleReconnect()

    companion object {

        const val WRITE_TIMEOUT_MS = 5000L
    }
}
