package app.aaps.plugins.eversense.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.protocol.BinaryFields
import app.aaps.plugins.eversense.protocol.SecureV2Cipher
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.json.Json

/**
 * The 365's key material: the phone's long-term identity (a P-256 key pair, registered with the Eversense server), and
 * per connection an ephemeral key pair, the session key agreed with the transmitter and the message counter.
 *
 * Stored keys keep the format of the first version: the private key as PKCS#8 DER, the public key as X.509
 * SubjectPublicKeyInfo DER, both in hex. A phone that registered with the first version keeps its registration.
 */
class EversenseCrypto365Util(val preference: EversenseStore, private val aapsLogger: AAPSLogger) {

    private val provider = CryptographyProvider.Default
    private var ephemPrivate: ECDH.PrivateKey? = null
    private var ephemPublic: ByteArray? = null
    private var ephemSalt: ByteArray? = null

    private var messageSequenceNumber = 1
    private var sessionKey: ByteArray? = null

    fun canUseShortcut(): Boolean {
        return getState(preference).canUseShortcut
    }

    fun allowUseShortcut() {
        val state = getState(preference)
        state.canUseShortcut = true
        saveState(state, preference)
    }

    fun disallowUseShortcut() {
        val state = getState(preference)
        state.canUseShortcut = false
        saveState(state, preference)
    }

    fun getClientPublicKey(): ByteArray {
        val state = getState(preference)
        return state.publicKey.hexToByteArray()
    }

    fun generateKeyPairIfNotExists(): Boolean {
        val state = getState(preference)
        if (state.clientId.isNotEmpty() && state.publicKey.isNotEmpty() && state.privateKey.isNotEmpty()) {
            aapsLogger.debug(LTag.BGSOURCE) { "Already generated keypair" }
            return true
        }

        try {
            val keyPair = provider.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
            state.clientId = randomBytes(32).toHexString()
            state.privateKey = keyPair.privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER).toHexString()
            state.publicKey = keyPair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.DER).toHexString()
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Error generating key pair", e)
            return false
        }

        saveState(state, preference)
        aapsLogger.debug(LTag.BGSOURCE) { "Generated keypair!" }
        return true
    }

    fun getClientId(): ByteArray {
        val state = getState(preference)
        return state.clientId.hexToByteArray()
    }

    fun getStartSecret(signature: ByteArray): ByteArray {
        val public = ephemPublic ?: return byteArrayOf()
        val salt = ephemSalt ?: return byteArrayOf()

        var secret = byteArrayOf(128.toByte(), 0)
        secret += getState(preference).clientId.hexToByteArray()
        secret += public
        secret += salt
        secret += signature

        return secret
    }

    /** A new ephemeral key pair and salt for this connection; returns their signature by the long-term key. */
    fun generateEphem(): ByteArray? {
        try {
            val keyPair = provider.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
            // X || Y: the uncompressed point without its 0x04 marker
            val publicKey = keyPair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW).copyOfRange(1, 65)
            val v2Salt = randomBytes(8)

            ephemPrivate = keyPair.privateKey
            ephemPublic = publicKey
            ephemSalt = v2Salt

            val identity = provider.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, getState(preference).privateKey.hexToByteArray())
            // r || s, 32 bytes each
            return identity.signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW).generateSignatureBlocking(publicKey + v2Salt)
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Got exception during generateEphem", e)
            return null
        }
    }

    /**
     * The session key from the transmitter's ephemeral public key ([encodedPublicKey], X || Y): ECDH, then HKDF-SHA256
     * with no salt and this connection's salt as the info, 16 bytes.
     */
    fun generateSessionKey(encodedPublicKey: ByteArray) {
        try {
            val salt = ephemSalt ?: return
            val private = ephemPrivate ?: return
            val transmitterKey = provider.get(ECDH).publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, byteArrayOf(4) + encodedPublicKey.copyOfRange(0, 64))
            val sharedSecret = private.sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(transmitterKey)
            sessionKey = hkdfSha256(sharedSecret, info = salt, length = 16)
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Failed to generate sessionKey", e)
        }
    }

    fun encrypt(data: ByteArray): ByteArray {
        val seed = ephemSalt ?: return missingMaterial("salt")
        val key = sessionKey ?: return missingMaterial("session key")
        val index = (messageSequenceNumber and 0x3fff).toLong()
        val prefix = SecureV2Cipher.prefix(index)
        val nonce = SecureV2Cipher.nonce(seed, index)
        messageSequenceNumber++
        return SecureV2Cipher.encrypt(data, key, prefix, nonce) ?: cipherFailed("encrypt")
    }

    fun decrypt(response: ByteArray): ByteArray {
        val seed = ephemSalt ?: return missingMaterial("salt")
        val key = sessionKey ?: return missingMaterial("session key")
        val ciphertext = response.copyOfRange(2, response.size)
        val prefix = response.copyOfRange(0, 2)
        val index = (BinaryFields.unsigned(prefix.toUByteArray()) shr 2) and 0x3fff
        val nonce = SecureV2Cipher.nonce(seed, index)
        return SecureV2Cipher.decrypt(ciphertext, key, prefix, nonce) ?: cipherFailed("decrypt")
    }

    private fun missingMaterial(name: String): ByteArray {
        aapsLogger.error(LTag.BGSOURCE, "No $name available")
        return byteArrayOf()
    }

    private fun cipherFailed(operation: String): ByteArray {
        aapsLogger.error(LTag.BGSOURCE, "Authenticated $operation failed")
        return byteArrayOf()
    }

    /** HKDF (RFC 5869) with SHA-256 and no salt, which means a salt of 32 zero bytes. [length] is at most 32. */
    private fun hkdfSha256(secret: ByteArray, info: ByteArray, length: Int): ByteArray {
        val pseudoRandomKey = hmacSha256(ByteArray(32), secret)
        return hmacSha256(pseudoRandomKey, info + byteArrayOf(1)).copyOf(length)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        provider.get(HMAC).keyDecoder(SHA256).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
            .signatureGenerator().generateSignatureBlocking(data)

    companion object {

        private val JSON = Json { ignoreUnknownKeys = true }

        private fun randomBytes(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

        private fun getState(preference: EversenseStore): EversenseSecureState {
            val stateJson = preference.getString(StorageKeys.SECURE_STATE, null) ?: "{}"
            return JSON.decodeFromString<EversenseSecureState>(stateJson)
        }

        private fun saveState(state: EversenseSecureState, preference: EversenseStore) {
            preference.edit(commit = true) {
                putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state))
            }
        }
    }
}
