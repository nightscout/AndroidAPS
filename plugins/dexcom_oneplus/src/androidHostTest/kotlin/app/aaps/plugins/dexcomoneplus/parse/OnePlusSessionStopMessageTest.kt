package app.aaps.plugins.dexcomoneplus.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OnePlusSessionStopMessageTest {

    @Test
    fun sessionStopTx_lengthAndCrc() {
        val packet = OnePlusSessionStopTx.build(stopTimeDexSeconds = 12345)
        assertThat(packet).hasLength(OnePlusSessionStopTx.PACKET_LENGTH)
        assertThat(packet[0]).isEqualTo(OnePlusSessionStopTx.OPCODE)
        assertThat(OnePlusFastCrc16.check(packet)).isTrue()
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.get()
        assertThat(buf.int).isEqualTo(12345)
    }

    @Test
    fun sessionStopRx_parsesOkay() {
        val body = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
        body.put(OnePlusSessionStopRx.OPCODE)
        body.put(0x00) // status
        body.put(0x01) // received
        body.putInt(50) // stop
        body.putInt(10) // start
        body.putInt(60) // tx time
        val packet = body.array()
        val crc = OnePlusFastCrc16.calculate(packet, 15)
        packet[15] = crc[0]
        packet[16] = crc[1]

        val rx = OnePlusSessionStopRx.parse(packet)
        assertThat(rx).isNotNull()
        assertThat(rx!!.isOkay()).isTrue()
        assertThat(rx.sessionStopTime).isEqualTo(50)
        assertThat(rx.transmitterTime).isEqualTo(60)
    }
}
