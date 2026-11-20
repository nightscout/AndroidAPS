package app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.CCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64

/**
 * [O5LTKExchanger] - the two CRC-16 companion functions (pure, no mocking needed), the
 * `o5NegotiateLTK()` orchestration's failure paths (send/receive failures, malformed
 * responses - reachable without simulating real crypto), and one full happy-path test that
 * *does* simulate a well-behaved pod's crypto responses (real ECDH + the same SHA-256 KDF
 * and AES-CCM the production code uses), to verify the handshake's sequencing/nonce
 * bookkeeping produces a correct, matching LTK end to end at the software layer.
 *
 * This does NOT verify the wire-format assumptions themselves are correct against a real
 * pod (nothing here can, without hardware) - see [O5CertificateStore]'s doc comment and
 * this project's own notes on `o5ValidatePodSps2`'s "transcript format may differ" comment
 * for that caveat.
 */
class O5LTKExchangerTest {

    @Test
    fun `crc16XMODEM matches the standard CRC-16-XMODEM check value`() {
        assertThat(O5LTKExchanger.crc16XMODEM("123456789".toByteArray())).isEqualTo(0x31C3)
    }

    @Test
    fun `crc16XMODEM of empty input is zero`() {
        assertThat(O5LTKExchanger.crc16XMODEM(ByteArray(0))).isEqualTo(0)
    }

    @Test
    fun `classicCrc16 is deterministic and sensitive to every input byte`() {
        val a = O5LTKExchanger.classicCrc16(byteArrayOf(0x01, 0x02, 0x03))
        val aAgain = O5LTKExchanger.classicCrc16(byteArrayOf(0x01, 0x02, 0x03))
        val b = O5LTKExchanger.classicCrc16(byteArrayOf(0x01, 0x02, 0x04))

        assertThat(a).isEqualTo(aAgain)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `classicCrc16 and crc16XMODEM are different algorithms, not accidentally identical`() {
        val data = "regression guard".toByteArray()

        assertThat(O5LTKExchanger.classicCrc16(data)).isNotEqualTo(O5LTKExchanger.crc16XMODEM(data))
    }

    @Test
    fun `classicCrc16 of empty input is zero`() {
        assertThat(O5LTKExchanger.classicCrc16(ByteArray(0))).isEqualTo(0)
    }


    private val aapsLogger = AAPSLoggerTest()
    private val p256KeyGenerator = P256KeyGenerator()
    private val usedControllerIds = mutableListOf<Long>()
    private val dummyId = Id.fromLong(0xDEADBEEFL and 0xFFFFFFFFL)

    @AfterEach
    fun tearDownRegistrations() {
        usedControllerIds.forEach { O5RegistrationData.remove(it) }
        usedControllerIds.clear()
    }

    private fun newCertStore(controllerId: Long, withIntermediateCA: Boolean = true, withTlsCertificate: Boolean = true): O5CertificateStore {
        usedControllerIds.add(controllerId)
        val privateKey = p256KeyGenerator.generatePrivateKey()
        val publicKey = p256KeyGenerator.publicFromPrivate(privateKey)
        O5RegistrationData.install(
            O5RegistrationData(
                controllerId = controllerId,
                privateKeyHex = privateKey.joinToString("") { "%02x".format(it) },
                publicKeyHex = publicKey.joinToString("") { "%02x".format(it) },
                intermediateCABase64 = if (withIntermediateCA) Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)) else "",
                tlsCertificateBase64 = if (withTlsCertificate) Base64.getEncoder().encodeToString(byteArrayOf(5, 6, 7, 8)) else ""
            ),
            source = O5RegistrationData.O5RegistrationSource.DOWNLOADED,
        )
        return O5CertificateStore(aapsLogger, p256KeyGenerator, controllerId)
    }

    private fun pairingPacket(key: String, payload: ByteArray, sequenceNumber: Byte): MessagePacket =
        MessagePacket(
            type = MessageType.PAIRING,
            source = dummyId,
            destination = dummyId,
            payload = StringLengthPrefixEncoding.formatKeys(arrayOf(key), arrayOf(payload)),
            sequenceNumber = sequenceNumber
        )

    private fun incrementNonce(nonce: ByteArray): ByteArray {
        val counter = ByteBuffer.wrap(nonce, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val result = nonce.copyOf()
        ByteBuffer.wrap(result, 0, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter + 1)
        return result
    }

    /** Replicates O5KeyExchange's private o5GenerateKeys() KDF exactly, so the test can
     *  independently derive what conf/ltk the code under test will arrive at. */
    private fun deriveConfAndLtk(pdmPublic: ByteArray, podPublic: ByteArray, sharedSecret: ByteArray): Pair<ByteArray, ByteArray> {
        fun lengthPrefixed(value: Int): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value.toLong()).array()
        val firmwareId = O5KeyExchange.FIRMWARE_ID
        val data = lengthPrefixed(firmwareId.size) + firmwareId +
            lengthPrefixed(4) + byteArrayOf(0, 0, 0, 0) +
            lengthPrefixed(pdmPublic.size) + pdmPublic +
            lengthPrefixed(podPublic.size) + podPublic +
            lengthPrefixed(sharedSecret.size) + sharedSecret
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.copyOfRange(0, 16) to digest.copyOfRange(16, 32)
    }

    private fun aesCcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = CCMBlockCipher(AESEngine())
        cipher.init(true, AEADParameters(KeyParameter(key), 64, nonce, ByteArray(0)))
        val output = ByteArray(plaintext.size + 8)
        cipher.processPacket(plaintext, 0, plaintext.size, output, 0)
        return output
    }

    @Test
    fun `o5NegotiateLTK throws when the SP1+SP2 send fails`() {
        val certStore = newCertStore(0x10000001L)
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendErrorSending("boom"))

        assertThrows(PairingException::class.java) {
            O5LTKExchanger(aapsLogger, msgIO, certStore, dummyId, dummyId.increment()).o5NegotiateLTK()
        }
    }

    @Test
    fun `o5NegotiateLTK throws when SPS0 is never received`() {
        val certStore = newCertStore(0x10000002L)
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        whenever(msgIO.receiveMessage()).thenReturn(null)

        val exception = assertThrows(PairingException::class.java) {
            O5LTKExchanger(aapsLogger, msgIO, certStore, dummyId, dummyId.increment()).o5NegotiateLTK()
        }
        assertThat(exception.message).contains("SPS0")
    }

    @Test
    fun `o5NegotiateLTK throws when the pod's SPS0 CRC does not match`() {
        val certStore = newCertStore(0x10000003L)
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        whenever(msgIO.receiveMessage()).thenReturn(pairingPacket("SPS0=", byteArrayOf(0x00, 0x00, 0x09, 0x00, 0x00), sequenceNumber = 2))

        assertThrows(PairingException::class.java) {
            O5LTKExchanger(aapsLogger, msgIO, certStore, dummyId, dummyId.increment()).o5NegotiateLTK()
        }
    }

    @Test
    fun `o5NegotiateLTK throws when SPS1 is never received`() {
        val certStore = newCertStore(0x10000004L)
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        var callIndex = 0
        whenever(msgIO.receiveMessage()).thenAnswer {
            callIndex++
            if (callIndex == 1) {
                val header = byteArrayOf(0x00, 0x00, 0x09)
                val crc = O5LTKExchanger.crc16XMODEM(header)
                pairingPacket("SPS0=", header + byteArrayOf((crc ushr 8).toByte(), (crc and 0xFF).toByte()), sequenceNumber = 2)
            } else {
                null
            }
        }

        val exception = assertThrows(PairingException::class.java) {
            O5LTKExchanger(aapsLogger, msgIO, certStore, dummyId, dummyId.increment()).o5NegotiateLTK()
        }
        assertThat(exception.message).contains("SPS1")
    }

    @Test
    fun `o5NegotiateLTK throws MessageIOException when the pod's SPS1 payload is the wrong size`() {
        val certStore = newCertStore(0x10000005L)
        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        var callIndex = 0
        whenever(msgIO.receiveMessage()).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> {
                    val header = byteArrayOf(0x00, 0x00, 0x09)
                    val crc = O5LTKExchanger.crc16XMODEM(header)
                    pairingPacket("SPS0=", header + byteArrayOf((crc ushr 8).toByte(), (crc and 0xFF).toByte()), sequenceNumber = 2)
                }

                2 -> pairingPacket("SPS1=", ByteArray(10), sequenceNumber = 3)
                else -> null
            }
        }

        assertThrows(MessageIOException::class.java) {
            O5LTKExchanger(aapsLogger, msgIO, certStore, dummyId, dummyId.increment()).o5NegotiateLTK()
        }
    }

    @Test
    fun `o5NegotiateLTK completes the full handshake and derives a matching LTK`() {
        val certStore = newCertStore(0x10000006L)
        val myId = Id.fromLong(certStore.controllerId)
        val podId = myId.increment()

        val podPrivate = p256KeyGenerator.generatePrivateKey()
        val podPublic = p256KeyGenerator.publicFromPrivate(podPrivate)
        val podNonceP0 = ByteArray(16) { (it + 1).toByte() }

        var capturedPdmPublic: ByteArray? = null
        var capturedPdmNonceN0: ByteArray? = null

        val msgIO = mock<MessageIO>()
        whenever(msgIO.sendMessage(any())).thenAnswer { invocation ->
            val msg = invocation.getArgument<MessagePacket>(0)
            runCatching { StringLengthPrefixEncoding.parseKeys(arrayOf("SPS1="), msg.payload) }
                .getOrNull()
                ?.takeIf { it[0].size == 80 }
                ?.let { parsed ->
                    capturedPdmPublic = parsed[0].copyOfRange(0, 64)
                    capturedPdmNonceN0 = parsed[0].copyOfRange(64, 80)
                }
            MessageSendSuccess
        }

        var callIndex = 0
        whenever(msgIO.receiveMessage()).thenAnswer {
            callIndex++
            when (callIndex) {
                1 -> {
                    val header = byteArrayOf(0x00, 0x00, 0x09)
                    val crc = O5LTKExchanger.crc16XMODEM(header)
                    pairingPacket("SPS0=", header + byteArrayOf((crc ushr 8).toByte(), (crc and 0xFF).toByte()), sequenceNumber = 2)
                }

                2 -> {
                    pairingPacket("SPS1=", podPublic + podNonceP0, sequenceNumber = 3)
                }

                3 -> {
                    val n1 = incrementNonce(requireNotNull(capturedPdmNonceN0))
                    val nonce = byteArrayOf(0x02) + podNonceP0.copyOfRange(0, 6) + n1.copyOfRange(0, 6)
                    val sharedSecret = p256KeyGenerator.computeSharedSecret(podPrivate, requireNotNull(capturedPdmPublic))
                    val (conf, _) = deriveConfAndLtk(requireNotNull(capturedPdmPublic), podPublic, sharedSecret)
                    pairingPacket("SPS2.1=", aesCcmEncrypt(conf, nonce, ByteArray(20)), sequenceNumber = 4)
                }

                4 -> {
                    val n1 = incrementNonce(requireNotNull(capturedPdmNonceN0))
                    val n2 = incrementNonce(n1)
                    val p1 = incrementNonce(podNonceP0)
                    val nonce = byteArrayOf(0x02) + p1.copyOfRange(0, 6) + n2.copyOfRange(0, 6)
                    val sharedSecret = p256KeyGenerator.computeSharedSecret(podPrivate, requireNotNull(capturedPdmPublic))
                    val (conf, _) = deriveConfAndLtk(requireNotNull(capturedPdmPublic), podPublic, sharedSecret)
                    pairingPacket("SPS2=", aesCcmEncrypt(conf, nonce, ByteArray(70)), sequenceNumber = 5)
                }

                5 -> pairingPacket("P0=", byteArrayOf(0xa5.toByte()), sequenceNumber = 6)
                else -> null
            }
        }

        val result = O5LTKExchanger(aapsLogger, msgIO, certStore, myId, podId).o5NegotiateLTK()

        val sharedSecret = p256KeyGenerator.computeSharedSecret(podPrivate, requireNotNull(capturedPdmPublic))
        val (_, expectedLtk) = deriveConfAndLtk(requireNotNull(capturedPdmPublic), podPublic, sharedSecret)
        assertThat(result.ltk).isEqualTo(expectedLtk)
        assertThat(result.msgSeq).isEqualTo(6.toByte())
    }
}
