package app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairMessage
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding.Companion.parseKeys
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.pump.omnipod.common.bledriver.pod.util.RandomByteGenerator
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.CCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter

/**
 * Drives the Omnipod 5 pairing message sequence over BLE: SP1+SP2, SPS0, SPS1, SPS2.1,
 * SPS2, SP0GP0, P0 - using [O5KeyExchange] for the key agreement/nonces/transcripts and
 * [O5CertificateStore] for certificate data and channel-binding signatures.
 *
 * Ported from OmnipodKit's O5LTKExchanger.swift (loopandlearn/OmnipodKit).
 *
 * Unlike the Swift original (which resolves `myId`/`podId` via a Dash-specific `Ids`
 * helper tied to `OmnipodDashBleManagerImpl`/`OmnipodDashPodStateManager`), this takes
 * [myId] and [podId] directly - O5 has its own certificate-derived controller identity
 * (see [O5CertificateStore.controllerId]) rather than sharing Dash's `Ids` scheme, and
 * wiring those together for a live pod is a separate integration step.
 */
class O5LTKExchanger(
    private val aapsLogger: AAPSLogger,
    private val msgIO: MessageIO,
    private val certStore: O5CertificateStore,
    private val myId: Id,
    private val podId: Id
) {

    private val podAddress: Id = Id.fromLong(PodScanner.POD_ID_NOT_ACTIVATED)
    private val keyExchange: O5KeyExchange = O5KeyExchange(
        aapsLogger,
        P256KeyGenerator(),
        RandomByteGenerator(),
        certStore.controllerIdData
    )
    private var seq: Byte = 1

    fun o5NegotiateLTK(): PairResult {
        aapsLogger.debug(LTag.PUMPBTCOMM, "O5 pairing attempt using fixed confirmed parameters")
        try {
            val result = o5NegotiateLTKBody()
            aapsLogger.debug(LTag.PUMPBTCOMM, "O5 pairing SUCCEEDED")
            return result
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "O5 pairing FAILED: $e")
            throw e
        }
    }

    private fun o5NegotiateLTKBody(): PairResult {
        val sp1sp2 = PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            keys = arrayOf(SP1, SP2),
            payloads = arrayOf(podId.address, o5Sp2(podId))
        )
        throwOnSendError(sp1sp2.messagePacket, SP1 + SP2)

        seq++
        val sps0 = PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            keys = arrayOf(SPS0),
            payloads = arrayOf(o5Sps0())
        )
        throwOnSendError(sps0.messagePacket, SPS0)

        val podSps0 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS0")
        validatePodO5Sps0(podSps0)

        seq++
        val sps1 = PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            keys = arrayOf(SPS1),
            payloads = arrayOf(keyExchange.pdmPublic + keyExchange.pdmNonce)
        )
        throwOnSendError(sps1.messagePacket, SPS1)

        val podSps1 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS1")
        o5ValidatePodSps1(podSps1)

        seq++
        val sps2_1 = PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            keys = arrayOf(SPS2_1),
            payloads = arrayOf(o5Sps2_1())
        )
        throwOnSendError(sps2_1.messagePacket, SPS2_1)

        val podSps2_1 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS2.1")
        o5ValidatePodSps2_1(podSps2_1)

        seq++
        val sps2 = PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            keys = arrayOf(SPS2),
            payloads = arrayOf(o5Sps2())
        )
        throwOnSendError(sps2.messagePacket, SPS2)

        val podSps2 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS2")
        o5ValidatePodSps2(podSps2)

        seq++
        val sp0gp0 = PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            keys = arrayOf(SP0GP0),
            payloads = arrayOf(ByteArray(0))
        )
        val result = msgIO.sendMessage(sp0gp0.messagePacket)
        if (result !is MessageSendSuccess) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Error sending SP0GP0: $result")
        }

        msgIO.receiveMessage()
            ?.let { o5ValidateP0(it) }
            ?: aapsLogger.warn(LTag.PUMPBTCOMM, "Could not read P0")

        check(keyExchange.ltk.size == 16) { "Invalid LTK size: ${keyExchange.ltk.size}" }

        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "=== O5 Pairing Complete === address: 0x%08x, seq: %d".format(podId.toLong(), seq)
        )
        return PairResult(ltk = keyExchange.ltk, msgSeq = seq)
    }

    private fun throwOnSendError(msg: MessagePacket, msgType: String) {
        val result = msgIO.sendMessage(msg)
        if (result !is MessageSendSuccess) {
            throw PairingException("Could not send or confirm $msgType: $result")
        }
    }


    private fun o5Sp2(podId: Id): ByteArray {
        val body = byteArrayOf(0x0e, 0x01, 0x00)
        val b9: Byte = 0x00
        val withoutCrc = podId.address + byteArrayOf(b9, body.size.toByte()) + body
        val crc = classicCrc16(withoutCrc)
        return withoutCrc + byteArrayOf((crc ushr 8).toByte(), (crc and 0xFF).toByte())
    }


    /**
     * Generates the 5-byte SPS0 payload with CRC-16/XMODEM.
     * Structure: [constant_byte, direction_byte, algorithm_byte, crc16_high, crc16_low].
     * Direction: 0x01 for PDM->Pod. Algorithm byte 0x09 specifies the encryption algorithm.
     */
    private fun o5Sps0(): ByteArray {
        val header = byteArrayOf(0x00, 0x01, 0x09)
        val crc = crc16XMODEM(header)
        return header + byteArrayOf((crc ushr 8).toByte(), (crc and 0xFF).toByte())
    }

    /** Validates the pod's 5-byte SPS0 response (pod's direction byte is 0x00, vs PDM's 0x01). */
    private fun validatePodO5Sps0(msg: MessagePacket) {
        val payload = parseKeys(arrayOf(SPS0), msg.payload)[0]
        if (payload.size != 5) {
            throw PairingException("Invalid SPS0 payload length: ${payload.size}")
        }
        if (payload[0] != 0x00.toByte() || payload[1] != 0x00.toByte() || payload[2] != 0x09.toByte()) {
            throw PairingException("Unexpected SPS0 header bytes")
        }
        val header = payload.copyOfRange(0, 3)
        val expectedCrc = crc16XMODEM(header)
        val receivedCrc = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
        if (expectedCrc != receivedCrc) {
            throw PairingException(
                "SPS0 CRC mismatch: expected %04x, received %04x".format(expectedCrc, receivedCrc)
            )
        }
    }


    private fun o5ValidatePodSps1(msg: MessagePacket) {
        val payload = parseKeys(arrayOf(SPS1), msg.payload)[0]
        keyExchange.o5UpdatePodPublicData(payload)
    }


    /**
     * Builds and encrypts the SPS2.1 payload: `AES_CCM_ENC(intermediateCA_DER) || tag(8)`.
     * The ECDSA channel-binding signature goes in SPS2, not here.
     */
    private fun o5Sps2_1(): ByteArray {
        val certDER = certStore.registration.intermediateCA
            ?: throw PairingException("SPS2.1: intermediate CA certificate DER is null")

        aapsLogger.debug(LTag.PUMPBTCOMM, "=== SPS2.1 PHASE START ===")
        aapsLogger.debug(LTag.PUMPBTCOMM, "SPS2.1: using cert (${certDER.size} bytes, cert-only short path)")

        val nonce = keyExchange.getSPSNonce(O5KeyExchange.Direction.WRITE)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "Encrypting SPS2.1: key=${keyExchange.conf.toHex()}, nonce=${nonce.toHex()}, plaintext=${certDER.size} bytes"
        )
        val encrypted = aesCcmEncrypt(keyExchange.conf, nonce, certDER)
        keyExchange.incrementNonce(O5KeyExchange.Direction.WRITE)
        aapsLogger.debug(LTag.PUMPBTCOMM, "SPS2.1 encrypted: ${encrypted.size} bytes (target=${certDER.size + MAC_SIZE_BYTES})")
        return encrypted
    }

    /** Decrypts and validates the pod's SPS2.1 response (short path: cert only). */
    private fun o5ValidatePodSps2_1(msg: MessagePacket) {
        val payload = parseKeys(arrayOf(SPS2_1), msg.payload)[0]
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received pod SPS2.1: ${payload.size} bytes")

        val nonce = keyExchange.getSPSNonce(O5KeyExchange.Direction.READ)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "Decrypting pod SPS2.1: key=${keyExchange.conf.toHex()}, nonce=${nonce.toHex()}, ciphertext=${payload.size} bytes"
        )
        val podCertDER = aesCcmDecrypt(keyExchange.conf, nonce, payload)
        keyExchange.incrementNonce(O5KeyExchange.Direction.READ)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Pod SPS2.1 decrypted: ${podCertDER.size} bytes (pod cert DER, short path)")

        if (O5CertificateStore.extractP256PublicKey(podCertDER) == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Failed to extract P-256 public key from pod SPS2.1 certificate DER")
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "=== SPS2.1 PHASE COMPLETE ===")
    }


    /**
     * Builds and encrypts the SPS2 payload: `AES_CCM_ENC(TLS_cert_DER || ECDSA_sig(64)) || tag(8)`.
     * The 64-byte signature is over the 171-byte channel-binding transcript, signed with
     * the secondary key.
     */
    private fun o5Sps2(): ByteArray {
        val certDER = certStore.registration.tlsCertificate
            ?: throw PairingException("SPS2: TLS certificate DER is null")

        aapsLogger.debug(LTag.PUMPBTCOMM, "=== SPS2 PHASE START ===")

        val transcript = keyExchange.buildChannelBindingTranscript()
        aapsLogger.debug(LTag.PUMPBTCOMM, "Channel-binding transcript (${transcript.size} bytes): ${transcript.toHex()}")

        val signatureRaw = certStore.signRaw(transcript)
        aapsLogger.debug(LTag.PUMPBTCOMM, "ECDSA signature (${signatureRaw.size} bytes): ${signatureRaw.toHex()}")

        val plaintext = certDER + signatureRaw
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "SPS2: TLS cert (${certDER.size} bytes) + sig (${signatureRaw.size}) = ${plaintext.size} plaintext"
        )

        val nonce = keyExchange.getSPSNonce(O5KeyExchange.Direction.WRITE)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "Encrypting SPS2: key=${keyExchange.conf.toHex()}, nonce=${nonce.toHex()}, plaintext=${plaintext.size} bytes"
        )
        val encrypted = aesCcmEncrypt(keyExchange.conf, nonce, plaintext)
        keyExchange.incrementNonce(O5KeyExchange.Direction.WRITE)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "SPS2 encrypted: ${encrypted.size} bytes (target=${certDER.size + signatureRaw.size + MAC_SIZE_BYTES})"
        )
        return encrypted
    }

    /** Decrypts and validates the pod's SPS2 response (extended path: cert + signature). */
    private fun o5ValidatePodSps2(msg: MessagePacket) {
        val payload = parseKeys(arrayOf(SPS2), msg.payload)[0]
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received pod SPS2: ${payload.size} bytes")

        val nonce = keyExchange.getSPSNonce(O5KeyExchange.Direction.READ)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "Decrypting pod SPS2: key=${keyExchange.conf.toHex()}, nonce=${nonce.toHex()}, ciphertext=${payload.size} bytes"
        )
        val decrypted = aesCcmDecrypt(keyExchange.conf, nonce, payload)
        keyExchange.incrementNonce(O5KeyExchange.Direction.READ)

        if (decrypted.size <= 64) {
            throw PairingException("Pod SPS2 payload too short: ${decrypted.size} bytes (need > 64)")
        }
        val certLen = decrypted.size - 64
        val podCertDER = decrypted.copyOfRange(0, certLen)
        val podSignature = decrypted.copyOfRange(certLen, decrypted.size)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "Pod SPS2 decrypted: ${decrypted.size} bytes (cert_DER=$certLen + sig=${podSignature.size})"
        )
        aapsLogger.debug(LTag.PUMPBTCOMM, "Pod signature (${podSignature.size} bytes): ${podSignature.toHex()}")

        aapsLogger.debug(LTag.PUMPBTCOMM, "=== SPS2 PHASE COMPLETE ===")
    }


    private fun o5ValidateP0(msg: MessagePacket) {
        val payload = parseKeys(arrayOf(P0), msg.payload)[0]
        if (!payload.contentEquals(EXPECTED_P0_PAYLOAD)) {
            throw PairingException(
                "Received unexpected P0 payload: ${payload.joinToString("") { "%02x".format(it) }}"
            )
        }
    }


    private fun aesCcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = CCMBlockCipher(AESEngine())
        cipher.init(true, AEADParameters(KeyParameter(key), MAC_SIZE_BITS, nonce, ByteArray(0)))
        val output = ByteArray(plaintext.size + MAC_SIZE_BYTES)
        cipher.processPacket(plaintext, 0, plaintext.size, output, 0)
        return output
    }

    private fun aesCcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = CCMBlockCipher(AESEngine())
        cipher.init(false, AEADParameters(KeyParameter(key), MAC_SIZE_BITS, nonce, ByteArray(0)))
        val output = ByteArray(ciphertext.size - MAC_SIZE_BYTES)
        cipher.processPacket(ciphertext, 0, ciphertext.size, output, 0)
        return output
    }

    companion object {

        private const val SP1 = "SP1="
        private const val SP2 = ",SP2="
        private const val SPS0 = "SPS0="
        private const val SPS1 = "SPS1="
        private const val SPS2_1 = "SPS2.1="
        private const val SPS2 = "SPS2="
        private const val SP0GP0 = "SP0,GP0"
        private const val P0 = "P0="
        private val EXPECTED_P0_PAYLOAD = byteArrayOf(0xa5.toByte())

        private const val MAC_SIZE_BYTES = 8
        private const val MAC_SIZE_BITS = MAC_SIZE_BYTES * 8

        /**
         * The CRC-16 variant (and 256-entry table) used by OmnipodKit's own Message.swift
         * for the classic Omnipod message envelope in [o5Sp2]. Despite the table's
         * resemblance to CRC-16/ARC's polynomial, it does NOT match CRC-16/ARC's published
         * check value for "123456789" - this specific table+algorithm combination is
         * whatever OmnipodKit itself uses, verified here only for faithful byte-for-byte
         * reproduction of that source (see the class's test suite), not for matching any
         * named textbook CRC-16 standard. This is a DIFFERENT variant from [crc16XMODEM]
         * (used only for SPS0) - the two must not be confused.
         */
        fun classicCrc16(data: ByteArray): Int {
            var acc = 0
            for (byte in data) {
                val idx = (acc xor (byte.toInt() and 0xFF)) and 0xFF
                acc = (acc ushr 8) xor CLASSIC_CRC16_TABLE[idx]
            }
            return acc and 0xFFFF
        }

        /** CRC-16/XMODEM (poly 0x1021, init 0x0000, no reflection), used only for SPS0. */
        fun crc16XMODEM(data: ByteArray): Int {
            var crc = 0x0000
            for (byte in data) {
                crc = crc xor ((byte.toInt() and 0xFF) shl 8)
                repeat(8) {
                    crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                    crc = crc and 0xFFFF
                }
            }
            return crc and 0xFFFF
        }

        private val CLASSIC_CRC16_TABLE: IntArray = intArrayOf(
            0x0000, 0x8005, 0x800f, 0x000a, 0x801b, 0x001e, 0x0014, 0x8011,
            0x8033, 0x0036, 0x003c, 0x8039, 0x0028, 0x802d, 0x8027, 0x0022,
            0x8063, 0x0066, 0x006c, 0x8069, 0x0078, 0x807d, 0x8077, 0x0072,
            0x0050, 0x8055, 0x805f, 0x005a, 0x804b, 0x004e, 0x0044, 0x8041,
            0x80c3, 0x00c6, 0x00cc, 0x80c9, 0x00d8, 0x80dd, 0x80d7, 0x00d2,
            0x00f0, 0x80f5, 0x80ff, 0x00fa, 0x80eb, 0x00ee, 0x00e4, 0x80e1,
            0x00a0, 0x80a5, 0x80af, 0x00aa, 0x80bb, 0x00be, 0x00b4, 0x80b1,
            0x8093, 0x0096, 0x009c, 0x8099, 0x0088, 0x808d, 0x8087, 0x0082,
            0x8183, 0x0186, 0x018c, 0x8189, 0x0198, 0x819d, 0x8197, 0x0192,
            0x01b0, 0x81b5, 0x81bf, 0x01ba, 0x81ab, 0x01ae, 0x01a4, 0x81a1,
            0x01e0, 0x81e5, 0x81ef, 0x01ea, 0x81fb, 0x01fe, 0x01f4, 0x81f1,
            0x81d3, 0x01d6, 0x01dc, 0x81d9, 0x01c8, 0x81cd, 0x81c7, 0x01c2,
            0x0140, 0x8145, 0x814f, 0x014a, 0x815b, 0x015e, 0x0154, 0x8151,
            0x8173, 0x0176, 0x017c, 0x8179, 0x0168, 0x816d, 0x8167, 0x0162,
            0x8123, 0x0126, 0x012c, 0x8129, 0x0138, 0x813d, 0x8137, 0x0132,
            0x0110, 0x8115, 0x811f, 0x011a, 0x810b, 0x010e, 0x0104, 0x8101,
            0x8303, 0x0306, 0x030c, 0x8309, 0x0318, 0x831d, 0x8317, 0x0312,
            0x0330, 0x8335, 0x833f, 0x033a, 0x832b, 0x032e, 0x0324, 0x8321,
            0x0360, 0x8365, 0x836f, 0x036a, 0x837b, 0x037e, 0x0374, 0x8371,
            0x8353, 0x0356, 0x035c, 0x8359, 0x0348, 0x834d, 0x8347, 0x0342,
            0x03c0, 0x83c5, 0x83cf, 0x03ca, 0x83db, 0x03de, 0x03d4, 0x83d1,
            0x83f3, 0x03f6, 0x03fc, 0x83f9, 0x03e8, 0x83ed, 0x83e7, 0x03e2,
            0x83a3, 0x03a6, 0x03ac, 0x83a9, 0x03b8, 0x83bd, 0x83b7, 0x03b2,
            0x0390, 0x8395, 0x839f, 0x039a, 0x838b, 0x038e, 0x0384, 0x8381,
            0x0280, 0x8285, 0x828f, 0x028a, 0x829b, 0x029e, 0x0294, 0x8291,
            0x82b3, 0x02b6, 0x02bc, 0x82b9, 0x02a8, 0x82ad, 0x82a7, 0x02a2,
            0x82e3, 0x02e6, 0x02ec, 0x82e9, 0x02f8, 0x82fd, 0x82f7, 0x02f2,
            0x02d0, 0x82d5, 0x82df, 0x02da, 0x82cb, 0x02ce, 0x02c4, 0x82c1,
            0x8243, 0x0246, 0x024c, 0x8249, 0x0258, 0x825d, 0x8257, 0x0252,
            0x0270, 0x8275, 0x827f, 0x027a, 0x826b, 0x026e, 0x0264, 0x8261,
            0x0220, 0x8225, 0x822f, 0x022a, 0x823b, 0x023e, 0x0234, 0x8231,
            0x8213, 0x0216, 0x021c, 0x8219, 0x0208, 0x820d, 0x8207, 0x0202
        )
    }
}
