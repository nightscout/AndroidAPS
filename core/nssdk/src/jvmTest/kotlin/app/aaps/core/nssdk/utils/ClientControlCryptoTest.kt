package app.aaps.core.nssdk.utils

import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.AckPhase
import app.aaps.core.nssdk.localmodel.clientcontrol.AckStatus
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClientControlCryptoTest {

    private fun draft(
        clientId: String = "client-1",
        counter: Long = 1L,
        timestamp: Long = 1_700_000_000_000L,
        type: String = "hello",
        payload: String = """{"protocolVersion":1,"capabilities":[]}"""
    ) = SignedEnvelope(clientId, counter, timestamp, type, payload, signature = "")

    @Test
    fun signVerifyRoundtrip() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signEnvelope(secret, draft())
        assertThat(signed.signature).isNotEmpty()
        assertThat(ClientControlCrypto.verifyEnvelope(secret, signed)).isTrue()
    }

    @Test
    fun verifyRejectsWrongSecret() {
        val signed = ClientControlCrypto.signEnvelope(ClientControlCrypto.newSecretBytes(), draft())
        assertThat(ClientControlCrypto.verifyEnvelope(ClientControlCrypto.newSecretBytes(), signed)).isFalse()
    }

    @Test
    fun verifyRejectsTamperedPayload() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signEnvelope(secret, draft(payload = "original"))
        val tampered = signed.copy(payload = "tampered")
        assertThat(ClientControlCrypto.verifyEnvelope(secret, tampered)).isFalse()
    }

    @Test
    fun verifyRejectsTamperedCounter() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signEnvelope(secret, draft(counter = 5L))
        val tampered = signed.copy(counter = 6L)
        assertThat(ClientControlCrypto.verifyEnvelope(secret, tampered)).isFalse()
    }

    @Test
    fun verifyRejectsTamperedType() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signEnvelope(secret, draft(type = "hello"))
        val tampered = signed.copy(type = "scene.stop")
        assertThat(ClientControlCrypto.verifyEnvelope(secret, tampered)).isFalse()
    }

    @Test
    fun signatureIsDeterministicForSameInput() {
        val secret = ClientControlCrypto.newSecretBytes()
        val a = ClientControlCrypto.signEnvelope(secret, draft())
        val b = ClientControlCrypto.signEnvelope(secret, draft())
        assertThat(a.signature).isEqualTo(b.signature)
    }

    @Test
    fun newSecretIs32Bytes() {
        assertThat(ClientControlCrypto.newSecretBytes()).hasLength(32)
    }

    @Test
    fun newSecretsAreUnique() {
        val a = ClientControlCrypto.bytesToHex(ClientControlCrypto.newSecretBytes())
        val b = ClientControlCrypto.bytesToHex(ClientControlCrypto.newSecretBytes())
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun hexRoundtripsCleanly() {
        val original = ClientControlCrypto.newSecretBytes()
        val roundtrip = ClientControlCrypto.hexToBytes(ClientControlCrypto.bytesToHex(original))
        assertThat(roundtrip).isEqualTo(original)
    }

    @Test
    fun hexRejectsOddLength() {
        try {
            ClientControlCrypto.hexToBytes("abc")
            assertThat(false).isTrue() // unreached
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Odd-length")
        }
    }

    @Test
    fun hexRejectsNonHexCharacters() {
        try {
            ClientControlCrypto.hexToBytes("zz")
            assertThat(false).isTrue() // unreached
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Non-hex")
        }
    }

    @Test
    fun timestampSkewAcceptsCloseValues() {
        val now = 1_700_000_000_000L
        assertThat(ClientControlCrypto.timestampWithinSkew(now, now)).isTrue()
        assertThat(ClientControlCrypto.timestampWithinSkew(now - 4 * 60_000L, now)).isTrue()
        assertThat(ClientControlCrypto.timestampWithinSkew(now + 4 * 60_000L, now)).isTrue()
    }

    @Test
    fun timestampSkewRejectsStaleAndFuture() {
        val now = 1_700_000_000_000L
        assertThat(ClientControlCrypto.timestampWithinSkew(now - 6 * 60_000L, now)).isFalse()
        assertThat(ClientControlCrypto.timestampWithinSkew(now + 6 * 60_000L, now)).isFalse()
    }

    @Test
    fun timestampSkewBoundaryIsInclusive() {
        val now = 1_700_000_000_000L
        val window = 5 * 60_000L
        // Exactly at the boundary — accepted (window is `<=`).
        assertThat(ClientControlCrypto.timestampWithinSkew(now - window, now)).isTrue()
        assertThat(ClientControlCrypto.timestampWithinSkew(now + window, now)).isTrue()
        // One millisecond past the boundary — rejected.
        assertThat(ClientControlCrypto.timestampWithinSkew(now - window - 1, now)).isFalse()
        assertThat(ClientControlCrypto.timestampWithinSkew(now + window + 1, now)).isFalse()
    }

    @Test
    fun newClientIdIsUuidShape() {
        val id = ClientControlCrypto.newClientId()
        assertThat(id).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }

    @Test
    fun canonicalStringMatchesPipeFormat() {
        val env = draft(clientId = "c", counter = 7L, timestamp = 12345L, type = "hello", payload = "p").copy(validUntil = 99999L, wantsAck = true)
        assertThat(env.canonicalString()).isEqualTo("c|7|12345|99999|true|hello|p")
    }

    @Test
    fun verifyRejectsTamperedWantsAck() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signEnvelope(secret, draft().copy(wantsAck = false))
        val tampered = signed.copy(wantsAck = true)
        assertThat(ClientControlCrypto.verifyEnvelope(secret, tampered)).isFalse()
    }

    @Test
    fun verifyRejectsTamperedValidUntil() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signEnvelope(secret, draft().copy(validUntil = 1_000L))
        val tampered = signed.copy(validUntil = 9_999_999L)
        assertThat(ClientControlCrypto.verifyEnvelope(secret, tampered)).isFalse()
    }

    // ---- ACK (master → client) ----

    private fun ackDraft(
        clientId: String = "client-1",
        commandCounter: Long = 7L,
        phase: AckPhase = AckPhase.Done,
        status: AckStatus = AckStatus.Ok,
        reason: String? = null,
        payload: String? = null,
        timestamp: Long = 1_700_000_000_000L
    ) = AckEnvelope(clientId, commandCounter, phase, status, reason, payload, timestamp, signature = "")

    @Test
    fun ackSignVerifyRoundtrip() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signAck(secret, ackDraft(status = AckStatus.Failed, reason = "no active profile"))
        assertThat(signed.signature).isNotEmpty()
        assertThat(ClientControlCrypto.verifyAck(secret, signed)).isTrue()
    }

    @Test
    fun ackVerifyRejectsWrongSecret() {
        val signed = ClientControlCrypto.signAck(ClientControlCrypto.newSecretBytes(), ackDraft())
        assertThat(ClientControlCrypto.verifyAck(ClientControlCrypto.newSecretBytes(), signed)).isFalse()
    }

    @Test
    fun ackVerifyRejectsTamperedStatus() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signAck(secret, ackDraft(status = AckStatus.Failed))
        val tampered = signed.copy(status = AckStatus.Ok)
        assertThat(ClientControlCrypto.verifyAck(secret, tampered)).isFalse()
    }

    @Test
    fun ackVerifyRejectsTamperedCounter() {
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signAck(secret, ackDraft(commandCounter = 7L))
        val tampered = signed.copy(commandCounter = 8L)
        assertThat(ClientControlCrypto.verifyAck(secret, tampered)).isFalse()
    }

    @Test
    fun ackCanonicalStringMatchesPipeFormat() {
        val ack = ackDraft(clientId = "c", commandCounter = 7L, phase = AckPhase.Done, status = AckStatus.Failed, reason = "x", timestamp = 12345L)
        assertThat(ack.canonicalString()).isEqualTo("c|7|Done|Failed|x||12345")
    }

    @Test
    fun ackCanonicalStringNullReasonIsEmpty() {
        val ack = ackDraft(clientId = "c", commandCounter = 7L, phase = AckPhase.Executing, status = AckStatus.Pending, reason = null, timestamp = 12345L)
        assertThat(ack.canonicalString()).isEqualTo("c|7|Executing|Pending|||12345")
    }

    @Test
    fun ackCanonicalStringIncludesPayload() {
        val ack = ackDraft(clientId = "c", commandCounter = 7L, phase = AckPhase.Done, status = AckStatus.Ok, reason = null, payload = "{\"bolusId\":42}", timestamp = 12345L)
        assertThat(ack.canonicalString()).isEqualTo("c|7|Done|Ok||{\"bolusId\":42}|12345")
    }

    @Test
    fun ackVerifyRejectsTamperedPayload() {
        // A forged dose must be no more believable than a forged "Ok" — the payload is HMAC-covered.
        val secret = ClientControlCrypto.newSecretBytes()
        val signed = ClientControlCrypto.signAck(secret, ackDraft(status = AckStatus.Ok, payload = "{\"bolusId\":42,\"insulin\":1.0}"))
        val tampered = signed.copy(payload = "{\"bolusId\":42,\"insulin\":9.9}")
        assertThat(ClientControlCrypto.verifyAck(secret, tampered)).isFalse()
    }
}
