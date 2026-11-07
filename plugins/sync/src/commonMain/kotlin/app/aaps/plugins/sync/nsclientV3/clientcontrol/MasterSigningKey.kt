package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.core.nssdk.utils.MasterDocSigning
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Master side: the key the master signs its settings docs with (`MasterDocSigning`). Made on first use and kept, the
 * private key wrapped like the pairing secrets. Clients get [publicKey] in each pong and in the QR code.
 *
 * A key that cannot be read any more (a reset key store) is replaced by a new one. Paired clients take the new one
 * with their next pong; a phone that only reads Nightscout needs the master's QR code again.
 */
@SingleIn(AppScope::class)
@Inject
class MasterSigningKey(
    private val preferences: Preferences,
    private val secureEncrypt: SecureEncrypt,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    private val mutex = Mutex()

    /** The unwrapped private key, kept after the first use so the key store is not asked for every doc. SENSITIVE. */
    private var privateKey: ByteArray? = null

    /** The public key, or null when no key can be kept (nothing is signed then). */
    suspend fun publicKey(): String? = mutex.withLock {
        if (keyLocked() == null) null else preferences.get(StringNonKey.NsClientControlSigningPublic)
    }

    /**
     * Signs a doc: returns its "signed at" time and the signature of [identifier] with the text [signedText] makes from
     * that time. The signature is null when no key can be kept; the doc then goes out unsigned, and clients that know
     * the key refuse it.
     */
    suspend fun sign(identifier: String, signedText: (signedAt: Long) -> String): Signed = mutex.withLock {
        // Always later than the last one, also when the clock went back: clients refuse an older doc as a replay.
        val signedAt = maxOf(dateUtil.now(), preferences.get(LongNonKey.NsClientControlSignedAt) + 1)
        preferences.put(LongNonKey.NsClientControlSignedAt, signedAt)
        val text = signedText(signedAt)
        Signed(text, keyLocked()?.let { MasterDocSigning.sign(it, identifier, text) })
    }

    class Signed(val text: String, val signature: String?)

    private fun keyLocked(): ByteArray? {
        privateKey?.let { return it }
        val stored = preferences.get(StringNonKey.NsClientControlSigningPrivateEnc)
        if (stored.isNotEmpty() && preferences.get(StringNonKey.NsClientControlSigningPublic).isNotEmpty()) {
            val hex = if (secureEncrypt.isValidDataString(stored)) secureEncrypt.decrypt(stored) else ""
            if (hex.isNotEmpty()) return ClientControlCrypto.hexToBytes(hex).also { privateKey = it }
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: the key for signing the settings docs could not be read, a new one is made")
        }
        val pair = MasterDocSigning.newKeyPair()
        val wrapped = secureEncrypt.encrypt(ClientControlCrypto.bytesToHex(pair.privateKey), AuthorizedClientsRepository.SECURE_ENCRYPT_ALIAS)
        if (wrapped.isEmpty()) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: the key for signing the settings docs could not be stored, the docs go out unsigned")
            return null
        }
        preferences.put(StringNonKey.NsClientControlSigningPublic, pair.publicKey)
        preferences.put(StringNonKey.NsClientControlSigningPrivateEnc, wrapped)
        return pair.privateKey.also { privateKey = it }
    }
}
