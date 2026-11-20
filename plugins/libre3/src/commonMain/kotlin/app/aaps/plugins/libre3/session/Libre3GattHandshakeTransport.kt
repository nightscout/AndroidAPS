package app.aaps.plugins.libre3.session

import app.aaps.plugins.libre3.gatt.Libre3BluetoothUuids
import app.aaps.plugins.libre3.gatt.Libre3GattClient

/**
 * Puts the handshake on top of a real Bluetooth link.
 *
 * The handshake itself knows nothing about Bluetooth, which is what lets its whole command order
 * be unit tested. This class is the only place where the two meet.
 *
 * ⚠️ ASYNC IMPACT: every method blocks the calling thread while the sensor answers. It must run on
 * the driver's own executor.
 */
class Libre3GattHandshakeTransport(
    private val gatt: Libre3GattClient,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : Libre3HandshakeTransport {

    /**
     * The command byte goes out on its own, with nothing in front of it.
     *
     * Sending it like an ordinary message would put a two byte place marker first, so `0x11` would
     * arrive as `00 00 11` and the sensor would not act on it.
     */
    override fun writeCommand(command: Byte) {
        gatt.writeRaw(Libre3BluetoothUuids.SEC_COMMAND_RESPONSE, byteArrayOf(command))
    }

    override fun write(channel: Libre3HandshakeChannel, payload: ByteArray) {
        gatt.write(uuidOf(channel), payload)
    }

    override fun awaitNotify(channel: Libre3HandshakeChannel, expectedSize: Int?): ByteArray {
        val answer = when {
            // The command channel answers with one piece whose first byte is the answer itself.
            // Putting it through the message builder would drop that byte as if it were a counter.
            channel == Libre3HandshakeChannel.COMMAND ->
                gatt.awaitNotifyRaw(uuidOf(channel), timeoutMs)

            expectedSize != null                      ->
                gatt.awaitNotify(uuidOf(channel), expectedSize, timeoutMs)

            // The certificate and challenge channels always send pieces that carry a counter, so
            // the caller has to say how long the message is. Reading them raw would hand back a
            // piece with a counter still in front of it, which no parser expects.
            else                                      ->
                throw Libre3HandshakeException("a size is needed to read the ${channel.name.lowercase()} channel")
        } ?: throw Libre3HandshakeException("the sensor did not answer on the ${channel.name.lowercase()} channel")
        if (answer.isEmpty()) throw Libre3HandshakeException("the link dropped while waiting for the sensor")
        return answer
    }

    private fun uuidOf(channel: Libre3HandshakeChannel) = when (channel) {
        Libre3HandshakeChannel.CERT      -> Libre3BluetoothUuids.SEC_CERT_DATA
        Libre3HandshakeChannel.CHALLENGE -> Libre3BluetoothUuids.SEC_CHALLENGE_DATA
        Libre3HandshakeChannel.COMMAND   -> Libre3BluetoothUuids.SEC_COMMAND_RESPONSE
    }

    companion object {

        /** The upstream project waits two seconds per step. Five leaves room for a slow phone. */
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
