package app.aaps.plugins.libre3.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The glucose data plane: ordinary AES with the session key from the handshake. */
class Libre3DataPlaneCryptoTest {

    private val kEnc = ByteArray(16) { it.toByte() }
    private val ivEnc = ByteArray(8) { (0xA0 + it).toByte() }
    private val crypto = Libre3DataPlaneCrypto(kEnc, ivEnc)

    private fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(it) }

    @Test
    fun `the nonce is the sequence, then the kind, then the stored part`() {
        val nonce = crypto.nonce(sequence = 0x1234, kind = Libre3PacketKind.PATCH_DATA)

        assertThat(nonce).hasLength(13)
        assertThat(hex(nonce)).isEqualTo("3412" + "440000" + "a0a1a2a3a4a5a6a7")
    }

    @Test
    fun `a packet comes back the way it went in`() {
        val plaintext = ByteArray(29) { (it * 3).toByte() }

        val packet = crypto.encrypt(plaintext, sequence = 7, kind = Libre3PacketKind.PATCH_DATA)
        val recovered = crypto.decrypt(packet, sequence = 7, kind = Libre3PacketKind.PATCH_DATA)

        assertThat(packet).hasLength(plaintext.size + Libre3DataPlaneCrypto.TAG_SIZE)
        assertThat(hex(recovered)).isEqualTo(hex(plaintext))
    }

    @Test
    fun `the right kind is found when it is not known in advance`() {
        val plaintext = ByteArray(29) { it.toByte() }
        val packet = crypto.encrypt(plaintext, sequence = 12, kind = Libre3PacketKind.PATCH_DATA)

        val found = crypto.decryptTryingAllKinds(packet, sequence = 12)

        assertThat(found.kind).isEqualTo(Libre3PacketKind.PATCH_DATA)
        assertThat(hex(found.plaintext)).isEqualTo(hex(plaintext))
    }

    @Test
    fun `a packet read with the wrong sequence number is refused`() {
        val packet = crypto.encrypt(ByteArray(29), sequence = 7, kind = Libre3PacketKind.PATCH_DATA)

        assertThrows<Libre3CryptoException> { crypto.decrypt(packet, sequence = 8, kind = Libre3PacketKind.PATCH_DATA) }
    }

    @Test
    fun `a packet from another session is refused`() {
        val packet = crypto.encrypt(ByteArray(29), sequence = 7, kind = Libre3PacketKind.PATCH_DATA)
        val otherSession = Libre3DataPlaneCrypto(ByteArray(16) { (it + 1).toByte() }, ivEnc)

        assertThrows<Libre3CryptoException> { otherSession.decryptTryingAllKinds(packet, sequence = 7) }
    }

    @Test
    fun `a packet that is shorter than its own tag is refused`() {
        assertThrows<Libre3CryptoException> {
            crypto.decrypt(ByteArray(3), sequence = 1, kind = Libre3PacketKind.PATCH_DATA)
        }
    }

    @Test
    fun `keys of the wrong size are refused when the session starts`() {
        assertThrows<IllegalArgumentException> { Libre3DataPlaneCrypto(ByteArray(15), ivEnc) }
        assertThrows<IllegalArgumentException> { Libre3DataPlaneCrypto(kEnc, ByteArray(7)) }
    }
}
