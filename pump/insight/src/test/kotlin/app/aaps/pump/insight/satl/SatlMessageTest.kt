package app.aaps.pump.insight.satl

import app.aaps.pump.insight.exceptions.InvalidNonceException
import app.aaps.pump.insight.utils.ByteBuf
import app.aaps.pump.insight.utils.Nonce
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Covers [SatlMessage] serialize/deserialize for both the unencrypted CRC framing and the
 * encrypted CTR framing (round-trip via [SynRequest], a registered empty-payload command), plus
 * hasCompletePacket and the nonce-replay guard. Pure ByteBuf/Cryptograph — no Android, no pump.
 */
class SatlMessageTest {

    private val key = ByteArray(16) { it.toByte() }

    @Test
    fun crcRoundTrip_preservesTypeAndCommId() {
        val serialized = SynRequest().apply { commID = 12345L }.serialize(null) // key null → CRC framing
        assertThat(SatlMessage.hasCompletePacket(serialized)).isTrue()
        val restored = SatlMessage.deserialize(serialized, null, null)!!
        assertThat(restored).isInstanceOf(SynRequest::class.java)
        assertThat(restored.commID).isEqualTo(12345L)
    }

    @Test
    fun ctrRoundTrip_preservesTypeAndCommId() {
        val serialized = SynRequest().apply {
            commID = 999L
            nonce = Nonce().apply { increment(5) }
        }.serialize(key) // nonce + key → CTR framing (encrypted + MAC)
        val restored = SatlMessage.deserialize(serialized, Nonce(), key)!! // lastNonce 0 < 5
        assertThat(restored).isInstanceOf(SynRequest::class.java)
        assertThat(restored.commID).isEqualTo(999L)
    }

    @Test
    fun ctrRejectsReplayedNonce() {
        val serialized = SynRequest().apply {
            commID = 1L
            nonce = Nonce().apply { increment(3) }
        }.serialize(key)
        // lastNonce 10 is not smaller than the packet's nonce 3 → replay
        assertThrows<InvalidNonceException> { SatlMessage.deserialize(serialized, Nonce().apply { increment(10) }, key) }
    }

    @Test
    fun hasCompletePacket_falseForTooShortBuffer() {
        assertThat(SatlMessage.hasCompletePacket(ByteBuf(10))).isFalse() // filledSize 0 < 37
    }
}
