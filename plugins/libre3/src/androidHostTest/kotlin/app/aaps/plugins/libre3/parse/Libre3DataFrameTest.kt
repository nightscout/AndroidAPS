package app.aaps.plugins.libre3.parse

import app.aaps.plugins.libre3.crypto.Libre3DataPlaneCrypto
import app.aaps.plugins.libre3.crypto.Libre3PacketKind
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The outer wrapping, and the packet number that the nonce depends on. */
class Libre3DataFrameTest {

    @Test
    fun `the packet number is read from the last two bytes`() {
        val raw = ByteArray(33) { it.toByte() } + byteArrayOf(0x07, 0x00)

        val frame = Libre3DataFrame.parse(raw)

        assertThat(frame.encrypted).hasLength(33)
        assertThat(frame.seq).isEqualTo(7)
        assertThat(frame.type).isEqualTo(0)
        assertThat(frame.sequenceNumber).isEqualTo(7)
    }

    @Test
    fun `the second byte carries the high half of the number`() {
        val raw = ByteArray(33) + byteArrayOf(0x34, 0x12)

        assertThat(Libre3DataFrame.parse(raw).sequenceNumber).isEqualTo(0x1234)
    }

    @Test
    fun `a glucose message is 35 bytes, which is 29 plain plus a tag plus the number`() {
        // 15 + 20 arrive on the air, and they must add up to exactly this.
        val raw = ByteArray(15) + ByteArray(20)

        val frame = Libre3DataFrame.parse(raw)

        assertThat(raw).hasLength(35)
        assertThat(frame.encrypted).hasLength(Libre3GlucoseParser.PLAINTEXT_SIZE + Libre3DataPlaneCrypto.TAG_SIZE)
    }

    @Test
    fun `a message that is too short is refused`() {
        assertThrows<Libre3ParseException> { Libre3DataFrame.parse(ByteArray(2)) }
    }

    @Test
    fun `a message really written by a sensor can be read back with its own number`() {
        val crypto = Libre3DataPlaneCrypto(ByteArray(16) { it.toByte() }, ByteArray(8) { (it + 9).toByte() })
        val plaintext = ByteArray(29) { (it * 5).toByte() }
        val sequence = 0x0142

        // What the sensor puts on the air: the sealed bytes, then the packet number.
        val sealed = crypto.encrypt(plaintext, sequence, Libre3PacketKind.PATCH_DATA)
        val onAir = sealed + byteArrayOf((sequence and 0xFF).toByte(), ((sequence shr 8) and 0xFF).toByte())

        val frame = Libre3DataFrame.parse(onAir)
        val recovered = crypto.decryptTryingAllKinds(frame.encrypted, frame.sequenceNumber)

        assertThat(frame.sequenceNumber).isEqualTo(sequence)
        assertThat(recovered.plaintext).isEqualTo(plaintext)
    }

    @Test
    fun `counting packets on this side instead would fail to read the message`() {
        // This is the mistake the wrapping exists to prevent. A driver that kept its own counter
        // would be right only until the first packet it did not see.
        val crypto = Libre3DataPlaneCrypto(ByteArray(16), ByteArray(8))
        val sealed = crypto.encrypt(ByteArray(29), 0x0142, Libre3PacketKind.PATCH_DATA)

        assertThrows<Exception> { crypto.decryptTryingAllKinds(sealed, 0) }
    }
}
