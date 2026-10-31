package app.aaps.core.nssdk.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * The master's signature on its settings docs (the cold and the hot running configuration).
 *
 * A client's Nightscout token may write to `settings`, so without a signature anybody with one could put settings,
 * a client list and a "master is alive" sign in front of every client. The master signs each doc with its own key
 * (ECDSA, P-256, SHA-256) and the clients check it with the public key they got from the master directly: in a pong,
 * which is signed with the pairing secret, or in the QR code.
 *
 * What is signed is the doc [identifier] and the [signed] text: a JSON string, kept by Nightscout as it is. A JSON
 * object could come back with numbers or key order changed, and the signature would no longer fit.
 *
 * P-256 like the key exchange: in Android's crypto on every version AAPS supports, and in CryptoKit on iOS.
 * `MasterDocSigningTest` (commonTest) checks it on every target.
 */
object MasterDocSigning {

    private const val PREFIX = "aaps-settings-v1"

    private val ecdsa by lazy { CryptographyProvider.Default.get(ECDSA) }

    /** The master's key pair. [privateKey] is SENSITIVE (DER); [publicKey] is base64 of the raw point. */
    class KeyPair(val publicKey: String, val privateKey: ByteArray) {

        override fun toString(): String = "KeyPair(publicKey=$publicKey, privateKey=****)"
    }

    fun newKeyPair(): KeyPair {
        val pair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        return KeyPair(
            publicKey = Base64.Default.encode(pair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)),
            privateKey = pair.privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER)
        )
    }

    /** The signature (base64) of the doc [identifier] with the [signed] text. */
    fun sign(privateKey: ByteArray, identifier: String, signed: String): String {
        val key = ecdsa.privateKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, privateKey)
        val signature = key.signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW).generateSignatureBlocking(data(identifier, signed))
        return Base64.Default.encode(signature)
    }

    /** True only when [signature] is the master's (with [publicKey]) for this [identifier] and [signed] text. */
    fun verify(publicKey: String, identifier: String, signed: String, signature: String): Boolean = try {
        val key = ecdsa.publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, Base64.Default.decode(publicKey))
        key.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW).tryVerifySignatureBlocking(data(identifier, signed), Base64.Default.decode(signature))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // A key or a signature that is not even readable is not the master's.
        false
    }

    private fun data(identifier: String, signed: String): ByteArray = "$PREFIX|$identifier|$signed".encodeToByteArray()
}
