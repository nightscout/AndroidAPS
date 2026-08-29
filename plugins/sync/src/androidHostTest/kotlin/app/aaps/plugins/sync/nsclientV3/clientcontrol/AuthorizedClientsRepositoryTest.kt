package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class AuthorizedClientsRepositoryTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var aapsLogger: AAPSLogger

    private var rejectsBlobValidation = false

    // Reverses the plaintext so the persisted form does not contain the original — keeps the
    // "raw secret never written to prefs" invariant testable with a deterministic fake.
    private val secureEncrypt = object : SecureEncrypt {
        override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = "ENC:$keystoreAlias:${plaintextSecret.reversed()}"
        override fun decrypt(encryptedSecret: String): String = encryptedSecret.removePrefix("ENC:NsClientControlSecret:").reversed()
        override fun isValidDataString(data: String?): Boolean = !rejectsBlobValidation && data != null && data.startsWith("ENC:")
        override fun deleteKey(keystoreAlias: String) {}
    }

    private lateinit var sut: AuthorizedClientsRepository

    private var stored = "[]"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        rejectsBlobValidation = false
        whenever(preferences.get(StringNonKey.NsClientControlAuthorizedClients)).thenAnswer { stored }
        whenever(preferences.put(any<StringNonKey>(), any<String>())).thenAnswer { invocation ->
            stored = invocation.arguments[1] as String
        }
        sut = AuthorizedClientsRepository(preferences, secureEncrypt, aapsLogger)
    }

    @Test
    fun addPendingPersistsEncryptedSecretOnly() {
        val (entry, secretHex) = sut.addPending("phone", pairTtlMs = 60_000L, now = 1_000L)
        assertThat(secretHex).hasLength(64)
        assertThat(entry.encryptedSecret).startsWith("ENC:NsClientControlSecret:")
        assertThat(stored).contains(entry.clientId)
        assertThat(stored).doesNotContain(secretHex) // raw secret never persisted
    }

    @Test
    fun secretLookupRoundtripsAddedSecret() {
        val (entry, secretHex) = sut.addPending("phone", pairTtlMs = 60_000L, now = 1_000L)
        val lookup = sut.secretLookup(entry.clientId)!!
        assertThat(lookup.secretBytes).hasLength(32)
        assertThat(lookup.counterReceived).isEqualTo(0L) // pending entry, no counter accepted yet
        val recovered = lookup.secretBytes.joinToString("") { "%02x".format(it) }
        assertThat(recovered).isEqualTo(secretHex)
    }

    @Test
    fun secretLookupReturnsCounterAfterMarkActive() {
        val (entry, _) = sut.addPending("phone", 60_000L, 1_000L)
        sut.markActive(entry.clientId, counterReceived = 7L, now = 5_000L)
        assertThat(sut.secretLookup(entry.clientId)!!.counterReceived).isEqualTo(7L)
    }

    @Test
    fun secretLookupReturnsNullForUnknownClient() {
        assertThat(sut.secretLookup("missing")).isNull()
    }

    @Test
    fun secretLookupReturnsNullOnCorruptStorage() {
        stored = "{not json"
        assertThat(sut.secretLookup("anything")).isNull()
    }

    @Test
    fun secretLookupReturnsNullWhenBlobValidationFails() {
        val (entry, _) = sut.addPending("a", 60_000L, 1_000L)
        rejectsBlobValidation = true // simulate corrupted ciphertext blob
        assertThat(sut.secretLookup(entry.clientId)).isNull()
    }

    @Test
    fun deleteRemovesEntry() {
        val (entry, _) = sut.addPending("a", 60_000L, 1_000L)
        sut.delete(entry.clientId)
        assertThat(sut.current(2_000L)).isEmpty()
    }

    @Test
    fun markActivePromotesPending() {
        val (entry, _) = sut.addPending("a", 60_000L, 1_000L)
        sut.markActive(entry.clientId, counterReceived = 1L, now = 5_000L)
        val list = sut.current(5_000L)
        assertThat(list).hasSize(1)
        assertThat(list[0].state).isEqualTo(ClientState.Active)
        assertThat(list[0].counterReceived).isEqualTo(1L)
        assertThat(list[0].lastSeenAt).isEqualTo(5_000L)
    }

    @Test
    fun markActiveIsNoopForActive() {
        val (entry, _) = sut.addPending("a", 60_000L, 1_000L)
        sut.markActive(entry.clientId, 1L, 5_000L)
        val before = stored
        sut.markActive(entry.clientId, 99L, 9_000L) // already active — should be ignored
        assertThat(stored).isEqualTo(before)
    }

    @Test
    fun bumpLastSeenUpdatesActiveEntry() {
        val (entry, _) = sut.addPending("a", 60_000L, 1_000L)
        sut.markActive(entry.clientId, 1L, 5_000L)
        sut.bumpLastSeen(entry.clientId, counterReceived = 7L, now = 10_000L)
        val list = sut.current(10_000L)
        assertThat(list[0].counterReceived).isEqualTo(7L)
        assertThat(list[0].lastSeenAt).isEqualTo(10_000L)
    }

    @Test
    fun currentPrunesExpiredPending() {
        sut.addPending("a", pairTtlMs = 60_000L, now = 1_000L)
        sut.addPending("b", pairTtlMs = 60_000L, now = 1_000L)
        // Both expire at 61_000
        val list = sut.current(now = 200_000L)
        assertThat(list).isEmpty()
    }

    @Test
    fun currentDoesNotPruneActive() {
        val (entry, _) = sut.addPending("a", 60_000L, 1_000L)
        sut.markActive(entry.clientId, 1L, 5_000L)
        // Pretend pairExpiresAt is in the past — Active state must keep the entry
        val list = sut.current(now = 200_000L)
        assertThat(list).hasSize(1)
    }

    @Test
    fun pruneExpiredReturnsRemovedClientIds() {
        val (a, _) = sut.addPending("a", 60_000L, 1_000L)
        val (b, _) = sut.addPending("b", 60_000L, 1_000L)
        val (c, _) = sut.addPending("c", 60_000L, 1_000L)
        sut.markActive(c.clientId, 1L, 5_000L)
        val removed = sut.pruneExpired(now = 200_000L)
        assertThat(removed).containsExactly(a.clientId, b.clientId)
        assertThat(sut.current(200_000L)).hasSize(1)
    }

    @Test
    fun pruneExpiredReturnsEmptyWhenNothingRemoved() {
        sut.addPending("a", 60_000L, 1_000L)
        assertThat(sut.pruneExpired(now = 5_000L)).isEmpty()
    }

    @Test
    fun decodeReturnsEmptyOnGarbageStorage() {
        stored = "{not json"
        assertThat(sut.current(0L)).isEmpty()
    }

    @Test
    fun currentSkipsPersistWhenNothingPruned() {
        sut.addPending("a", 60_000L, 1_000L) // 1 put
        sut.current(now = 5_000L) // not expired — must not write
        sut.current(now = 5_000L) // again — still must not write
        verify(preferences, times(1)).put(any<StringNonKey>(), any<String>())
    }

    @Test
    fun multipleClientsAreUniquelyIdentified() {
        val (a, _) = sut.addPending("a", 60_000L, 1_000L)
        val (b, _) = sut.addPending("b", 60_000L, 1_000L)
        assertThat(a.clientId).isNotEqualTo(b.clientId)
        assertThat(sut.current(2_000L).map { it.clientId }).containsExactly(a.clientId, b.clientId)
    }

    @Test
    fun unknownClientIdInDeleteIsNoOp() {
        sut.addPending("a", 60_000L, 1_000L)
        val before = stored
        sut.delete("missing")
        assertThat(stored).isEqualTo(before)
    }
}
