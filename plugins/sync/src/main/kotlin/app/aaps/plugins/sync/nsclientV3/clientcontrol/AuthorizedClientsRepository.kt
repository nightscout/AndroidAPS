package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.utils.ClientControlCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Master-side store of clients authorized to send commands.
 *
 * Persists a JSON array in [StringNonKey.NsClientControlAuthorizedClients].
 * Plaintext HMAC secrets never sit in prefs — the bytes are wrapped through
 * [SecureEncrypt] (AndroidKeyStore-backed AES/GCM) before persistence and
 * unwrapped only on demand.
 *
 * All mutations and the prune-on-read path go through a single intrinsic lock.
 * The lock does NOT, by itself, stop a bump from resurrecting a deleted entry —
 * every mutation re-decodes the current prefs and maps over that fresh snapshot,
 * so an entry already removed simply isn't in the list a concurrent bump maps
 * over (it can't re-add what it never read). What the lock actually buys is
 * atomicity of the read-modify-write: concurrent bumpLastSeen / markActive /
 * delete calls each do decode → transform → write, and without serialization
 * two such cycles could interleave and have one overwrite the other's update
 * (lost counter / lost deletion). Serializing them keeps every committed write
 * built on the latest persisted state — relevant on a pump-control surface.
 */
@Singleton
class AuthorizedClientsRepository @Inject constructor(
    private val preferences: Preferences,
    private val secureEncrypt: SecureEncrypt,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        const val SECURE_ENCRYPT_ALIAS = "NsClientControlSecret"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    /** Current list with expired pending entries pruned. Side-effects prefs if any were pruned. */
    fun current(now: Long): List<AuthorizedClient> = synchronized(lock) {
        val list = decode()
        val kept = list.filter { it.state != ClientState.Pending || it.pairExpiresAt > now }
        if (kept.size != list.size) write(kept)
        kept
    }

    /** Flow of the list (raw — does not auto-prune; callers can prune via [pruneExpired]). */
    fun observe(): Flow<List<AuthorizedClient>> =
        preferences.observe(StringNonKey.NsClientControlAuthorizedClients).map { decode(it) }

    /**
     * Look up an entry without pruning expired pendings. Use only when the caller needs to
     * distinguish "truly never paired" from "pairing-window expired" for diagnostics —
     * general code should prefer [current] so expired entries are pruned out automatically.
     */
    fun findRaw(clientId: String): AuthorizedClient? = synchronized(lock) {
        decode().firstOrNull { it.clientId == clientId }
    }

    /**
     * Create a new pending pairing. Returns the freshly-generated secret in **plaintext hex**
     * for caller-side wrapping (PIN-based pairing offer) — caller must not persist it. The
     * encrypted form is stored on the entry.
     */
    fun addPending(name: String, pairTtlMs: Long, now: Long): PendingResult = synchronized(lock) {
        val secretBytes = ClientControlCrypto.newSecretBytes()
        val secretHex = ClientControlCrypto.bytesToHex(secretBytes)
        val entry = AuthorizedClient(
            clientId = ClientControlCrypto.newClientId(),
            name = name,
            encryptedSecret = secureEncrypt.encrypt(secretHex, SECURE_ENCRYPT_ALIAS),
            state = ClientState.Pending,
            createdAt = now,
            pairExpiresAt = now + pairTtlMs
        )
        write(decode() + entry)
        PendingResult(entry, secretHex)
    }

    fun delete(clientId: String): Unit = synchronized(lock) {
        write(decode().filterNot { it.clientId == clientId })
    }

    /**
     * Promote a pending entry to active on first verified hello. No-op if not present or already active.
     *
     * Callers must first resolve the entry via [current] (now) — which prunes expired pending entries —
     * so a hello arriving after the pairing window has lapsed does not promote a stale entry that
     * [current] would otherwise have dropped.
     */
    fun markActive(clientId: String, counterReceived: Long, now: Long): Unit = synchronized(lock) {
        val list = decode()
        val updated = list.map {
            if (it.clientId == clientId && it.state == ClientState.Pending)
                it.copy(state = ClientState.Active, counterReceived = counterReceived, lastSeenAt = now)
            else it
        }
        if (updated != list) write(updated)
    }

    /** Update lastSeen + counter after an accepted command. No-op if entry missing. */
    fun bumpLastSeen(clientId: String, counterReceived: Long, now: Long): Unit = synchronized(lock) {
        val list = decode()
        val updated = list.map {
            if (it.clientId == clientId) it.copy(counterReceived = counterReceived, lastSeenAt = now) else it
        }
        if (updated != list) write(updated)
    }

    /**
     * Drop pending entries past their pairExpiresAt. Returns the clientIds that were removed so
     * the caller can drive the matching offer-doc cleanup without an inconsistent re-snapshot of
     * `clients` (which is a [SharingStarted.WhileSubscribed] StateFlow and can lag the prefs).
     */
    fun pruneExpired(now: Long): List<String> = synchronized(lock) {
        val list = decode()
        val kept = list.filter { it.state != ClientState.Pending || it.pairExpiresAt > now }
        if (kept.size == list.size) return@synchronized emptyList()
        write(kept)
        val keptIds = kept.mapTo(HashSet(kept.size)) { it.clientId }
        list.mapNotNull { if (it.clientId !in keptIds) it.clientId else null }
    }

    /**
     * Resolves the HMAC secret + the last-accepted counter for [clientId] in one shot. Returning
     * both forces the caller to compare the incoming counter against `counterReceived` before
     * accepting a message — a separate `secretBytesFor` would let callers skip the replay check.
     *
     * Returns null in three distinct cases (logged separately so a backup-restore / KeyStore
     * reset does not silently reject every command):
     * - entry missing for [clientId]
     * - stored encryptedSecret fails [SecureEncrypt.isValidDataString] (corrupted blob)
     * - decrypt produces empty result (KeyStore key gone — typical after backup-restore)
     */
    fun secretLookup(clientId: String): SecretLookup? = synchronized(lock) {
        val entry = decode().firstOrNull { it.clientId == clientId } ?: return@synchronized null
        if (!secureEncrypt.isValidDataString(entry.encryptedSecret)) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: stored secret blob for $clientId is corrupted")
            return@synchronized null
        }
        val hex = secureEncrypt.decrypt(entry.encryptedSecret)
        if (hex.isEmpty()) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: decrypt of secret for $clientId failed (KeyStore reset?)")
            return@synchronized null
        }
        val bytes = runCatching { ClientControlCrypto.hexToBytes(hex) }.getOrNull()
        if (bytes == null) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: secret hex for $clientId is malformed")
            return@synchronized null
        }
        SecretLookup(bytes, entry.counterReceived)
    }

    private fun decode(raw: String = preferences.get(StringNonKey.NsClientControlAuthorizedClients)): List<AuthorizedClient> =
        runCatching { json.decodeFromString<List<AuthorizedClient>>(raw) }.getOrNull() ?: emptyList()

    private fun write(list: List<AuthorizedClient>) {
        preferences.put(StringNonKey.NsClientControlAuthorizedClients, json.encodeToString(list))
    }

    data class PendingResult(val entry: AuthorizedClient, val secretHex: String)
    data class SecretLookup(val secretBytes: ByteArray, val counterReceived: Long) {

        override fun equals(other: Any?): Boolean =
            other is SecretLookup && secretBytes.contentEquals(other.secretBytes) && counterReceived == other.counterReceived

        override fun hashCode(): Int = secretBytes.contentHashCode() * 31 + counterReceived.hashCode()
    }
}
