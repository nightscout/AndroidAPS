package app.aaps.plugins.dexcomoneplus.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OnePlusTransmitterTimeMessageTest {

    @Test
    fun transmitterTimeTx_lengthAndCrc() {
        val packet = OnePlusTransmitterTimeTx.request()
        assertThat(packet).hasLength(OnePlusTransmitterTimeTx.PACKET_LENGTH)
        assertThat(packet[0]).isEqualTo(OnePlusTransmitterTimeTx.OPCODE)
        assertThat(OnePlusFastCrc16.check(packet)).isTrue()
    }

    @Test
    fun transmitterTimeRx_sessionInProgress() {
        // Minimal 10-byte body + CRC padding to make FastCRC16.check pass over full packet.
        // Packet: op | status | current(4) | sessionStart(4) | crc(2) = 12 bytes common in Ob1.
        val body = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        body.put(OnePlusTransmitterTimeRx.OPCODE)
        body.put(0x00) // status
        body.putInt(3600) // currentTime
        body.putInt(0) // sessionStart
        val packet = body.array()
        val crc = OnePlusFastCrc16.calculate(packet, 10)
        packet[10] = crc[0]
        packet[11] = crc[1]

        val rx = OnePlusTransmitterTimeRx.parse(packet)
        assertThat(rx).isNotNull()
        assertThat(rx!!.sessionInProgress()).isTrue()
        assertThat(rx.sessionAgeSeconds()).isEqualTo(3600L)
        assertThat(rx.currentTimeSeconds).isEqualTo(3600)
    }

    @Test
    fun transmitterTimeRx_notStarted() {
        val body = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        body.put(OnePlusTransmitterTimeRx.OPCODE)
        body.put(0x00)
        body.putInt(100)
        body.putInt(-1) // INVALID_TIME
        val packet = body.array()
        val crc = OnePlusFastCrc16.calculate(packet, 10)
        packet[10] = crc[0]
        packet[11] = crc[1]

        val rx = OnePlusTransmitterTimeRx.parse(packet)!!
        assertThat(rx.sessionInProgress()).isFalse()
        assertThat(rx.realSessionStartEpochMs()).isNull()
    }
}
