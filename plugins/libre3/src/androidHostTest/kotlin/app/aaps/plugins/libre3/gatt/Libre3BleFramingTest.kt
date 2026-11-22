package app.aaps.plugins.libre3.gatt

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Cutting messages into pieces and putting them back together. */
class Libre3BleFramingTest {

    private fun hex(data: ByteArray) = data.joinToString("") { "%02x".format(it) }

    @Test
    fun `a Phase 5 message goes out as three pieces of 18 bytes`() {
        val message = ByteArray(54) { it.toByte() }

        val pieces = Libre3BleFraming.fragmentForWrite(message)

        assertThat(pieces).hasSize(3)
        pieces.forEach { assertThat(it).hasLength(20) }
        assertThat(hex(pieces[0].copyOfRange(0, 2))).isEqualTo("0000")
        assertThat(hex(pieces[1].copyOfRange(0, 2))).isEqualTo("1200")
        assertThat(hex(pieces[2].copyOfRange(0, 2))).isEqualTo("2400")
    }

    @Test
    fun `the certificate goes out as nine pieces`() {
        val pieces = Libre3BleFraming.fragmentForWrite(ByteArray(162))

        assertThat(pieces).hasSize(9)
    }

    @Test
    fun `a message that was cut can be put back together`() {
        val message = ByteArray(162) { (it * 7).toByte() }

        val rebuilt = Libre3BleFraming.reassembleWrite(Libre3BleFraming.fragmentForWrite(message))

        assertThat(hex(rebuilt)).isEqualTo(hex(message))
    }

    @Test
    fun `pieces that arrive out of order are still put back in the right order`() {
        val message = ByteArray(54) { it.toByte() }
        val pieces = Libre3BleFraming.fragmentForWrite(message).reversed()

        assertThat(hex(Libre3BleFraming.reassembleWrite(pieces))).isEqualTo(hex(message))
    }

    @Test
    fun `a missing piece is noticed instead of making a wrong message`() {
        val pieces = Libre3BleFraming.fragmentForWrite(ByteArray(54))

        assertThrows<Libre3FramingException> {
            Libre3BleFraming.reassembleWrite(listOf(pieces[0], pieces[2]))
        }
    }

    @Test
    fun `what the sensor sends is put together by its counting byte`() {
        val reassembler = Libre3BleFraming.NotifyReassembler()

        reassembler.feed(byteArrayOf(0) + ByteArray(19) { it.toByte() })
        reassembler.feed(byteArrayOf(1) + ByteArray(4) { (it + 19).toByte() })

        assertThat(reassembler.availableBytes).isEqualTo(23)
        assertThat(hex(reassembler.take(23))).isEqualTo(hex(ByteArray(23) { it.toByte() }))
    }

    @Test
    fun `a lost piece is noticed instead of making a wrong message`() {
        val reassembler = Libre3BleFraming.NotifyReassembler()
        reassembler.feed(byteArrayOf(0) + ByteArray(19))

        assertThrows<Libre3FramingException> { reassembler.feed(byteArrayOf(2) + ByteArray(19)) }
    }

    @Test
    fun `the counting byte wraps round after 255`() {
        val reassembler = Libre3BleFraming.NotifyReassembler()
        reassembler.feed(byteArrayOf(0xFF.toByte()) + ByteArray(19))

        reassembler.feed(byteArrayOf(0x00) + ByteArray(19))

        assertThat(reassembler.availableBytes).isEqualTo(38)
    }

    @Test
    fun `asking for more bytes than arrived is refused`() {
        val reassembler = Libre3BleFraming.NotifyReassembler()
        reassembler.feed(byteArrayOf(0) + ByteArray(10))

        assertThrows<Libre3FramingException> { reassembler.take(23) }
    }

    @Test
    fun `starting again clears both the bytes and the counting`() {
        val reassembler = Libre3BleFraming.NotifyReassembler()
        reassembler.feed(byteArrayOf(5) + ByteArray(19))

        reassembler.reset()
        reassembler.feed(byteArrayOf(0) + ByteArray(19))

        assertThat(reassembler.availableBytes).isEqualTo(19)
    }
}
