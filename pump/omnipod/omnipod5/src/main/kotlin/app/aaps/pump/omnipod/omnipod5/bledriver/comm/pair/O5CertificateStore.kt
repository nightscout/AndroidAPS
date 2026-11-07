package app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.pair.CommandSigner
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256Codec
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import java.math.BigInteger
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64

/**
 * Manages the PKI material needed for Omnipod 5 pairing and pod command authentication.
 * Key/certificate data comes from [O5RegistrationData] - swap the underlying registration
 * to use different registration data.
 *
 * Ported from OmnipodKit's O5CertificateStore.swift (loopandlearn/OmnipodKit). Two
 * deliberate differences from the Swift original, both explained inline below:
 * 1. Certificate field extraction (public key + serial number) is ported as a faithful,
 *    hand-rolled byte-level parser rather than switched to a full ASN.1 library, so every
 *    line here could be verified against real, JDK-generated X.509 certificates without
 *    depending on an external library this environment couldn't compile-check.
 * 2. DER signature encoding/decoding is similarly hand-rolled rather than delegated to a
 *    library, for the same verifiability reason.
 *
 * @throws PairingException if no registration data is available for [controllerId], or if
 *   the registration's stored public key doesn't match the one derived from its private key
 *   (which would indicate corrupted or tampered registration data).
 */
class O5CertificateStore(
    private val aapsLogger: AAPSLogger,
    private val p256KeyGenerator: P256KeyGenerator,
    controllerId: Long
) : CommandSigner {

    val registration: O5RegistrationData =
        O5RegistrationData.get(controllerId)
            ?: throw PairingException("No O5 registration data found for controller id 0x%08X".format(controllerId))

    val controllerId: Long get() = registration.controllerId
    val controllerIdData: ByteArray get() = registration.controllerIdData

    private val signingPrivateKeyRaw: ByteArray = registration.privateKey

    /** The secondary P-256 signing key's raw (65-byte, uncompressed) public key. */
    val signingPublicKeyRaw: ByteArray

    init {
        require(signingPrivateKeyRaw.size == P256Codec.SCALAR_LENGTH_BYTES) {
            "Secondary key scalar must be exactly ${P256Codec.SCALAR_LENGTH_BYTES} bytes"
        }

        signingPublicKeyRaw = p256KeyGenerator.publicFromPrivate(signingPrivateKeyRaw)

        if (!signingPublicKeyRaw.contentEquals(registration.publicKey)) {
            aapsLogger.error(
                LTag.PUMPBTCOMM,
                "O5 signing public key does NOT match expected secondary public key for controller 0x%08X"
                    .format(controllerId)
            )
            throw PairingException(
                "O5 signing public key mismatch for controller id 0x%08X".format(controllerId)
            )
        }

        val tlsCert = registration.tlsCertificate
        val intermediateCA = registration.intermediateCA
        val tlsSerial = tlsCert?.let { extractSerialNumber(it) }
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "O5CertificateStore initialized for controller 0x%08X (source=%s): tlsCert=%s bytes, intermediateCA=%s bytes, tlsSerial=%s, signingPublicKey=%d bytes"
                .format(
                    controllerId,
                    O5RegistrationData.source(controllerId),
                    tlsCert?.size?.toString() ?: "null",
                    intermediateCA?.size?.toString() ?: "null",
                    tlsSerial?.toHex() ?: "unavailable",
                    signingPublicKeyRaw.size
                )
        )
        if (tlsCert != null && extractP256PublicKey(tlsCert) == null) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "O5CertificateStore: could not extract P-256 public key from TLS certificate for controller 0x%08X"
                    .format(controllerId)
            )
        }
    }

    /** Sign [data] with the secondary key; returns the raw signature (r || s, 64 bytes). */
    override fun signRaw(data: ByteArray): ByteArray {
        val privateKey = P256Codec.decodePrivateKey(signingPrivateKeyRaw)
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data)
        val raw = derSignatureToRaw(signature.sign())
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "O5 signRaw: signed ${data.size} bytes with secondary key -> ${raw.size}-byte raw signature"
        )
        return raw
    }

    companion object {

        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"


        /**
         * Verify an ECDSA signature against a raw (65-byte, uncompressed) public key.
         * Accepts [signature] in either DER or raw (64-byte r || s) form, trying DER first,
         * matching the Swift original's behavior.
         */
        fun verifySignature(signature: ByteArray, data: ByteArray, publicKeyRaw: ByteArray): Boolean {
            val publicKey = try {
                P256Codec.decodePublicKey(publicKeyRaw)
            } catch (e: Exception) {
                return false
            }

            verifyDer(signature, data, publicKey)?.let { return it }

            if (signature.size == 64) {
                val der = try {
                    rawSignatureToDer(signature)
                } catch (e: Exception) {
                    null
                }
                if (der != null) {
                    verifyDer(der, data, publicKey)?.let { return it }
                }
            }

            return false
        }

        /** Returns null (rather than false) if [der] isn't validly-DER-encoded, so the
         *  caller knows to try the other signature format instead of treating it as a
         *  genuine verification failure. */
        private fun verifyDer(der: ByteArray, data: ByteArray, publicKey: ECPublicKey): Boolean? =
            try {
                val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
                signature.initVerify(publicKey)
                signature.update(data)
                signature.verify(der)
            } catch (e: Exception) {
                null
            }


        internal fun derSignatureToRaw(der: ByteArray): ByteArray {
            require(der.isNotEmpty() && der[0] == 0x30.toByte()) { "Expected DER SEQUENCE tag" }
            val (_, seqContentStart) = readDerLength(der, 1)

            val (r, afterR) = readDerInteger(der, seqContentStart)
            val (s, _) = readDerInteger(der, afterR)

            return r.toFixedLengthBytes(32) + s.toFixedLengthBytes(32)
        }

        internal fun rawSignatureToDer(raw: ByteArray): ByteArray {
            require(raw.size == 64) { "Raw ECDSA signature must be 64 bytes (r || s), got ${raw.size}" }
            val r = BigInteger(1, raw.copyOfRange(0, 32))
            val s = BigInteger(1, raw.copyOfRange(32, 64))
            val body = encodeDerInteger(r) + encodeDerInteger(s)
            return byteArrayOf(0x30) + encodeDerLength(body.size) + body
        }

        private fun encodeDerInteger(value: BigInteger): ByteArray {
            val content = value.toByteArray()
            return byteArrayOf(0x02) + encodeDerLength(content.size) + content
        }


        private val p256SpkiHeader = byteArrayOf(
            0x30, 0x59.toByte(),
            0x30, 0x13,
            0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
            0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07,
            0x03, 0x42,
            0x00,
            0x04
        )

        /** Extract the raw P-256 public key (64 bytes, x || y) from a base64-encoded DER cert. */
        fun extractP256PublicKey(certDERBase64: String): ByteArray? {
            val certDER = try {
                Base64.getDecoder().decode(certDERBase64)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return extractP256PublicKey(certDER)
        }

        /**
         * Extract the raw P-256 public key (64 bytes, x || y) from DER certificate bytes.
         * Searches for the known SubjectPublicKeyInfo DER header pattern for P-256 keys,
         * then extracts the 64-byte raw EC point (without the leading 0x04 prefix).
         */
        fun extractP256PublicKey(certDER: ByteArray): ByteArray? {
            val header = p256SpkiHeader
            val headerLen = header.size
            val keyLen = 64

            if (certDER.size < headerLen + keyLen) return null

            val lastPossibleStart = certDER.size - headerLen - keyLen
            for (i in 0..lastPossibleStart) {
                var matches = true
                for (j in 0 until headerLen) {
                    if (certDER[i + j] != header[j]) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    val keyStart = i + headerLen
                    return certDER.copyOfRange(keyStart, keyStart + keyLen)
                }
            }
            return null
        }


        /**
         * Extract the serial number from a DER-encoded X.509 certificate.
         *
         * Handles both v3 certificates (with explicit version tag `a0 03 02 01 02`)
         * and v1 certificates (no version tag - serial immediately after the
         * TBSCertificate SEQUENCE).
         */
        fun extractSerialNumber(certDER: ByteArray): ByteArray? {
            val v3Pattern = byteArrayOf(0xa0.toByte(), 0x03, 0x02, 0x01, 0x02, 0x02)
            val v3PatternIndex = indexOf(certDER, v3Pattern)
            if (v3PatternIndex >= 0) {
                val serialLenOffset = v3PatternIndex + v3Pattern.size
                if (serialLenOffset >= certDER.size) return null
                val serialLen = certDER[serialLenOffset].toInt() and 0xFF
                val serialStart = serialLenOffset + 1
                if (serialLen <= 0 || serialStart + serialLen > certDER.size) return null
                return certDER.copyOfRange(serialStart, serialStart + serialLen)
            }

            if (certDER.size <= 4 || certDER[0] != 0x30.toByte()) return null
            val (_, outerContentStart) = readDerLength(certDER, 1)
            if (outerContentStart >= certDER.size || certDER[outerContentStart] != 0x30.toByte()) return null
            val (_, tbsContentStart) = readDerLength(certDER, outerContentStart + 1)
            if (tbsContentStart >= certDER.size) return null

            if (certDER[tbsContentStart] != 0x02.toByte()) return null
            val lenOffset = tbsContentStart + 1
            if (lenOffset >= certDER.size) return null
            val serialLen = certDER[lenOffset].toInt() and 0xFF
            val serialStart = lenOffset + 1
            if (serialLen <= 0 || serialStart + serialLen > certDER.size) return null
            return certDER.copyOfRange(serialStart, serialStart + serialLen)
        }

        private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
            if (needle.isEmpty() || haystack.size < needle.size) return -1
            outer@ for (i in 0..haystack.size - needle.size) {
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }


        /**
         * Parses a DER length field starting at [offset]. Returns (length, contentStartOffset).
         * Handles both short-form (single byte) and long-form (0x8n + n bytes) lengths.
         */
        private fun readDerLength(data: ByteArray, offset: Int): Pair<Int, Int> {
            val firstByte = data[offset].toInt() and 0xFF
            return if (firstByte and 0x80 == 0) {
                Pair(firstByte, offset + 1)
            } else {
                val numLenBytes = firstByte and 0x7f
                require(numLenBytes > 0 && offset + 1 + numLenBytes <= data.size) { "Malformed DER length" }
                var len = 0
                for (i in 0 until numLenBytes) {
                    len = (len shl 8) or (data[offset + 1 + i].toInt() and 0xFF)
                }
                Pair(len, offset + 1 + numLenBytes)
            }
        }

        private fun encodeDerLength(length: Int): ByteArray {
            if (length < 0x80) return byteArrayOf(length.toByte())
            var remaining = length
            val bytes = java.util.ArrayDeque<Byte>()
            while (remaining > 0) {
                bytes.addFirst((remaining and 0xFF).toByte())
                remaining = remaining shr 8
            }
            return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
        }

        /** Reads a DER INTEGER (tag 0x02) at [offset]; returns (value, offset after integer). */
        private fun readDerInteger(data: ByteArray, offset: Int): Pair<BigInteger, Int> {
            require(data[offset] == 0x02.toByte()) { "Expected DER INTEGER tag" }
            val (len, contentStart) = readDerLength(data, offset + 1)
            val bytes = data.copyOfRange(contentStart, contentStart + len)
            return Pair(BigInteger(bytes), contentStart + len)
        }

        private fun BigInteger.toFixedLengthBytes(length: Int): ByteArray {
            val raw = this.toByteArray()
            return when {
                raw.size == length                             -> raw
                raw.size == length + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
                raw.size < length                               -> ByteArray(length - raw.size) + raw
                else                                             -> throw IllegalStateException(
                    "Value does not fit in $length bytes (got ${raw.size})"
                )
            }
        }
    }
}
