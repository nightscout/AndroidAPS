package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.KeyExchangeEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.core.nssdk.utils.ClientControlKeyExchangeCrypto
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

internal class ClientPairingRepositoryTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var aapsLogger: AAPSLogger

    private var rejectsBlobValidation = false

    // SecureEncrypt answers "" when the key store refuses the key (the iOS Keychain does this when the
    // app has no entitlements). Set this to reproduce that without a key store.
    private var encryptFails = false

    private val secureEncrypt = object : SecureEncrypt {
        override fun encrypt(plaintextSecret: String, keystoreAlias: String): String =
            if (encryptFails) "" else "ENC:$keystoreAlias:${plaintextSecret.reversed()}"
        override fun decrypt(encryptedSecret: String): String = encryptedSecret.removePrefix("ENC:NsClientControlSecret:").reversed()
        override fun isValidDataString(data: String?): Boolean = !rejectsBlobValidation && data != null && data.startsWith("ENC:")
        override fun deleteKey(keystoreAlias: String) {}
    }

    private lateinit var sut: ClientPairingRepository

    private val now = 1_700_000_000_000L
    private val stringStore = mutableMapOf<StringNonKey, String>()
    private val longStore = mutableMapOf<LongNonKey, Long>()

    private fun samplePayload(secretHex: String = "00".repeat(32), role: String? = null) = PairingPayload(
        masterInstallId = "master-uuid",
        clientId = "client-uuid",
        secretHex = secretHex,
        expiresAt = 99_000L,
        role = role
    )

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        rejectsBlobValidation = false
        encryptFails = false
        stringStore.clear()
        longStore.clear()
        whenever(preferences.get(any<StringNonKey>())).thenAnswer { invocation ->
            stringStore[invocation.arguments[0] as StringNonKey] ?: (invocation.arguments[0] as StringNonKey).defaultValue
        }
        whenever(preferences.put(any<StringNonKey>(), any<String>())).thenAnswer { invocation ->
            stringStore[invocation.arguments[0] as StringNonKey] = invocation.arguments[1] as String
        }
        whenever(preferences.get(any<LongNonKey>())).thenAnswer { invocation ->
            longStore[invocation.arguments[0] as LongNonKey] ?: (invocation.arguments[0] as LongNonKey).defaultValue
        }
        whenever(preferences.put(any<LongNonKey>(), any<Long>())).thenAnswer { invocation ->
            longStore[invocation.arguments[0] as LongNonKey] = invocation.arguments[1] as Long
        }
        booleanStore.clear()
        whenever(preferences.get(any<BooleanNonKey>())).thenAnswer { invocation ->
            booleanStore[invocation.arguments[0] as BooleanNonKey] ?: (invocation.arguments[0] as BooleanNonKey).defaultValue
        }
        whenever(preferences.put(any<BooleanNonKey>(), any<Boolean>())).thenAnswer { invocation ->
            booleanStore[invocation.arguments[0] as BooleanNonKey] = invocation.arguments[1] as Boolean
        }
        sut = ClientPairingRepository(preferences, secureEncrypt, aapsLogger)
    }

    private val booleanStore = mutableMapOf<BooleanNonKey, Boolean>()

    // ---- key exchange: the secret from the pairing is replaced ----

    private val oldSecretHex = "ab".repeat(32)

    /** The master's half of the exchange, with the real crypto: its answer to the client's [clientPublicKey]. */
    private fun masterAnswer(clientPublicKey: String, token: String? = null, signWith: ByteArray = ClientControlCrypto.hexToBytes(oldSecretHex)): Pair<KeyExchangeEnvelope, ByteArray> {
        val master = ClientControlKeyExchangeCrypto.newKeyPair()
        val keys = ClientControlKeyExchangeCrypto.deriveKeys(master.privateKey, clientPublicKey, ClientControlCrypto.hexToBytes(oldSecretHex), "client-uuid", clientPublicKey, master.publicKey)
        val sealed = token?.let { ClientControlKeyExchangeCrypto.encryptToken(keys.tokenKey, it) }
        val draft = KeyExchangeEnvelope("client-uuid", clientPublicKey, master.publicKey, sealed?.first, sealed?.second, now, "")
        return ClientControlCrypto.signKeyExchange(signWith, draft) to keys.secret
    }

    // ---- the role a pairing comes with ----

    /** The client shows the right controls at once, not only after the master's next roster. */
    @Test
    fun aPairingKeepsTheRoleTheMasterGaveIt() = runTest {
        sut.pair(samplePayload(role = "Caregiver"), now)

        assertThat(stringStore[StringNonKey.NsClientControlRole]).isEqualTo("Caregiver")
    }

    /** A master that does not send the role: as little as ReadOnly (empty) until its roster says more. */
    @Test
    fun aPairingWithoutARoleStoresNone() = runTest {
        stringStore[StringNonKey.NsClientControlRole] = "Admin" // from an earlier pairing
        sut.pair(samplePayload(role = null), now)

        assertThat(stringStore[StringNonKey.NsClientControlRole]).isEmpty()
    }

    /** `ClientRole.of` reads a blank name as Admin (stored before roles); from a pairing it must not mean that. */
    @Test
    fun aBlankRoleIsNotAdmin() = runTest {
        sut.pair(samplePayload(role = " "), now)

        assertThat(stringStore[StringNonKey.NsClientControlRole]).isEmpty()
    }

    @Test
    fun anUnknownRoleIsReadOnly() = runTest {
        sut.pair(samplePayload(role = "Superuser"), now)

        assertThat(stringStore[StringNonKey.NsClientControlRole]).isEqualTo("ReadOnly")
    }

    /** A hello that was not sent must not hold the next one back: the pairing offer lives only 2 minutes. */
    @Test
    fun aKeyExchangeWhoseHelloWasNotSentCanStartAgainAtOnce() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)
        val publicKey = sut.startKeyExchange(now)!!
        assertThat(sut.needsKeyExchange(now + 1_000L)).isFalse()

        sut.abandonKeyExchange(publicKey)

        assertThat(sut.needsKeyExchange(now + 1_000L)).isTrue()
    }

    /** Giving up an older exchange must not cancel the one started after it. */
    @Test
    fun givingUpAnOlderKeyExchangeLeavesTheNewOne() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)
        val older = sut.startKeyExchange(now)!!
        sut.startKeyExchange(now + 1L)

        sut.abandonKeyExchange(older)

        assertThat(sut.needsKeyExchange(now + 1_000L)).isFalse()
    }

    @Test
    fun aNewPairingNeedsAKeyExchange() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)

        assertThat(sut.needsKeyExchange(now)).isTrue()
    }

    /** A second exchange before the first one's answer could leave the master with the other key. */
    @Test
    fun noSecondKeyExchangeWhileTheFirstWaits() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)
        sut.startKeyExchange(now)

        assertThat(sut.needsKeyExchange(now + 60_000L)).isFalse()
        assertThat(sut.needsKeyExchange(now + ClientPairingRepository.KEY_EXCHANGE_WAIT_MS)).isTrue()
    }

    @Test
    fun theMastersAnswerReplacesTheSecretAndBringsTheToken() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)
        val publicKey = sut.startKeyExchange(now)!!
        val (answer, masterSecret) = masterAnswer(publicKey, token = "aapsphone-0123456789abcdef")

        val result = sut.completeKeyExchange(answer)

        assertThat((result as ClientPairingRepository.KeyExchangeResult.Done).token).isEqualTo("aapsphone-0123456789abcdef")
        // Signs with the new secret, still checks answers with the old one, and needs no further exchange.
        assertThat(ClientControlCrypto.verifyEnvelope(masterSecret, sut.nextSignedEnvelope("ping", "{}", now)!!)).isTrue()
        assertThat(sut.verificationSecrets().map { ClientControlCrypto.bytesToHex(it) }).containsExactly(ClientControlCrypto.bytesToHex(masterSecret), oldSecretHex).inOrder()
        assertThat(sut.needsKeyExchange(now + ClientPairingRepository.KEY_EXCHANGE_WAIT_MS)).isFalse()

        sut.forgetPreviousSecret()
        assertThat(sut.verificationSecrets()).hasSize(1)
    }

    @Test
    fun anAnswerToAnotherHelloIsIgnored() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)
        sut.startKeyExchange(now)
        val (answer, _) = masterAnswer(ClientControlKeyExchangeCrypto.newKeyPair().publicKey)

        assertThat(sut.completeKeyExchange(answer)).isEqualTo(ClientPairingRepository.KeyExchangeResult.Ignored)
        assertThat(sut.verificationSecrets().map { ClientControlCrypto.bytesToHex(it) }).containsExactly(oldSecretHex)
    }

    /** Someone writing to Nightscout without the pairing secret cannot hand this phone a key of their own. */
    @Test
    fun anAnswerNotSignedByTheMasterIsIgnored() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)
        val publicKey = sut.startKeyExchange(now)!!
        val (forged, _) = masterAnswer(publicKey, signWith = ByteArray(32) { 7 })

        assertThat(sut.completeKeyExchange(forged)).isEqualTo(ClientPairingRepository.KeyExchangeResult.Ignored)
        assertThat(sut.needsKeyExchange(now + ClientPairingRepository.KEY_EXCHANGE_WAIT_MS)).isTrue()
    }

    @Test
    fun unpairForgetsTheKeyExchange() = runTest {
        sut.pair(samplePayload(secretHex = oldSecretHex), now)
        sut.startKeyExchange(now)

        sut.unpair()

        assertThat(stringStore[StringNonKey.NsClientControlKeyExchangePrivateEnc]).isEmpty()
        assertThat(longStore[LongNonKey.NsClientControlKeyExchangeAt]).isEqualTo(0L)
    }

    @Test
    fun unpairedRepoReportsNotPaired() = runTest {
        assertThat(sut.isPaired()).isFalse()
        assertThat(sut.currentPairing()).isNull()
    }

    @Test
    fun pairPersistsEncryptedSecretOnly() = runTest {
        val payload = samplePayload(secretHex = "deadbeef".repeat(8))
        assertThat(sut.pair(payload, now)).isTrue()
        assertThat(sut.isPaired()).isTrue()
        val stored = stringStore[StringNonKey.NsClientControlMasterSecretEnc]!!
        assertThat(stored).startsWith("ENC:NsClientControlSecret:")
        assertThat(stored).doesNotContain(payload.secretHex) // raw never persisted
    }

    @Test
    fun currentPairingReturnsAssembledSnapshot() = runTest {
        sut.pair(samplePayload(), now)
        val pairing = sut.currentPairing()!!
        assertThat(pairing.masterInstallId).isEqualTo("master-uuid")
        assertThat(pairing.clientId).isEqualTo("client-uuid")
        assertThat(pairing.masterSecretEnc).isNotEmpty()
    }

    @Test
    fun pairResetsCounter() = runTest {
        longStore[LongNonKey.NsClientControlCounterSent] = 42L
        sut.pair(samplePayload(), now)
        assertThat(longStore[LongNonKey.NsClientControlCounterSent]).isEqualTo(0L)
    }

    @Test
    fun pairPersistsPairedAtForOrphanRaceGuard() = runTest {
        sut.pair(samplePayload(), now)
        assertThat(longStore[LongNonKey.NsClientControlPairedAt]).isEqualTo(now)
    }

    @Test
    fun unpairClearsPairedAt() = runTest {
        sut.pair(samplePayload(), now)
        sut.unpair()
        assertThat(longStore[LongNonKey.NsClientControlPairedAt]).isEqualTo(0L)
    }

    @Test
    fun unpairClearsAllKeysAndCounter() = runTest {
        sut.pair(samplePayload(), now)
        sut.nextSignedEnvelope("hello", "{}", 1_000L) // bumps counter
        sut.unpair()
        assertThat(sut.isPaired()).isFalse()
        assertThat(sut.currentPairing()).isNull()
        assertThat(longStore[LongNonKey.NsClientControlCounterSent]).isEqualTo(0L)
    }

    @Test
    fun nextSignedEnvelopeMonotonicallyIncrementsCounter() = runTest {
        sut.pair(samplePayload(), now)
        val a = sut.nextSignedEnvelope("hello", "{}", 1_000L)!!
        val b = sut.nextSignedEnvelope("scene_start", """{"id":"x"}""", 2_000L)!!
        val c = sut.nextSignedEnvelope("scene_stop", "{}", 3_000L)!!
        assertThat(a.counter).isEqualTo(1L)
        assertThat(b.counter).isEqualTo(2L)
        assertThat(c.counter).isEqualTo(3L)
    }

    @Test
    fun nextSignedEnvelopeFillsAllFields() = runTest {
        sut.pair(samplePayload(), now)
        val env = sut.nextSignedEnvelope("hello", """{"protocolVersion":1}""", 1_700_000_000_000L)!!
        assertThat(env.clientId).isEqualTo("client-uuid")
        assertThat(env.counter).isEqualTo(1L)
        assertThat(env.timestamp).isEqualTo(1_700_000_000_000L)
        assertThat(env.type).isEqualTo("hello")
        assertThat(env.payload).isEqualTo("""{"protocolVersion":1}""")
        assertThat(env.signature).isNotEmpty()
    }

    @Test
    fun nextSignedEnvelopeProducesVerifiableSignature() = runTest {
        val secretHex = "ab".repeat(32)
        sut.pair(samplePayload(secretHex = secretHex), now)
        val env = sut.nextSignedEnvelope("hello", "{}", 1_000L)!!
        val secretBytes = ClientControlCrypto.hexToBytes(secretHex)
        assertThat(ClientControlCrypto.verifyEnvelope(secretBytes, env)).isTrue()
    }

    @Test
    fun nextSignedEnvelopeReturnsNullWhenUnpaired() = runTest {
        assertThat(sut.nextSignedEnvelope("hello", "{}", 1_000L)).isNull()
    }

    @Test
    fun nextSignedEnvelopeReturnsNullOnCorruptBlob() = runTest {
        sut.pair(samplePayload(), now)
        rejectsBlobValidation = true // simulate corrupted ciphertext
        assertThat(sut.nextSignedEnvelope("hello", "{}", 1_000L)).isNull()
    }

    @Test
    fun pairOverwritesExistingPairing() = runTest {
        sut.pair(samplePayload(secretHex = "00".repeat(32)), now)
        val firstClientId = stringStore[StringNonKey.NsClientControlClientId]
        sut.pair(PairingPayload(masterInstallId = "master2", clientId = "client2", secretHex = "ff".repeat(32), expiresAt = 1L), now)
        assertThat(stringStore[StringNonKey.NsClientControlClientId]).isEqualTo("client2")
        assertThat(stringStore[StringNonKey.NsClientControlClientId]).isNotEqualTo(firstClientId)
    }

    @Test
    fun pairOverwriteResetsCounter() = runTest {
        sut.pair(samplePayload(), now)
        sut.nextSignedEnvelope("hello", "{}", 1_000L) // counter = 1
        sut.pair(PairingPayload(masterInstallId = "master2", clientId = "client2", secretHex = "ff".repeat(32), expiresAt = 1L), now)
        assertThat(longStore[LongNonKey.NsClientControlCounterSent]).isEqualTo(0L)
    }

    /**
     * The secret could not be wrapped, so nothing at all may be written. Storing the other four keys
     * used to leave the client claiming to be paired while every send was refused.
     */
    @Test
    fun pairStoresNothingWhenTheSecretCannotBeWrapped() = runTest {
        encryptFails = true
        assertThat(sut.pair(samplePayload(), now)).isFalse()
        assertThat(sut.isPaired()).isFalse()
        assertThat(sut.currentPairing()).isNull()
        assertThat(stringStore[StringNonKey.NsClientControlClientId]).isNull()
        assertThat(stringStore[StringNonKey.NsClientControlMasterInstallId]).isNull()
        assertThat(stringStore[StringNonKey.NsClientControlMasterSecretEnc]).isNull()
        assertThat(longStore[LongNonKey.NsClientControlPairedAt]).isNull()
    }

    /** A failed re-pair must not damage the pairing the client already had. */
    @Test
    fun pairFailureLeavesAnExistingPairingAlone() = runTest {
        assertThat(sut.pair(samplePayload(), now)).isTrue()
        val before = sut.currentPairing()!!

        encryptFails = true
        val other = PairingPayload(masterInstallId = "other-master", clientId = "other-client", secretHex = "11".repeat(32), expiresAt = 99_000L)
        assertThat(sut.pair(other, now + 1)).isFalse()

        val after = sut.currentPairing()!!
        assertThat(after.masterInstallId).isEqualTo(before.masterInstallId)
        assertThat(after.clientId).isEqualTo(before.clientId)
        assertThat(after.masterSecretEnc).isEqualTo(before.masterSecretEnc)
    }
}
