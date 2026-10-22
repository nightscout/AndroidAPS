package app.aaps.pump.omnipod.omnipod5.bledriver.comm.session
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5CertificateStore
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.common.bledriver.pod.command.DeactivateCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers [Session]'s O5-only Type-4 (ENCRYPTED_SIGNED) command signing - the fix for real
 * O5 pod firmware rejecting insulin-schedule/deactivate/cancel-delivery commands sent as
 * plain ENCRYPTED (confirmed against OmnipodKit's BleMessageTransport.swift, see
 * `Session.sign()`'s doc comment). Uses a mocked [MessageIO] (the actual BLE I/O boundary,
 * same approach as [app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5LTKExchangerTest])
 * plus a real [O5CertificateStore] built from generated P-256 test key material, so the
 * signature produced is independently re-verifiable rather than just "some bytes got
 * appended".
 */
class SessionSigningTest {

    private val aapsLogger = AAPSLoggerTest()
    private val keyGenerator = P256KeyGenerator()
    private val usedControllerIds = mutableListOf<Long>()

    @AfterEach
    fun tearDown() {
        usedControllerIds.forEach { O5RegistrationData.remove(it) }
        usedControllerIds.clear()
    }

    private fun installValidRegistration(controllerId: Long) {
        usedControllerIds.add(controllerId)
        val privateKey = keyGenerator.generatePrivateKey()
        val publicKey = keyGenerator.publicFromPrivate(privateKey)
        O5RegistrationData.install(
            O5RegistrationData(
                controllerId = controllerId,
                privateKeyHex = privateKey.toHex(),
                publicKeyHex = publicKey.toHex(),
                intermediateCABase64 = "",
                tlsCertificateBase64 = ""
            ),
            source = O5RegistrationData.O5RegistrationSource.DOWNLOADED,
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun buildSession(msgIO: MessageIO, commandSigner: O5CertificateStore?): Session {
        val ids = Ids.forController(Id.fromInt(1), Id.fromInt(2))
        val nonce = Nonce(ByteArray(8), 0)
        val sessionKeys = SessionKeys(ck = ByteArray(16), nonce = nonce, msgSequenceNumber = 1)
        val enDecrypt = EnDecrypt(aapsLogger, nonce, sessionKeys.ck)
        return Session(aapsLogger, msgIO, ids, sessionKeys, enDecrypt, commandSigner)
    }

    private fun sentPacket(msgIO: MessageIO): MessagePacket {
        val captor = argumentCaptor<MessagePacket>()
        verify(msgIO).sendMessage(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `deactivate command is sent Type-4 signed, with a signature that verifies, when a signer is present`() {
        val controllerId = 0x11112222L
        installValidRegistration(controllerId)
        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        val session = buildSession(msgIO, commandSigner = store)
        val cmd = DeactivateCommand.Builder().setUniqueId(1).setSequenceNumber(1).setNonce(0).build()

        session.sendCommand(cmd)

        val sent = sentPacket(msgIO)
        assertThat(sent.type).isEqualTo(MessageType.ENCRYPTED_SIGNED)
        assertThat(sent.signatureData).isNotNull()
        assertThat(sent.signatureData!!.size).isEqualTo(64)
        val aad = sent.asByteArray(forEncryption = false).copyOfRange(0, 16)
        assertThat(
            O5CertificateStore.verifySignature(sent.signatureData!!, aad + sent.payload, store.signingPublicKeyRaw)
        ).isTrue()
    }

    @Test
    fun `a command outside the signed set is sent plain ENCRYPTED even when a signer is present`() {
        val controllerId = 0x33334444L
        installValidRegistration(controllerId)
        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        val session = buildSession(msgIO, commandSigner = store)
        val cmd = GetStatusCommand.Builder()
            .setUniqueId(1).setSequenceNumber(1)
            .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
            .build()

        session.sendCommand(cmd)

        val sent = sentPacket(msgIO)
        assertThat(sent.type).isEqualTo(MessageType.ENCRYPTED)
        assertThat(sent.signatureData).isNull()
    }

    @Test
    fun `a deactivate command is never signed without a commandSigner - matches Dash, which has none`() {
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        val session = buildSession(msgIO, commandSigner = null)
        val cmd = DeactivateCommand.Builder().setUniqueId(1).setSequenceNumber(1).setNonce(0).build()

        session.sendCommand(cmd)

        val sent = sentPacket(msgIO)
        assertThat(sent.type).isEqualTo(MessageType.ENCRYPTED)
        assertThat(sent.signatureData).isNull()
    }
}
