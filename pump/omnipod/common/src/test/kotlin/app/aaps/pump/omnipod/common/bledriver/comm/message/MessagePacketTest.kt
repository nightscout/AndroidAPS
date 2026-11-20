package app.aaps.pump.omnipod.common.bledriver.comm.message

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [MessagePacket.signatureData] (O5 Type-4/ENCRYPTED_SIGNED commands, see
 * `Session.sign()`) must be appended after the payload on the wire but stay outside the
 * 16-byte header's size field - the exact subtlety that would otherwise make a
 * receiver's AAD reconstruction disagree with the sender's, silently breaking every
 * signed command's AES-CCM tag.
 */
class MessagePacketTest {

    private fun packet(payload: ByteArray, signatureData: ByteArray? = null) = MessagePacket(
        type = MessageType.ENCRYPTED_SIGNED,
        source = Id.fromInt(1),
        destination = Id.fromInt(2),
        payload = payload,
        sequenceNumber = 1,
        signatureData = signatureData
    )

    @Test
    fun `signatureData is appended after the payload on the wire`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val signature = ByteArray(64) { 0x7F }

        val bytes = packet(payload, signature).asByteArray()

        assertThat(bytes.size).isEqualTo(16 + payload.size + signature.size)
        assertThat(bytes.copyOfRange(16, 16 + payload.size)).isEqualTo(payload)
        assertThat(bytes.copyOfRange(16 + payload.size, bytes.size)).isEqualTo(signature)
    }

    @Test
    fun `the header's size field is unaffected by a trailing signature - only payload counts`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val signature = ByteArray(64) { 0x11 }

        val unsigned = packet(payload, signatureData = null).asByteArray()
        val signed = packet(payload, signatureData = signature).asByteArray()

        assertThat(signed.copyOfRange(6, 8)).isEqualTo(unsigned.copyOfRange(6, 8))
        assertThat(signed.copyOfRange(0, 16)).isEqualTo(unsigned.copyOfRange(0, 16))
    }

    @Test
    fun `no signatureData means nothing extra is appended`() {
        val payload = byteArrayOf(0x0A, 0x0B)

        val bytes = packet(payload, signatureData = null).asByteArray()

        assertThat(bytes.size).isEqualTo(16 + payload.size)
    }
}
