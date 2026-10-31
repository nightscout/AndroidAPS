package app.aaps.core.nssdk.utils

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * The key exchange that replaces a pairing's secret right after pairing.
 *
 * The secret a client gets at pairing has been where others could see it: wrapped with an 8-digit PIN in a Nightscout
 * document (which can be cracked offline, given time), or in a QR code on the master's screen. So the client and the
 * master replace it with one that never travelled:
 *
 * 1. The client makes a one-time key pair ([newKeyPair]) and sends its public key in the hello, signed with the old
 *    secret.
 * 2. The master makes its own key pair and answers with its public key, signed with the old secret too.
 * 3. Both get the same shared value from ECDH (P-256) and turn it into two keys with HKDF-SHA256 ([deriveKeys]): the new
 *    secret, and a key for the client's Nightscout token, which the master can send in the same answer
 *    ([encryptToken]).
 *
 * The old secret is the HKDF salt, so only the two sides of this pairing come to the same keys. Someone who read the
 * old secret and the two public keys still cannot: that needs one of the private keys, and those never leave their
 * phone. Someone with the old secret who sends a hello of their own before the real client could; that is no worse than
 * what the old secret already allowed.
 *
 * P-256, not X25519: it is in Android's crypto on every version AAPS supports and in CryptoKit, and the library runs it
 * through each platform's own implementation. `ClientControlKeyExchangeCryptoTest` (commonTest) checks that both sides
 * agree, on every target.
 */
@OptIn(DelicateCryptographyApi::class)
object ClientControlKeyExchangeCrypto {

    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val INFO_PREFIX = "aaps-clientcontrol-key-v1"

    private val ecdh by lazy { CryptographyProvider.Default.get(ECDH) }
    private val hkdf by lazy { CryptographyProvider.Default.get(HKDF) }
    private val aesKeyDecoder by lazy { CryptographyProvider.Default.get(AES.GCM).keyDecoder() }

    /** One side's one-time key pair. [privateKey] is SENSITIVE (DER); [publicKey] is base64 of the raw point. */
    class KeyPair(val publicKey: String, val privateKey: ByteArray) {

        override fun toString(): String = "KeyPair(publicKey=$publicKey, privateKey=****)"
    }

    /** What the exchange gives: the new pairing [secret] and the key for the token. Both SENSITIVE. */
    class Keys(val secret: ByteArray, val tokenKey: ByteArray) {

        override fun toString(): String = "Keys(****)"
    }

    fun newKeyPair(): KeyPair {
        val pair = ecdh.keyPairGenerator(EC.Curve.P256).generateKeyBlocking()
        return KeyPair(
            publicKey = Base64.Default.encode(pair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)),
            privateKey = pair.privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER)
        )
    }

    /**
     * The keys from this side's [privateKey] and the other side's [otherPublicKey]. [clientPublicKey] and
     * [masterPublicKey] name both public keys in the same order on both sides, so the two get the same result.
     *
     * @throws Exception when a key cannot be read; the caller treats that as a failed exchange.
     */
    fun deriveKeys(
        privateKey: ByteArray,
        otherPublicKey: String,
        oldSecret: ByteArray,
        clientId: String,
        clientPublicKey: String,
        masterPublicKey: String
    ): Keys {
        val own = ecdh.privateKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, privateKey)
        val other = ecdh.publicKeyDecoder(EC.Curve.P256).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, Base64.Default.decode(otherPublicKey))
        val shared = own.sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(other)
        val info = "$INFO_PREFIX|$clientId|$clientPublicKey|$masterPublicKey".encodeToByteArray()
        val keys = hkdf.secretDerivation(digest = SHA256, outputSize = (2 * KEY_BYTES).bytes, salt = oldSecret, info = info)
            .deriveSecretToByteArrayBlocking(shared)
        return Keys(secret = keys.copyOfRange(0, KEY_BYTES), tokenKey = keys.copyOfRange(KEY_BYTES, 2 * KEY_BYTES))
    }

    /** [token] encrypted with AES-256-GCM under [tokenKey]: base64 of the IV and of the text with its tag. */
    fun encryptToken(tokenKey: ByteArray, token: String): Pair<String, String> {
        val iv = CryptographyRandom.nextBytes(IV_BYTES)
        val sealed = aesKeyDecoder.decodeFromByteArrayBlocking(AES.Key.Format.RAW, tokenKey).cipher().encryptWithIvBlocking(iv, token.encodeToByteArray())
        return Base64.Default.encode(iv) to Base64.Default.encode(sealed)
    }

    /** The token, or null when it does not open with [tokenKey] (another exchange, or changed on the way). */
    fun decryptToken(tokenKey: ByteArray, iv: String, sealed: String): String? = try {
        aesKeyDecoder.decodeFromByteArrayBlocking(AES.Key.Format.RAW, tokenKey).cipher()
            .decryptWithIvBlocking(Base64.Default.decode(iv), Base64.Default.decode(sealed))
            .decodeToString()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
