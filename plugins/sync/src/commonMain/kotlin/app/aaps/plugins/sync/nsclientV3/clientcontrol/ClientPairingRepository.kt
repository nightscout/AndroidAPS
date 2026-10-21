package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.KeyExchangeEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.MasterPairing
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.core.nssdk.utils.ClientControlKeyExchangeCrypto
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client-side store of the single master pairing.
 *
 * Symmetric to [AuthorizedClientsRepository] on the master side — the secret bytes are
 * wrapped via [SecureEncrypt] (AndroidKeyStore-backed AES/GCM) before persistence and
 * unwrapped only on demand. Plaintext never sits in shared prefs.
 *
 * Replay protection on the client side rests on a single monotonically-increasing counter
 * stored in [LongNonKey.NsClientControlCounterSent]. The counter is incremented inside
 * [nextSignedEnvelope] under a process-local lock so concurrent send paths cannot reuse
 * a value. A backup-restore that regresses the counter will cause the next message to be
 * rejected by the master (counter ≤ counterReceived); failure mode is "client must
 * re-pair", not "replay window opens".
 */
@SingleIn(AppScope::class)
@Inject
class ClientPairingRepository(
    private val preferences: Preferences,
    private val secureEncrypt: SecureEncrypt,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        const val SECURE_ENCRYPT_ALIAS = "NsClientControlSecret"

        /** How long a started key exchange waits for the master's answer before a new hello may start another. */
        const val KEY_EXCHANGE_WAIT_MS = 10L * 60L * 1000L
    }

    // A coroutine Mutex, not a monitor: `synchronized` is JVM only, and every caller is already a
    // coroutine. The old lock was held across preference reads and writes, so it parked a
    // Dispatchers.IO thread on storage I/O; suspending releases it instead.
    //
    // Mutex is NOT reentrant. Audited: no locked method calls another one. The one place that
    // needed re-entrancy already had the unlocked `currentPairingLocked` split out for it.
    private val mutex = Mutex()

    /** True iff a pairing is currently stored (clientId is the canonical "paired" marker). */
    fun isPaired(): Boolean = preferences.get(StringNonKey.NsClientControlClientId).isNotEmpty()

    /** Returns the assembled pairing snapshot or null if not paired / corrupt. */
    suspend fun currentPairing(): MasterPairing? = mutex.withLock { currentPairingLocked() }

    /**
     * Assembles the pairing snapshot WITHOUT taking [mutex]. Call only from a context that already
     * holds [mutex] (e.g. [nextSignedEnvelope], [verificationSecrets]); the public [currentPairing]
     * is the locked entry point. Avoids a re-entrant `synchronized(lock)` on the same thread.
     */
    private fun currentPairingLocked(): MasterPairing? {
        val masterInstallId = preferences.get(StringNonKey.NsClientControlMasterInstallId)
        val clientId = preferences.get(StringNonKey.NsClientControlClientId)
        val secretEnc = preferences.get(StringNonKey.NsClientControlMasterSecretEnc)
        if (masterInstallId.isEmpty() || clientId.isEmpty() || secretEnc.isEmpty()) return null
        return MasterPairing(
            masterInstallId = masterInstallId,
            clientId = clientId,
            masterSecretEnc = secretEnc
        )
    }

    /**
     * Persist a freshly-scanned [PairingPayload]. Overwrites any existing pairing — the
     * caller is expected to confirm with the user before calling. Resets the counter so
     * the first signed message after pairing is counter=1.
     *
     * [now] is persisted so [OrphanDetector][app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector]
     * can ignore settings/aaps docs whose `srvModified` predates the pairing — master may not
     * have republished the roster yet.
     *
     * Returns false when the secret could not be wrapped. Nothing is written in that case, so an
     * existing pairing survives and the caller must tell the user that pairing failed.
     */
    suspend fun pair(payload: PairingPayload, now: Long): Boolean = mutex.withLock {
        // Wrap the secret BEFORE anything is stored. [SecureEncrypt.encrypt] returns an empty string
        // when the key store refuses the key - seen on the iOS simulator, where the app carries no
        // entitlements and the Keychain answers errSecMissingEntitlement (-34018). Writing the other
        // four keys anyway produced the worst possible state: the client reported itself paired and
        // authorized while every send was refused with "publish called while unpaired", and no user
        // could tell why. All five keys or none.
        val secretEnc = secureEncrypt.encrypt(payload.secretHex, SECURE_ENCRYPT_ALIAS)
        if (secretEnc.isEmpty()) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: the pairing secret could not be stored - nothing was saved and pairing was refused")
            return@withLock false
        }
        preferences.put(StringNonKey.NsClientControlMasterInstallId, payload.masterInstallId)
        preferences.put(StringNonKey.NsClientControlClientId, payload.clientId)
        preferences.put(StringNonKey.NsClientControlMasterSecretEnc, secretEnc)
        preferences.put(LongNonKey.NsClientControlCounterSent, 0L)
        preferences.put(LongNonKey.NsClientControlPairedAt, now)
        // Not known until the master's roster lists this pairing; until then the client shows as little as ReadOnly.
        preferences.put(StringNonKey.NsClientControlRole, "")
        // A new pairing has a secret that travelled: it gets replaced by the key exchange in the hello.
        clearKeyExchangeLocked()
        true
    }

    /** Clears all pairing state. Counter reset stops a re-pair from inheriting the old counter. */
    suspend fun unpair(): Unit = mutex.withLock {
        preferences.put(StringNonKey.NsClientControlMasterInstallId, "")
        preferences.put(StringNonKey.NsClientControlClientId, "")
        preferences.put(StringNonKey.NsClientControlMasterSecretEnc, "")
        preferences.put(LongNonKey.NsClientControlCounterSent, 0L)
        preferences.put(LongNonKey.NsClientControlPairedAt, 0L)
        // The role belongs to this pairing; a new pairing learns its own from the master's roster.
        preferences.put(StringNonKey.NsClientControlRole, "")
        clearKeyExchangeLocked()
    }

    private fun clearKeyExchangeLocked() {
        preferences.put(BooleanNonKey.NsClientControlSecretReplaced, false)
        preferences.put(StringNonKey.NsClientControlPreviousSecretEnc, "")
        preferences.put(StringNonKey.NsClientControlKeyExchangePublic, "")
        preferences.put(StringNonKey.NsClientControlKeyExchangePrivateEnc, "")
        preferences.put(LongNonKey.NsClientControlKeyExchangeAt, 0L)
    }

    /**
     * True while this pairing still uses the secret the master handed out, and no key exchange is waiting for the
     * master's answer: the hello with a key is due. A started exchange gets [KEY_EXCHANGE_WAIT_MS] for its answer; a
     * second hello before that could leave the master with the key of the second while this phone takes the first.
     */
    fun needsKeyExchange(now: Long): Boolean =
        isPaired() && !preferences.get(BooleanNonKey.NsClientControlSecretReplaced) &&
            now - preferences.get(LongNonKey.NsClientControlKeyExchangeAt) >= KEY_EXCHANGE_WAIT_MS

    /**
     * Starts a key exchange: a one-time key pair whose private key is kept, wrapped, until the master answers. Returns
     * the public key for the hello, or null when the private key cannot be kept (the hello then goes without one and
     * the secret stays as it is).
     */
    suspend fun startKeyExchange(now: Long): String? = mutex.withLock {
        val pair = ClientControlKeyExchangeCrypto.newKeyPair()
        val privateEnc = secureEncrypt.encrypt(ClientControlCrypto.bytesToHex(pair.privateKey), SECURE_ENCRYPT_ALIAS)
        if (privateEnc.isEmpty()) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: the key for the key exchange could not be stored, the hello goes without it")
            return@withLock null
        }
        preferences.put(StringNonKey.NsClientControlKeyExchangePublic, pair.publicKey)
        preferences.put(StringNonKey.NsClientControlKeyExchangePrivateEnc, privateEnc)
        preferences.put(LongNonKey.NsClientControlKeyExchangeAt, now)
        pair.publicKey
    }

    sealed interface KeyExchangeResult {

        /** The new secret is in use. [token] is the client's own Nightscout token, when the master sent one (SENSITIVE). */
        class Done(val token: String?) : KeyExchangeResult {

            override fun toString(): String = "Done(token=${if (token == null) "none" else "****"})"
        }

        /** Not an answer to this phone's last hello, or not from its master: nothing changed. */
        data object Ignored : KeyExchangeResult
    }

    /**
     * Finishes the key exchange with the master's answer and signs with the new secret from now on. The old secret is
     * kept only to check the master's answers signed before it took the new one ([verificationSecrets]).
     */
    suspend fun completeKeyExchange(answer: KeyExchangeEnvelope): KeyExchangeResult = mutex.withLock {
        val pairing = currentPairingLocked() ?: return@withLock KeyExchangeResult.Ignored
        val ownPublicKey = preferences.get(StringNonKey.NsClientControlKeyExchangePublic)
        // Another client's answer, or one to an older hello of this phone.
        if (answer.clientId != pairing.clientId || ownPublicKey.isEmpty() || answer.clientPublicKey != ownPublicKey) return@withLock KeyExchangeResult.Ignored
        val oldSecret = decryptSecretLocked(pairing.masterSecretEnc) ?: return@withLock KeyExchangeResult.Ignored
        if (!ClientControlCrypto.verifyKeyExchange(oldSecret, answer)) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: the answer to the key exchange is not signed by the master, ignored")
            return@withLock KeyExchangeResult.Ignored
        }
        val privateKey = decryptSecretLocked(preferences.get(StringNonKey.NsClientControlKeyExchangePrivateEnc), "key-exchange key (a new hello starts again)")
            ?: return@withLock KeyExchangeResult.Ignored
        val keys = runCatching {
            ClientControlKeyExchangeCrypto.deriveKeys(privateKey, answer.masterPublicKey, oldSecret, pairing.clientId, ownPublicKey, answer.masterPublicKey)
        }.getOrElse {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: the key exchange failed: ${it.message}")
            return@withLock KeyExchangeResult.Ignored
        }
        val newSecretEnc = secureEncrypt.encrypt(ClientControlCrypto.bytesToHex(keys.secret), SECURE_ENCRYPT_ALIAS)
        if (newSecretEnc.isEmpty()) return@withLock KeyExchangeResult.Ignored
        val sealedToken = answer.token
        val tokenIv = answer.tokenIv
        val token = if (sealedToken != null && tokenIv != null) ClientControlKeyExchangeCrypto.decryptToken(keys.tokenKey, tokenIv, sealedToken) else null
        preferences.put(StringNonKey.NsClientControlPreviousSecretEnc, pairing.masterSecretEnc)
        preferences.put(StringNonKey.NsClientControlMasterSecretEnc, newSecretEnc)
        preferences.put(BooleanNonKey.NsClientControlSecretReplaced, true)
        preferences.put(StringNonKey.NsClientControlKeyExchangePublic, "")
        preferences.put(StringNonKey.NsClientControlKeyExchangePrivateEnc, "")
        KeyExchangeResult.Done(token)
    }

    /**
     * The secrets the master's answers may be signed with: the current one, then the one before the key exchange, while
     * the master may still sign with it.
     */
    suspend fun verificationSecrets(): List<ByteArray> = mutex.withLock {
        val pairing = currentPairingLocked() ?: return@withLock emptyList()
        listOfNotNull(
            decryptSecretLocked(pairing.masterSecretEnc),
            preferences.get(StringNonKey.NsClientControlPreviousSecretEnc).takeIf { it.isNotEmpty() }?.let { decryptSecretLocked(it, "previous pairing secret") }
        )
    }

    /** The master signs with the new secret now: the old one is not needed any more. */
    suspend fun forgetPreviousSecret(): Unit = mutex.withLock {
        if (preferences.get(StringNonKey.NsClientControlPreviousSecretEnc).isNotEmpty())
            preferences.put(StringNonKey.NsClientControlPreviousSecretEnc, "")
    }

    /** Unwraps a hex value stored with [SecureEncrypt]; null (and logged as [what]) when it cannot be read. */
    private fun decryptSecretLocked(enc: String, what: String = "pairing secret (re-pair required)"): ByteArray? {
        if (!secureEncrypt.isValidDataString(enc)) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: stored $what is corrupted")
            return null
        }
        val hex = secureEncrypt.decrypt(enc)
        if (hex.isEmpty()) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: decrypt of the $what failed (KeyStore reset?)")
            return null
        }
        val bytes = runCatching { ClientControlCrypto.hexToBytes(hex) }.getOrNull()
        if (bytes == null) aapsLogger.error(LTag.NSCLIENT, "ClientControl: the $what is malformed")
        return bytes
    }

    /**
     * Increments the counter atomically, signs an envelope of the given [type] over [payloadJson],
     * and returns it ready to ship. Returns null when:
     * - not paired
     * - encrypted secret blob fails [SecureEncrypt.isValidDataString] (corrupted)
     * - decrypt yields empty (KeyStore reset / backup-restore — typically requires re-pair)
     * - secret hex is malformed
     *
     * Each `null` path is logged separately so a backup-restore failure produces a diagnostic
     * instead of silent send drops.
     */
    suspend fun nextSignedEnvelope(type: String, payloadJson: String, timestamp: Long, validUntil: Long = Long.MAX_VALUE, wantsAck: Boolean = false): SignedEnvelope? = mutex.withLock {
        val pairing = currentPairingLocked() ?: run {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: nextSignedEnvelope called while unpaired")
            return@withLock null
        }
        val secretBytes = decryptSecretLocked(pairing.masterSecretEnc) ?: return@withLock null
        val nextCounter = preferences.get(LongNonKey.NsClientControlCounterSent) + 1L
        preferences.put(LongNonKey.NsClientControlCounterSent, nextCounter)
        val draft = SignedEnvelope(
            clientId = pairing.clientId,
            counter = nextCounter,
            timestamp = timestamp,
            type = type,
            payload = payloadJson,
            signature = "",
            validUntil = validUntil,
            wantsAck = wantsAck
        )
        ClientControlCrypto.signEnvelope(secretBytes, draft)
    }

}
