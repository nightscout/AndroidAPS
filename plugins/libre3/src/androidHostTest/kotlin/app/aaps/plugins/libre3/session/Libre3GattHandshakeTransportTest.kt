package app.aaps.plugins.libre3.session

import app.aaps.plugins.libre3.gatt.Libre3BluetoothUuids
import app.aaps.plugins.libre3.gatt.Libre3GattClient
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * How the handshake reaches the wire.
 *
 * The three kinds of channel need three different treatments, and getting that wrong is invisible
 * in a compiler and fatal on a real sensor:
 *
 * - a command goes out with nothing in front of it (`writeRaw`),
 * - a command answer is read exactly as it arrived, because its first byte is the answer,
 * - a certificate or challenge answer is put back together from pieces that carry a counter.
 *
 * Source: LibreCRKit `SensorSessionTransport.swift`, where `writeCommand` uses `session.writeRaw`
 * and `awaitCommandResponse` returns the raw fragment, while `awaitNotify(on:exactly:)` builds a
 * fresh reassembler.
 */
class Libre3GattHandshakeTransportTest {

    /** Records what was asked of the link, and answers from a script. */
    private class RecordingGatt : Libre3GattClient {

        val framedWrites = mutableListOf<Pair<String, ByteArray>>()
        val rawWrites = mutableListOf<Pair<String, ByteArray>>()
        val rawWaits = mutableListOf<String>()
        val sizedWaits = mutableListOf<Pair<String, Int>>()

        var rawAnswer: ByteArray? = byteArrayOf(0x08)
        var sizedAnswer: ByteArray? = ByteArray(23)

        override fun connect(deviceAddress: String) = Unit
        override fun disconnect() = Unit
        override fun isConnected(): Boolean = true
        override fun setNotify(characteristic: String, enabled: Boolean): Boolean = true

        override fun write(characteristic: String, payload: ByteArray) {
            framedWrites.add(characteristic to payload)
        }

        override fun writeRaw(characteristic: String, payload: ByteArray) {
            rawWrites.add(characteristic to payload)
        }

        override fun awaitNotifyRaw(characteristic: String, timeoutMs: Long): ByteArray? {
            rawWaits.add(characteristic)
            return rawAnswer
        }

        override fun awaitNotify(characteristic: String, exactly: Int, timeoutMs: Long): ByteArray? {
            sizedWaits.add(characteristic to exactly)
            return sizedAnswer
        }

        override fun awaitDataPlaneNotify(timeoutMs: Long): Pair<String, ByteArray>? = null
    }

    @Test
    fun `a command goes out on its own, with no place marker in front`() {
        val gatt = RecordingGatt()

        Libre3GattHandshakeTransport(gatt).writeCommand(0x11)

        assertThat(gatt.rawWrites).hasSize(1)
        assertThat(gatt.rawWrites[0].first).isEqualTo(Libre3BluetoothUuids.SEC_COMMAND_RESPONSE)
        assertThat(gatt.rawWrites[0].second).isEqualTo(byteArrayOf(0x11))
        // Sent the other way it would arrive as 00 00 11 and the sensor would ignore it.
        assertThat(gatt.framedWrites).isEmpty()
    }

    @Test
    fun `a command answer is read exactly as it arrived`() {
        val gatt = RecordingGatt().apply { rawAnswer = byteArrayOf(0x04, 0x00) }

        val answer = Libre3GattHandshakeTransport(gatt).awaitNotify(Libre3HandshakeChannel.COMMAND)

        assertThat(answer[0]).isEqualTo(0x04.toByte())
        assertThat(gatt.rawWaits).containsExactly(Libre3BluetoothUuids.SEC_COMMAND_RESPONSE)
        // Nothing was put back together, so the first byte is still the answer of the sensor.
        assertThat(gatt.sizedWaits).isEmpty()
    }

    @Test
    fun `a certificate or challenge answer is put back together to its full size`() {
        val gatt = RecordingGatt()
        val transport = Libre3GattHandshakeTransport(gatt)

        transport.awaitNotify(Libre3HandshakeChannel.CHALLENGE, 23)
        transport.awaitNotify(Libre3HandshakeChannel.CERT, 140)

        assertThat(gatt.sizedWaits).containsExactly(
            Libre3BluetoothUuids.SEC_CHALLENGE_DATA to 23,
            Libre3BluetoothUuids.SEC_CERT_DATA to 140,
        ).inOrder()
    }

    @Test
    fun `a certificate goes out cut into pieces, not in one write`() {
        val gatt = RecordingGatt()

        Libre3GattHandshakeTransport(gatt).write(Libre3HandshakeChannel.CERT, ByteArray(162))

        assertThat(gatt.framedWrites).hasSize(1)
        assertThat(gatt.framedWrites[0].first).isEqualTo(Libre3BluetoothUuids.SEC_CERT_DATA)
        assertThat(gatt.rawWrites).isEmpty()
    }

    @Test
    fun `a sensor that says nothing is reported instead of being waited on for ever`() {
        val gatt = RecordingGatt().apply { rawAnswer = null }

        assertThrows<Libre3HandshakeException> {
            Libre3GattHandshakeTransport(gatt).awaitNotify(Libre3HandshakeChannel.COMMAND)
        }
    }

    @Test
    fun `a dropped link during a wait is reported, not read as an answer`() {
        val gatt = RecordingGatt().apply { rawAnswer = ByteArray(0) }

        assertThrows<Libre3HandshakeException> {
            Libre3GattHandshakeTransport(gatt).awaitNotify(Libre3HandshakeChannel.COMMAND)
        }
    }
}
