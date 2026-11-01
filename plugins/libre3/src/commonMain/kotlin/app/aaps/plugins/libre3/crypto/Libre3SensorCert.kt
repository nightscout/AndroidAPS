package app.aaps.plugins.libre3.crypto

/**
 * The sensor's own certificate, 140 bytes, read during a first pairing.
 *
 * Ported from LibreCRKit `Pairing/SensorCert.swift` at pin `a86b92f`.
 *
 * Two things are read from it. The sensor's long lived point, because the Phase 5 source of a
 * first pairing is built from it. And the signature, which is checked against the sensor maker's
 * own signing keys before the pairing may go on, exactly as `PairingFlow.verifySensorCertificate`
 * does: a certificate that does not verify ends the pairing there rather than later and for a
 * reason nobody can read.
 */
class Libre3SensorCert private constructor(val raw: ByteArray) {

    /** The sensor's long lived point, 65 bytes, `0x04` then X then Y. */
    val staticPublicKey: ByteArray get() = raw.copyOfRange(PUBLIC_KEY_START, PUBLIC_KEY_END)

    /** The part of the certificate that the signature covers. */
    val signedPayload: ByteArray get() = raw.copyOfRange(0, SIGNED_PAYLOAD_END)

    /** The signature, 64 bytes, r then s. */
    val signature: ByteArray get() = raw.copyOfRange(SIGNATURE_START, TOTAL_SIZE)

    /**
     * True when the signature was made by one of the sensor maker's own signing keys.
     *
     * @param signingKeys the points to try, each 65 bytes starting with `0x04`.
     */
    fun isSignedByKnownKey(signingKeys: List<ByteArray> = KNOWN_SIGNING_KEYS): Boolean =
        signingKeys.any { verifiedBy(it) }

    private fun verifiedBy(signingKey65: ByteArray): Boolean {
        if (signingKey65.size != 65 || signingKey65[0] != 0x04.toByte()) return false
        return libre3VerifyEcdsaSha256(signingKey65, signedPayload, derSignature(signature))
    }

    /**
     * Turns the plain 64 byte `r || s` of the wire into the tagged shape a verifier wants.
     *
     * Verifiers read the tagged form, so the two numbers have to be wrapped, each with a leading
     * zero byte when its top bit is set, or it would be read as a negative number.
     */
    private fun derSignature(rawSignature: ByteArray): ByteArray {
        val r = derInteger(rawSignature.copyOfRange(0, 32))
        val s = derInteger(rawSignature.copyOfRange(32, 64))
        val body = r + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun derInteger(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0.toByte()) start += 1
        val trimmed = value.copyOfRange(start, value.size)
        val body = if (trimmed[0].toInt() and 0x80 != 0) byteArrayOf(0) + trimmed else trimmed
        return byteArrayOf(0x02, body.size.toByte()) + body
    }

    companion object {

        const val TOTAL_SIZE = 140
        private const val SIGNED_PAYLOAD_END = 76
        private const val PUBLIC_KEY_START = 11
        private const val PUBLIC_KEY_END = 76
        private const val SIGNATURE_START = 76

        /**
         * The sensor maker's own signing keys, both families, as public verifier material.
         *
         * Copied from LibreCRKit `Pairing/SensorCert.swift` `Libre3PatchSigningKey`. These are
         * public points, not secrets.
         */
        val KNOWN_SIGNING_KEYS: List<ByteArray> = listOf(
            byteArrayOf(
                0x04, 0xb6.toByte(), 0x9d.toByte(), 0x17, 0x34, 0xf5.toByte(), 0xe4.toByte(), 0x25,
                0xbc.toByte(), 0xc0.toByte(), 0x57, 0x6a, 0xd1.toByte(), 0xf7.toByte(), 0x27, 0xc1.toByte(),
                0x31, 0x1c, 0x90.toByte(), 0xb6.toByte(), 0xea.toByte(), 0x98.toByte(), 0x6f, 0x00,
                0x6e, 0x7e, 0x9f.toByte(), 0x90.toByte(), 0x96.toByte(), 0xf6.toByte(), 0xa8.toByte(), 0x28,
                0x4f, 0x12, 0xbf.toByte(), 0x7d, 0xdf.toByte(), 0xe1.toByte(), 0x54, 0xa3.toByte(),
                0xf1.toByte(), 0xd4.toByte(), 0x5a, 0x0f, 0x27, 0x34, 0xec.toByte(), 0xab.toByte(),
                0xca.toByte(), 0x6b, 0x9e.toByte(), 0xb5.toByte(), 0x6e, 0xe4.toByte(), 0xec.toByte(), 0xca.toByte(),
                0x87.toByte(), 0x85.toByte(), 0x3a, 0xd8.toByte(), 0x53, 0xb6.toByte(), 0xa6.toByte(), 0x41,
                0x80.toByte(),
            ),
            byteArrayOf(
                0x04, 0xa2.toByte(), 0xd8.toByte(), 0x47, 0x89.toByte(), 0x90.toByte(), 0x94.toByte(), 0x5f,
                0x70, 0xa9.toByte(), 0x57, 0x0a, 0xde.toByte(), 0x07, 0xb1.toByte(), 0x55,
                0xbc.toByte(), 0x90.toByte(), 0x4d, 0x2d, 0x38, 0x06, 0x47, 0x58,
                0x7b, 0x12, 0x39, 0x17, 0x01, 0x30, 0x9b.toByte(), 0xd1.toByte(),
                0x0b, 0x59, 0x90.toByte(), 0xc4.toByte(), 0xc4.toByte(), 0x7c, 0x47, 0xf1.toByte(),
                0xf0.toByte(), 0x80.toByte(), 0x46, 0xcb.toByte(), 0x6f, 0x2d, 0xe0.toByte(), 0x74,
                0x8d.toByte(), 0x1f, 0xa7.toByte(), 0xf7.toByte(), 0x37, 0x90.toByte(), 0xec.toByte(), 0x9d.toByte(),
                0x8d.toByte(), 0xd6.toByte(), 0x37, 0x21, 0x27, 0x78, 0x52, 0x88.toByte(),
                0x38,
            ),
        )

        /**
         * @throws Libre3CryptoException when the blob is the wrong size, or its point is not a
         *   plain uncompressed one.
         */
        fun parse(raw: ByteArray): Libre3SensorCert {
            if (raw.size != TOTAL_SIZE) {
                throw Libre3CryptoException("the sensor certificate must be $TOTAL_SIZE bytes, not ${raw.size}")
            }
            if (raw[PUBLIC_KEY_START] != 0x04.toByte()) {
                throw Libre3CryptoException("the sensor certificate does not carry a plain uncompressed point")
            }
            return Libre3SensorCert(raw.copyOf())
        }
    }
}
