package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.utils.ClientControlCrypto
import com.google.common.truth.Truth.assertThat
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

    private val secureEncrypt = object : SecureEncrypt {
        override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = "ENC:$keystoreAlias:${plaintextSecret.reversed()}"
        override fun decrypt(encryptedSecret: String): String = encryptedSecret.removePrefix("ENC:NsClientControlSecret:").reversed()
        override fun isValidDataString(data: String?): Boolean = !rejectsBlobValidation && data != null && data.startsWith("ENC:")
        override fun deleteKey(keystoreAlias: String) {}
    }

    private lateinit var sut: ClientPairingRepository

    private val now = 1_700_000_000_000L
    private val stringStore = mutableMapOf<StringNonKey, String>()
    private val longStore = mutableMapOf<LongNonKey, Long>()

    private fun samplePayload(secretHex: String = "00".repeat(32)) = PairingPayload(
        masterInstallId = "master-uuid",
        clientId = "client-uuid",
        secretHex = secretHex,
        expiresAt = 99_000L
    )

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        rejectsBlobValidation = false
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
        sut = ClientPairingRepository(preferences, secureEncrypt, aapsLogger)
    }

    @Test
    fun unpairedRepoReportsNotPaired() {
        assertThat(sut.isPaired()).isFalse()
        assertThat(sut.currentPairing()).isNull()
    }

    @Test
    fun pairPersistsEncryptedSecretOnly() {
        val payload = samplePayload(secretHex = "deadbeef".repeat(8))
        sut.pair(payload, now)
        assertThat(sut.isPaired()).isTrue()
        val stored = stringStore[StringNonKey.NsClientControlMasterSecretEnc]!!
        assertThat(stored).startsWith("ENC:NsClientControlSecret:")
        assertThat(stored).doesNotContain(payload.secretHex) // raw never persisted
    }

    @Test
    fun currentPairingReturnsAssembledSnapshot() {
        sut.pair(samplePayload(), now)
        val pairing = sut.currentPairing()!!
        assertThat(pairing.masterInstallId).isEqualTo("master-uuid")
        assertThat(pairing.clientId).isEqualTo("client-uuid")
        assertThat(pairing.masterSecretEnc).isNotEmpty()
    }

    @Test
    fun pairResetsCounter() {
        longStore[LongNonKey.NsClientControlCounterSent] = 42L
        sut.pair(samplePayload(), now)
        assertThat(longStore[LongNonKey.NsClientControlCounterSent]).isEqualTo(0L)
    }

    @Test
    fun pairPersistsPairedAtForOrphanRaceGuard() {
        sut.pair(samplePayload(), now)
        assertThat(longStore[LongNonKey.NsClientControlPairedAt]).isEqualTo(now)
    }

    @Test
    fun unpairClearsPairedAt() {
        sut.pair(samplePayload(), now)
        sut.unpair()
        assertThat(longStore[LongNonKey.NsClientControlPairedAt]).isEqualTo(0L)
    }

    @Test
    fun unpairClearsAllKeysAndCounter() {
        sut.pair(samplePayload(), now)
        sut.nextSignedEnvelope("hello", "{}", 1_000L) // bumps counter
        sut.unpair()
        assertThat(sut.isPaired()).isFalse()
        assertThat(sut.currentPairing()).isNull()
        assertThat(longStore[LongNonKey.NsClientControlCounterSent]).isEqualTo(0L)
    }

    @Test
    fun nextSignedEnvelopeMonotonicallyIncrementsCounter() {
        sut.pair(samplePayload(), now)
        val a = sut.nextSignedEnvelope("hello", "{}", 1_000L)!!
        val b = sut.nextSignedEnvelope("scene_start", """{"id":"x"}""", 2_000L)!!
        val c = sut.nextSignedEnvelope("scene_stop", "{}", 3_000L)!!
        assertThat(a.counter).isEqualTo(1L)
        assertThat(b.counter).isEqualTo(2L)
        assertThat(c.counter).isEqualTo(3L)
    }

    @Test
    fun nextSignedEnvelopeFillsAllFields() {
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
    fun nextSignedEnvelopeProducesVerifiableSignature() {
        val secretHex = "ab".repeat(32)
        sut.pair(samplePayload(secretHex = secretHex), now)
        val env = sut.nextSignedEnvelope("hello", "{}", 1_000L)!!
        val secretBytes = ClientControlCrypto.hexToBytes(secretHex)
        assertThat(ClientControlCrypto.verifyEnvelope(secretBytes, env)).isTrue()
    }

    @Test
    fun nextSignedEnvelopeReturnsNullWhenUnpaired() {
        assertThat(sut.nextSignedEnvelope("hello", "{}", 1_000L)).isNull()
    }

    @Test
    fun nextSignedEnvelopeReturnsNullOnCorruptBlob() {
        sut.pair(samplePayload(), now)
        rejectsBlobValidation = true // simulate corrupted ciphertext
        assertThat(sut.nextSignedEnvelope("hello", "{}", 1_000L)).isNull()
    }

    @Test
    fun pairOverwritesExistingPairing() {
        sut.pair(samplePayload(secretHex = "00".repeat(32)), now)
        val firstClientId = stringStore[StringNonKey.NsClientControlClientId]
        sut.pair(PairingPayload(masterInstallId = "master2", clientId = "client2", secretHex = "ff".repeat(32), expiresAt = 1L), now)
        assertThat(stringStore[StringNonKey.NsClientControlClientId]).isEqualTo("client2")
        assertThat(stringStore[StringNonKey.NsClientControlClientId]).isNotEqualTo(firstClientId)
    }

    @Test
    fun pairOverwriteResetsCounter() {
        sut.pair(samplePayload(), now)
        sut.nextSignedEnvelope("hello", "{}", 1_000L) // counter = 1
        sut.pair(PairingPayload(masterInstallId = "master2", clientId = "client2", secretHex = "ff".repeat(32), expiresAt = 1L), now)
        assertThat(longStore[LongNonKey.NsClientControlCounterSent]).isEqualTo(0L)
    }
}
