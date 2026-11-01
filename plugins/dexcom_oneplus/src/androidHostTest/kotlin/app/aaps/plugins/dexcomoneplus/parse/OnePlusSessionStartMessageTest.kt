package app.aaps.plugins.dexcomoneplus.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OnePlusSessionStartMessageTest {

    @Test
    fun sessionStartTx_lengthAndCrc() {
        val packet = OnePlusSessionStartTx.build(
            dexTimeSeconds = 100,
            startTimeEpochMs = 1_700_000_000_000L,
        )
        assertThat(packet).hasLength(OnePlusSessionStartTx.PACKET_LENGTH)
        assertThat(packet[0]).isEqualTo(OnePlusSessionStartTx.OPCODE)
        assertThat(OnePlusFastCrc16.check(packet)).isTrue()
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.get()
        assertThat(buf.int).isEqualTo(100)
        assertThat(buf.int).isEqualTo(1_700_000_000)
    }

    @Test
    fun sessionStartRx_parsesOkay() {
        val body = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
        body.put(OnePlusSessionStartRx.OPCODE)
        body.put(0x00) // status
        body.put(0x01) // info OK
        body.putInt(10)
        body.putInt(20)
        body.putInt(30)
        val packet = body.array()
        val crc = OnePlusFastCrc16.calculate(packet, 15)
        packet[15] = crc[0]
        packet[16] = crc[1]

        val rx = OnePlusSessionStartRx.parse(packet)
        assertThat(rx).isNotNull()
        assertThat(rx!!.isOkay()).isTrue()
        assertThat(rx.sessionStartTime).isEqualTo(20)
        assertThat(rx.transmitterTime).isEqualTo(30)
        assertThat(rx.message()).isEqualTo("OK")
    }

    @Test
    fun sessionStartRx_alreadyStarted() {
        val body = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
        body.put(OnePlusSessionStartRx.OPCODE)
        body.put(0x00)
        body.put(0x02)
        body.putInt(1)
        body.putInt(2)
        body.putInt(3)
        val packet = body.array()
        val crc = OnePlusFastCrc16.calculate(packet, 15)
        packet[15] = crc[0]
        packet[16] = crc[1]

        val rx = OnePlusSessionStartRx.parse(packet)!!
        assertThat(rx.isAlreadyStarted()).isTrue()
        assertThat(rx.isOkay()).isFalse()
    }
}
