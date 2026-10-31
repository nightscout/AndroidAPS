package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderAction
import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.bolus.RemoteCaller
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.authorization.NSAuthSubject
import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.AckPhase
import app.aaps.core.nssdk.localmodel.clientcontrol.PongPayload
import app.aaps.core.nssdk.localmodel.clientcontrol.BatchActionDto
import app.aaps.core.nssdk.localmodel.clientcontrol.BolusPreview
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.localmodel.clientcontrol.KeyExchangeEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.PrefEntry
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.core.nssdk.utils.ClientControlKeyExchangeCrypto
import app.aaps.plugins.sync.SyncStringsValues
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ClientControlReceiverTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var aapsLogger: AAPSLogger
    private val rh = generatedTextResolver("sync" to SyncStringsValues::textOf)
    @Mock private lateinit var nsClientRepository: NSClientRepository
    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient
    @Mock private lateinit var sceneAutomationApi: SceneAutomationApi
    @Mock private lateinit var offerPublisher: PairingOfferPublisher
    @Mock private lateinit var clientTokens: ClientTokens
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var runningConfigurationPublisher: RunningConfigurationPublisher
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var masterActionExecutor: MasterActionExecutor
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var config: Config
    @Mock private lateinit var bolusProgressData: BolusProgressData
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var masterSigningKey: MasterSigningKey
    @Mock private lateinit var eatReminder: EatReminder

    // Same deterministic fake as the repository tests — encrypt/decrypt round-trip via reverse,
    // so the persisted form does not contain the original plaintext.
    private val secureEncrypt = object : SecureEncrypt {
        override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = "ENC:$keystoreAlias:${plaintextSecret.reversed()}"
        override fun decrypt(encryptedSecret: String): String = encryptedSecret.removePrefix("ENC:NsClientControlSecret:").reversed()
        override fun isValidDataString(data: String?): Boolean = data != null && data.startsWith("ENC:")
        override fun deleteKey(keystoreAlias: String) {}
    }

    private lateinit var authorizedRepository: AuthorizedClientsRepository
    private lateinit var sut: ClientControlReceiver

    private var stored = "[]"

    // Per-key backing store — the scenes-update merge writes a different StringNonKey
    // (`SceneDefinitions`) than the authorized-clients repository, so a shared `stored`
    // would clobber whichever was written last. Keyed by [StringNonKey.key].
    private val storage = mutableMapOf<String, String>()
    private val now = 1_700_000_000_000L

    // Controllable bolus-progress flow the master observer mirrors from.
    private val bolusState = MutableStateFlow<BolusProgressState?>(null)

    // Stand-in for BolusProgressData.currentGeneration (bumped by start() in production). The mirror only
    // forwards a bolus whose generation is newer than the one captured at arm, so tests advance this to
    // simulate the client's bolus actually starting.
    private var progressGeneration = 0L

    // Scope injected into the SUT — cancelled in tearDown(). The master progress-mirror collector (and its
    // periodic heartbeat) is a long-lived coroutine; without cancellation it would outlive the test and fire
    // after Mockito cleared the inline mocks, surfacing as a flaky "uncaught exception before the next test".
    private lateinit var appScope: CoroutineScope

    @AfterEach
    fun tearDown() {
        if (::appScope.isInitialized) appScope.cancel()
    }

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        stored = "[]"
        storage.clear()
        storage[StringNonKey.SceneDefinitions.key] = "[]"
        whenever(preferences.get(StringNonKey.NsClientControlAuthorizedClients)).thenAnswer { stored }
        // Client control ON by default — the receiver gates on hardened mode (on the master it is the switch) and
        // rejects commands when it is off; see the controlOff tests.
        whenever(preferences.hardenedMode).thenReturn(true)
        whenever(preferences.get(StringNonKey.SceneDefinitions)).thenAnswer {
            storage[StringNonKey.SceneDefinitions.key] ?: "[]"
        }
        whenever(preferences.put(any<StringNonKey>(), any<String>())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as StringNonKey
            val value = invocation.arguments[1] as String
            when (key) {
                StringNonKey.SceneDefinitions,
                StringNonKey.InsulinConfiguration -> storage[key.key] = value

                else                              -> stored = value
            }
        }
        authorizedRepository = AuthorizedClientsRepository(preferences, secureEncrypt, aapsLogger)
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(bolusProgressData.state).thenReturn(bolusState)
        whenever(bolusProgressData.currentGeneration).thenAnswer { progressGeneration }
        appScope = CoroutineScope(Dispatchers.Unconfined)
        sut = ClientControlReceiver(
            authorizedRepository,
            { nsClientV3Plugin },
            nsClientRepository,
            sceneAutomationApi,
            offerPublisher,
            preferences,
            dateUtil,
            uel,
            runningConfigurationPublisher,
            persistenceLayer,
            masterActionExecutor,
            notificationManager,
            config,
            bolusProgressData,
            commandQueue,
            rh,
            aapsLogger,
            appScope,
            clientTokens,
            masterSigningKey,
            eatReminder
        )
    }

    /** Adds a pending client with [role] and returns (clientId, secretBytes). */
    private suspend fun pair(name: String = "phone", role: ClientRole = ClientRole.Admin): Pair<String, ByteArray> {
        val (entry, secretHex) = authorizedRepository.addPending(name, role, pairTtlMs = 60_000L, now = now - 10_000L)
        val secretBytes = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return entry.clientId to secretBytes
    }

    /**
     * Adds a pending client whose pairing window already expired relative to `now`.
     * Created 2h ago with a 5-min TTL, so pairExpiresAt is well in the past.
     */
    private suspend fun pairExpired(name: String = "phone"): Pair<String, ByteArray> {
        val (entry, secretHex) = authorizedRepository.addPending(name, ClientRole.Admin, pairTtlMs = 5 * 60_000L, now = now - 2 * 60 * 60 * 1000L)
        val secretBytes = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return entry.clientId to secretBytes
    }

    // Mirror the publisher's Json instance so test envelopes contain the same payload bytes
    // production would emit — particularly: `encodeDefaults = true` writes `protocolVersion: 1`
    // explicitly. Without this, the test could pass on shorter bytes than production sends.
    private val publisherJson = Json { encodeDefaults = true }

    private fun envelope(
        clientId: String,
        secret: ByteArray,
        message: ClientControlMessage = ClientControlMessage.Hello(),
        counter: Long = 1L,
        timestamp: Long = now,
        validUntil: Long = Long.MAX_VALUE,
        wantsAck: Boolean = false
    ): SignedEnvelope {
        val payload = publisherJson.encodeToString(ClientControlMessage.serializer(), message)
        val type = publisherJson.parseToJsonElement(payload).jsonObject["type"]!!.jsonPrimitive.content
        return signedEnvelope(
            clientId = clientId,
            secret = secret,
            type = type,
            payload = payload,
            counter = counter,
            timestamp = timestamp,
            validUntil = validUntil,
            wantsAck = wantsAck
        )
    }

    /** Lower-level helper for tests that need to forge a non-decodable payload while keeping a valid signature. */
    private fun signedEnvelope(clientId: String, secret: ByteArray, type: String, payload: String, counter: Long = 1L, timestamp: Long = now, validUntil: Long = Long.MAX_VALUE, wantsAck: Boolean = false): SignedEnvelope =
        ClientControlCrypto.signEnvelope(
            secret,
            SignedEnvelope(clientId = clientId, counter = counter, timestamp = timestamp, type = type, payload = payload, signature = "", validUntil = validUntil, wantsAck = wantsAck)
        )

    private fun wrap(envelope: SignedEnvelope): JsonObject =
        buildJsonObject {
            put("date", envelope.timestamp)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", 1)
            put("envelope", Json.encodeToJsonElement(SignedEnvelope.serializer(), envelope))
        }

    private val deleteOk = CreateUpdateResponse(response = 200, identifier = null, isDeduplication = false, deduplicatedIdentifier = null, lastModified = null, errorResponse = null)

    @Test
    fun helloFromPendingClientPromotesToActive() = runTest {
        val (clientId, secret) = pair()
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId
        val doc = wrap(envelope(clientId, secret, counter = 1L))

        sut.onSettingsDocChanged(identifier, doc)

        val entry = authorizedRepository.current(now).single { it.clientId == clientId }
        assertThat(entry.state).isEqualTo(ClientState.Active)
        assertThat(entry.counterReceived).isEqualTo(1L)
        // Success path no longer deletes the hello doc — NS would tombstone the slot and reject
        // the next same-type command with HTTP 410. Counter dedup handles replay protection.
        verify(nsAndroidClient, never()).deleteSettings(identifier)
        // …but the offer doc IS dropped on the Pending → Active transition so the wrapped secret
        // does not linger on NS past pairing-complete.
        verify(offerPublisher).deleteOffer(clientId)
    }

    // ---- key exchange in the hello: the secret that travelled is replaced ----

    private val writeOk = CreateUpdateResponse(response = 200, identifier = null, isDeduplication = false, deduplicatedIdentifier = null, lastModified = null, errorResponse = null)

    /** Sends a hello with a key from [clientKeys] and returns the master's answer as written to Nightscout. */
    private suspend fun helloWithKey(clientId: String, secret: ByteArray, clientKeys: ClientControlKeyExchangeCrypto.KeyPair, subject: String = "mujtelefon"): KeyExchangeEnvelope {
        whenever(nsAndroidClient.updateSettings(any(), any())).thenReturn(writeOk)
        val doc = JsonObject(wrap(envelope(clientId, secret, ClientControlMessage.Hello(publicKey = clientKeys.publicKey), counter = 1L)) + ("subject" to JsonPrimitive(subject)))
        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId, doc)
        val written = argumentCaptor<JsonObject>()
        verify(nsAndroidClient).updateSettings(eq(ClientControlPublisher.IDENTIFIER_KEY_PREFIX + clientId), written.capture())
        return Json.decodeFromJsonElement(KeyExchangeEnvelope.serializer(), written.firstValue["key"]!!)
    }

    private fun clientSideKeys(answer: KeyExchangeEnvelope, clientKeys: ClientControlKeyExchangeCrypto.KeyPair, oldSecret: ByteArray, clientId: String) =
        ClientControlKeyExchangeCrypto.deriveKeys(clientKeys.privateKey, answer.masterPublicKey, oldSecret, clientId, clientKeys.publicKey, answer.masterPublicKey)

    /**
     * The pong carries the master's key for its settings docs, inside the ACK the pairing secret signs: every client
     * pings on each connect, so this is how a paired client learns the key.
     */
    @Test
    fun thePongCarriesTheKeyTheSettingsDocsAreSignedWith() = runTest {
        whenever(masterSigningKey.publicKey()).thenReturn("master-key")
        whenever(nsAndroidClient.updateSettings(any(), any())).thenReturn(writeOk)
        val (clientId, secret) = pair()
        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId, wrap(envelope(clientId, secret, counter = 1L)))

        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_CMD_PREFIX + "ping_" + clientId, wrap(envelope(clientId, secret, ClientControlMessage.Ping, counter = 2L, wantsAck = true)))

        val written = argumentCaptor<JsonObject>()
        verify(nsAndroidClient, atLeastOnce()).updateSettings(eq(ClientControlPublisher.IDENTIFIER_ACK_PREFIX + clientId), written.capture())
        val done = written.allValues.map { Json.decodeFromJsonElement(AckEnvelope.serializer(), it["ack"]!!) }.single { it.phase == AckPhase.Done }
        assertThat(ClientControlCrypto.verifyAck(secret, done)).isTrue()
        assertThat(Json.decodeFromString(PongPayload.serializer(), done.payload!!).signingKey).isEqualTo("master-key")
    }

    @Test
    fun aHelloWithAKeyIsAnsweredWithAKeyExchange() = runTest {
        whenever(clientTokens.subjects()).thenReturn(ClientTokens.Result.NotAllowed)
        val (clientId, secret) = pair()
        val clientKeys = ClientControlKeyExchangeCrypto.newKeyPair()

        val answer = helloWithKey(clientId, secret, clientKeys)

        // Signed with the old secret, the only one the client has, and naming the client's key.
        assertThat(ClientControlCrypto.verifyKeyExchange(secret, answer)).isTrue()
        assertThat(answer.clientPublicKey).isEqualTo(clientKeys.publicKey)
        // The client comes to the same new secret, which waits on the master next to the old one.
        val lookup = authorizedRepository.secretLookup(clientId)!!
        assertThat(lookup.pendingSecretBytes).isEqualTo(clientSideKeys(answer, clientKeys, secret, clientId).secret)
        assertThat(lookup.secretBytes).isEqualTo(secret)
        // Without admin rights there is no token to send.
        assertThat(answer.token).isNull()
    }

    /** Until the client has the answer it signs with the old secret, and that still works. */
    @Test
    fun theOldSecretStillWorksUntilTheClientUsesTheNewOne() = runTest {
        whenever(clientTokens.subjects()).thenReturn(ClientTokens.Result.NotAllowed)
        val (clientId, secret) = pair()
        helloWithKey(clientId, secret, ClientControlKeyExchangeCrypto.newKeyPair())

        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_CMD_PREFIX + "ping_" + clientId, wrap(envelope(clientId, secret, ClientControlMessage.Ping, counter = 2L, wantsAck = true)))

        val lookup = authorizedRepository.secretLookup(clientId)!!
        assertThat(lookup.counterReceived).isEqualTo(2L)
        assertThat(lookup.pendingSecretBytes).isNotNull()
        verify(offerPublisher, never()).deleteKeyExchange(any())
    }

    /** The first message with the new secret: the old one, which travelled, stops working, and the answer goes. */
    @Test
    fun theFirstMessageWithTheNewSecretMakesItTheOnlyOne() = runTest {
        whenever(clientTokens.subjects()).thenReturn(ClientTokens.Result.NotAllowed)
        val (clientId, secret) = pair()
        val clientKeys = ClientControlKeyExchangeCrypto.newKeyPair()
        val newSecret = clientSideKeys(helloWithKey(clientId, secret, clientKeys), clientKeys, secret, clientId).secret

        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_CMD_PREFIX + "ping_" + clientId, wrap(envelope(clientId, newSecret, ClientControlMessage.Ping, counter = 2L, wantsAck = true)))

        val lookup = authorizedRepository.secretLookup(clientId)!!
        assertThat(lookup.secretBytes).isEqualTo(newSecret)
        assertThat(lookup.pendingSecretBytes).isNull()
        verify(offerPublisher).deleteKeyExchange(clientId)

        // Signed with the old secret now: not accepted.
        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_CMD_PREFIX + "ping_" + clientId, wrap(envelope(clientId, secret, ClientControlMessage.Ping, counter = 3L, wantsAck = true)))
        assertThat(authorizedRepository.secretLookup(clientId)!!.counterReceived).isEqualTo(2L)
    }

    /** A client writing with the user's admin token gets its own client token, which only it can open. */
    @Test
    fun aClientOnAnotherTokenGetsItsOwnToken() = runTest {
        whenever(clientTokens.subjects()).thenReturn(ClientTokens.Result.Ok(listOf(NSAuthSubject(id = "s0", name = "mujtelefon", roles = listOf("admin")))))
        whenever(clientTokens.isClientToken(any())).thenAnswer { (it.arguments[0] as NSAuthSubject).roles == listOf(ClientTokens.ROLE_NAME) }
        whenever(clientTokens.tokenFor("phone"))
            .thenReturn(ClientTokens.Result.Ok(NSAuthSubject(id = "s1", name = "aaps-phone", roles = listOf(ClientTokens.ROLE_NAME), accessToken = "aapsphone-0123456789abcdef")))
        val (clientId, secret) = pair()
        val clientKeys = ClientControlKeyExchangeCrypto.newKeyPair()

        val answer = helloWithKey(clientId, secret, clientKeys, subject = "mujtelefon")

        val keys = clientSideKeys(answer, clientKeys, secret, clientId)
        assertThat(ClientControlKeyExchangeCrypto.decryptToken(keys.tokenKey, answer.tokenIv!!, answer.token!!)).isEqualTo("aapsphone-0123456789abcdef")
    }

    /** A client on its own client token (set up from the QR code) keeps it. */
    @Test
    fun aClientOnItsOwnTokenGetsNone() = runTest {
        whenever(clientTokens.subjects()).thenReturn(ClientTokens.Result.Ok(listOf(NSAuthSubject(id = "s1", name = "aaps-phone", roles = listOf(ClientTokens.ROLE_NAME)))))
        whenever(clientTokens.isClientToken(any())).thenAnswer { (it.arguments[0] as NSAuthSubject).roles == listOf(ClientTokens.ROLE_NAME) }
        val (clientId, secret) = pair()

        val answer = helloWithKey(clientId, secret, ClientControlKeyExchangeCrypto.newKeyPair(), subject = "aaps-phone")

        assertThat(answer.token).isNull()
        verify(clientTokens, never()).tokenFor(any())
    }

    /** Nightscout puts the name of the token that wrote a doc in `subject`; the master keeps it for a verified message. */
    @Test
    fun theTokenNameOfAVerifiedMessageIsKept() = runTest {
        val (clientId, secret) = pair()
        val doc = JsonObject(wrap(envelope(clientId, secret, counter = 1L)) + ("subject" to JsonPrimitive("aaps-phone")))

        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId, doc)

        assertThat(authorizedRepository.current(now).single { it.clientId == clientId }.nsSubject).isEqualTo("aaps-phone")
    }

    /** A doc that does not verify says nothing about the client, so its `subject` is not kept. */
    @Test
    fun theTokenNameOfAForgedMessageIsNotKept() = runTest {
        val (clientId, _) = pair()
        val forged = JsonObject(wrap(envelope(clientId, ByteArray(32) { 7 }, counter = 1L)) + ("subject" to JsonPrimitive("someone-else")))

        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId, forged)

        assertThat(authorizedRepository.current(now).single { it.clientId == clientId }.nsSubject).isNull()
    }

    @Test
    fun pairingStoresTheClientControlSwitchWhenItIsNotStored() = runTest {
        // Unset, the switch is on only in simple mode. Storing the value in use at pairing keeps client
        // control on when the user leaves simple mode later.
        val (clientId, secret) = pair()
        whenever(preferences.getIfExists(BooleanKey.HardenedMode)).thenReturn(null)
        whenever(preferences.get(BooleanKey.HardenedMode)).thenReturn(true)

        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId, wrap(envelope(clientId, secret, counter = 1L)))

        verify(preferences).put(BooleanKey.HardenedMode, true)
    }

    @Test
    fun pairingKeepsAStoredClientControlSwitch() = runTest {
        val (clientId, secret) = pair()
        whenever(preferences.getIfExists(BooleanKey.HardenedMode)).thenReturn(false)

        sut.onSettingsDocChanged(ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId, wrap(envelope(clientId, secret, counter = 1L)))

        verify(preferences, never()).put(eq(BooleanKey.HardenedMode), any<Boolean>())
    }

    @Test
    fun helloFromAlreadyActiveClientDoesNotDeleteOfferAgain() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId
        val doc = wrap(envelope(clientId, secret, counter = 2L))

        sut.onSettingsDocChanged(identifier, doc)

        // Active hellos only bump lastSeen — no redundant offer-delete.
        verify(offerPublisher, never()).deleteOffer(clientId)
    }

    @Test
    fun helloFromAlreadyActiveClientBumpsLastSeen() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId
        val doc = wrap(envelope(clientId, secret, counter = 2L))

        sut.onSettingsDocChanged(identifier, doc)

        val entry = authorizedRepository.current(now).single { it.clientId == clientId }
        assertThat(entry.state).isEqualTo(ClientState.Active)
        assertThat(entry.counterReceived).isEqualTo(2L)
        assertThat(entry.lastSeenAt).isEqualTo(now)
        verify(nsAndroidClient, never()).deleteSettings(identifier)
    }

    @Test
    fun verifiedEnvelopeWithUndecodablePayloadAdvancesCounter() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = ClientControlPublisher.IDENTIFIER_CMD_PREFIX + clientId
        // Forge a verified envelope whose payload doesn't match any ClientControlMessage variant.
        // Simulates "newer client speaks a type this older master doesn't know yet".
        val signed = signedEnvelope(clientId, secret, type = "scene.future", payload = """{"type":"scene.future","sceneId":"x"}""", counter = 5L)

        sut.onSettingsDocChanged(identifier, wrap(signed))

        val entry = authorizedRepository.current(now).single { it.clientId == clientId }
        assertThat(entry.counterReceived).isEqualTo(5L)
        verify(nsAndroidClient, never()).deleteSettings(identifier)
    }

    @Test
    fun badSignatureLeavesDocAndDoesNotMutateState() = runTest {
        val (clientId, secret) = pair()
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId
        val signed = envelope(clientId, secret)
        val tampered = signed.copy(signature = "0".repeat(64))

        sut.onSettingsDocChanged(identifier, wrap(tampered))

        val entry = authorizedRepository.current(now).single { it.clientId == clientId }
        assertThat(entry.state).isEqualTo(ClientState.Pending)
        assertThat(entry.counterReceived).isEqualTo(0L)
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    @Test
    fun signedByWrongSecretLeavesDoc() = runTest {
        val (clientId, _) = pair()
        val wrongSecret = ByteArray(32) { 0x42 }
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, wrongSecret)))

        val entry = authorizedRepository.current(now).single { it.clientId == clientId }
        assertThat(entry.state).isEqualTo(ClientState.Pending)
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    @Test
    fun counterRegressionLeavesDocForDiagnostics() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 5L, now = now - 5_000L)
        val identifier = ClientControlPublisher.IDENTIFIER_CMD_PREFIX + clientId

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, counter = 5L)))

        val entry = authorizedRepository.current(now).single { it.clientId == clientId }
        assertThat(entry.counterReceived).isEqualTo(5L)
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    @Test
    fun timestampOutsideSkewLeavesDoc() = runTest {
        val (clientId, secret) = pair()
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId
        val ancient = envelope(clientId, secret, timestamp = now - 60 * 60 * 1000L) // 1h ago

        sut.onSettingsDocChanged(identifier, wrap(ancient))

        val entry = authorizedRepository.current(now).single { it.clientId == clientId }
        assertThat(entry.state).isEqualTo(ClientState.Pending)
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    @Test
    fun unknownClientIdIsIgnoredNotDeleted() = runTest {
        pair()
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + "stranger-uuid"
        val signed = envelope("stranger-uuid", ByteArray(32) { 0x11 })

        sut.onSettingsDocChanged(identifier, wrap(signed))

        // Never delete: tombstoning the slot would 410 the next legit PUT, and in a multi-device
        // setup this could destroy a command another master's client legitimately owns. Neither a
        // soft nor a permanent delete is ever issued for an inbound command doc.
        verify(nsAndroidClient, never()).deleteSettings(any())
        verify(nsAndroidClient, never()).deleteSettingsPermanent(any())
    }

    /**
     * A hello arriving for a pending entry whose QR window already elapsed is ignored (NOT deleted —
     * deleting would tombstone the slot and 410 future writes) and logged distinctly from the
     * "truly unknown clientId" path — the latter is operational noise (typo / stale identifier),
     * the former is the scraped-expired-QR replay signature an operator wants to notice.
     * State must not flip to Active.
     */
    @Test
    fun helloForExpiredPendingLogsPairingWindowExpiredAndIsIgnored() = runTest {
        val (clientId, secret) = pairExpired()
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + clientId

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret)))

        // Entry pruned by current(now); attacker's hello did not promote it.
        assertThat(authorizedRepository.current(now).none { it.clientId == clientId }).isTrue()
        verify(nsAndroidClient, never()).deleteSettings(any())
        verify(nsAndroidClient, never()).deleteSettingsPermanent(any())
        verify(aapsLogger).error(eq(LTag.NSCLIENT), argThat<String> { contains("pairing window expired") })
    }

    @Test
    fun missingEnvelopeFieldIsIgnoredNotDeleted() = runTest {
        pair()
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + "anything"
        val noEnvelope = buildJsonObject {
            put("date", now)
            put("app", "AAPS")
        }

        sut.onSettingsDocChanged(identifier, noEnvelope)

        verify(nsAndroidClient, never()).deleteSettings(any())
        verify(nsAndroidClient, never()).deleteSettingsPermanent(any())
    }

    @Test
    fun malformedEnvelopeJsonIsIgnoredNotDeleted() = runTest {
        pair()
        val identifier = ClientControlPublisher.IDENTIFIER_HELLO_PREFIX + "anything"
        val malformed = buildJsonObject { put("envelope", buildJsonObject { put("garbage", true) }) }

        sut.onSettingsDocChanged(identifier, malformed)

        verify(nsAndroidClient, never()).deleteSettings(any())
        verify(nsAndroidClient, never()).deleteSettingsPermanent(any())
    }

    @Test
    fun nonClientControlIdentifiersAreIgnored() = runTest {
        val (clientId, secret) = pair()
        sut.onSettingsDocChanged("aaps_runningconfiguration", wrap(envelope(clientId, secret)))

        verify(nsAndroidClient, never()).deleteSettings(any())
        verify(nsAndroidClient, never()).getSettings(any())
    }

    @Test
    fun scenePrepareReturnsPreviewAndDoesNotActivate() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_prepare_$clientId"
        val msg = ClientControlMessage.ScenePrepare(sceneId = "sleep", durationMinutes = 30)
        whenever(sceneAutomationApi.prepareScene(eq("sleep"), eq(30), anyOrNull()))
            .thenReturn(MasterActionExecutor.PrepareResult.Preview(0.0, 0, 77L, emptyList(), false, emptyList()))

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = msg, counter = 5L)))

        // Prepare only authors the confirmation + parks — it must NOT activate the scene.
        verify(sceneAutomationApi).prepareScene(eq("sleep"), eq(30), anyOrNull())
        verify(sceneAutomationApi, never()).runScene(any(), anyOrNull())
        verify(nsAndroidClient, never()).deleteSettings(identifier)
    }

    @Test
    fun sceneCommitActivatesParkedScene() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_commit_$clientId"
        val msg = ClientControlMessage.SceneCommit(bolusId = 77L)
        whenever(sceneAutomationApi.commitScene(eq(77L), any(), anyOrNull())).thenReturn(MasterActionExecutor.ConfirmResult.Delivered)

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = msg, counter = 5L)))

        verify(sceneAutomationApi).commitScene(eq(77L), any(), anyOrNull())
    }

    @Test
    fun sceneStopWithoutChainDispatchesToStopActiveScene() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_stop_$clientId"
        whenever(sceneAutomationApi.stopActiveScene()).thenReturn(SceneAutomationResult.Success)

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.SceneStop(false), counter = 5L)))

        verify(sceneAutomationApi).stopActiveScene()
        verify(sceneAutomationApi, never()).stopActiveSceneAndChain()
        verify(nsAndroidClient, never()).deleteSettings(identifier)
    }

    @Test
    fun sceneStopWithChainDispatchesToStopActiveSceneAndChain() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_stop_$clientId"
        // The receiver delegates chain-target resolution + notification to the API; it just requests
        // stop+chain. (The resolution/fallback detail is covered by the SceneAutomationApiImpl tests.)
        whenever(sceneAutomationApi.stopActiveSceneAndChain()).thenReturn(SceneAutomationResult.Success)

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.SceneStop(true), counter = 5L)))

        verify(sceneAutomationApi).stopActiveSceneAndChain()
        verify(sceneAutomationApi, never()).stopActiveScene()
        verify(nsAndroidClient, never()).deleteSettings(identifier)
    }

    /**
     * ChainCompleted with failedCount==0 is a fully successful chain — receiver must NOT log a warn,
     * even though the result type isn't [SceneAutomationResult.Success].
     */
    @Test
    fun sceneStopChainCompletedSuccessfullyDoesNotWarn() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_stop_$clientId"
        whenever(sceneAutomationApi.stopActiveSceneAndChain()).thenReturn(
            SceneAutomationResult.ChainCompleted(endedSceneName = "Sleep", targetSceneName = "Wakeup", failedCount = 0, totalCount = 3)
        )

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.SceneStop(true), counter = 5L)))

        verify(aapsLogger, never()).warn(eq(LTag.NSCLIENT), argThat<String> { contains("scene.stop failed") })
    }

    /**
     * ChainCompleted with failedCount>0 means the target activated but some actions failed.
     * Receiver must log a warn so operators reading the NS log see partial failures.
     */
    @Test
    fun sceneStopChainPartialFailureWarns() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_stop_$clientId"
        whenever(sceneAutomationApi.stopActiveSceneAndChain()).thenReturn(
            SceneAutomationResult.ChainCompleted(endedSceneName = "Sleep", targetSceneName = "Wakeup", failedCount = 2, totalCount = 3)
        )

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.SceneStop(true), counter = 5L)))

        verify(aapsLogger).warn(eq(LTag.NSCLIENT), argThat<String> { contains("scene.stop failed") && contains("chain-partial") })
    }

    @Test
    fun dismissAlarmClearsMasterNotificationAndWritesNoAck() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}dismiss_alarm_$clientId"

        // Fire-and-forget (wantsAck = false): the client cleared the relayed alarm → master clears its own copy, no ack.
        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.DismissAlarm, counter = 5L)))

        verify(notificationManager).dismiss(NotificationId.BOLUS_DELIVERY_FAILED)
        assertThat(acks).isEmpty()
    }

    @Test
    fun stopBolusCancelsAllBolusesAndWritesNoAck() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}stop_bolus_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.StopBolus, counter = 5L)))

        verify(commandQueue).cancelAllBoluses(anyOrNull())
        assertThat(acks).isEmpty()
    }

    @Test
    fun masterMirrorsProgressToArmedClientThenDisarmsOnTerminal() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(MasterActionExecutor.ConfirmResult.Delivered)
        val progressId = "${ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX}$clientId"
        val cmdId = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        // Arm: the client commits a (mocked-Delivered) bolus → progress is now targeted at this client.
        sut.onSettingsDocChanged(cmdId, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(bolusId = 42L), counter = 5L)))

        // The client's bolus actually starts → a new generation. In-flight frame → mirrored to its progress doc.
        progressGeneration = 1L
        bolusState.value = progressState(percent = 50)
        verify(nsAndroidClient).updateSettings(eq(progressId), any())

        // 100% → final frame, then disarmed: a later (master-initiated) bolus must NOT be mirrored.
        bolusState.value = progressState(percent = 100)
        bolusState.value = null
        bolusState.value = progressState(percent = 10)
        verify(nsAndroidClient, times(2)).updateSettings(eq(progressId), any())
    }

    @Test
    fun masterDoesNotMirrorAnAlreadyRunningBolusToTheCommittingClient() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(MasterActionExecutor.ConfirmResult.Delivered)
        val progressId = "${ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX}$clientId"
        val cmdId = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        // A bolus is already delivering on the master (e.g. a manual master bolus) when the client commits.
        // Its generation predates the arm, so its frames must NOT be mis-attributed to the client's dialog.
        progressGeneration = 7L
        bolusState.value = progressState(percent = 30)

        sut.onSettingsDocChanged(cmdId, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(bolusId = 42L), counter = 5L)))

        // The already-running bolus keeps emitting; the client's own bolus never starts (queue-rejected).
        bolusState.value = progressState(percent = 40)
        bolusState.value = progressState(percent = 50)
        verify(nsAndroidClient, never()).updateSettings(eq(progressId), any())
    }

    @Test
    fun masterDisarmsMirrorWhenCommitFailsBeforeAnyFrameSoALaterBolusIsNotMirrored() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        // confirm() consumed the parked dose (Delivered) but delivery fails via onError before any frame streams
        // (e.g. the master was already bolusing → queue-rejected this one). The arm must be cleared right away.
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST") val onError = inv.arguments[2] as (MasterActionExecutor.Failure) -> Unit
            onError(MasterActionExecutor.Failure("executing right now"))
            MasterActionExecutor.ConfirmResult.Delivered
        }
        val progressId = "${ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX}$clientId"
        val cmdId = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        progressGeneration = 5L // generation at commit time
        sut.onSettingsDocChanged(cmdId, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(bolusId = 42L), counter = 5L)))

        // A LATER, unrelated master-local bolus starts (NEWER generation) within the arm-TTL window. Only the
        // disarm-on-failure stops it being mirrored — the generation-gate alone (6 > 5) would have let it through.
        progressGeneration = 6L
        bolusState.value = progressState(percent = 20)
        verify(nsAndroidClient, never()).updateSettings(eq(progressId), any())
    }

    private fun progressState(percent: Int) = BolusProgressState(
        insulin = 2.0, isSMB = false, isPriming = false, percent = percent, status = TextRef.Literal("x"),
        wearStatus = TextRef.Literal("x"), delivered = PumpInsulin(1.0), stopPressed = false, stopDeliveryEnabled = true
    )

    @Test
    fun bolusPrepareAcksOkWithSignedPreviewPayload() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.prepareQuickWizard(any(), anyOrNull())).thenReturn(
            MasterActionExecutor.PrepareResult.Preview(
                insulin = 2.0, carbs = 30, bolusId = 42L,
                lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "2 U")),
                advisorApplies = true,
                advisorLines = listOf(ConfirmationLine(ConfirmationRole.WARNING, "correct now"))
            )
        )
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_prepare_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusPrepare("guid-1"), counter = 5L, wantsAck = true)))

        val done = acks.last()
        assertThat(done.status.name).isEqualTo("Ok")
        // The dose + the master's confirmation lines ride back in the SIGNED payload.
        val preview = Json.decodeFromString<BolusPreview>(done.payload!!)
        assertThat(preview.bolusId).isEqualTo(42L)
        assertThat(preview.advisorApplies).isTrue()
        assertThat(preview.lines.single().text).isEqualTo("2 U")
    }

    @Test
    fun bolusPrepareErrorAcksBolusComputeFailedWithReasonInPayload() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.prepareQuickWizard(any(), anyOrNull())).thenReturn(MasterActionExecutor.PrepareResult.Error("no bg"))
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_prepare_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusPrepare("guid-1"), counter = 5L, wantsAck = true)))

        val done = acks.last()
        assertThat(done.status.name).isEqualTo("Failed")
        assertThat(done.reason).isEqualTo(FailureReason.BolusComputeFailed.name)
        assertThat(done.payload).isEqualTo("no bg") // the master's specific reason for the client to show
    }

    @Test
    fun bolusCommitDeliversAndAcksOk() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(MasterActionExecutor.ConfirmResult.Delivered)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L, asAdvisor = false), counter = 5L, wantsAck = true)))

        verify(masterActionExecutor).confirm(eq(42L), eq(Sources.NSClient), any(), eq(false), any(), anyOrNull(), any())
        assertThat(acks.last().status.name).isEqualTo("Ok")
    }

    /**
     * The LATE delivery ack — the bolus was already acked Ok, then the pump result arrived. The client turns a
     * Delivery/Failed ack into an URGENT BOLUS_ERROR alarm, so the reason has to say whether anything actually
     * failed. A command dropped on purpose (the master's queue was cleared by a settings import) must ack
     * Cancelled, or the client alarms for a bolus nothing tried to give.
     */
    @Test
    fun lateDeliveryFailureAcksCancelledWhenTheCommandWasDropped() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        // Capture the callback instead of calling it inside confirm(): the real failure arrives AFTER confirm
        // returned, which is exactly what makes it a late Delivery ack rather than part of the Done ack.
        var report: ((MasterActionExecutor.Failure) -> Unit)? = null
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenAnswer {
            report = it.getArgument(2)
            MasterActionExecutor.ConfirmResult.Delivered
        }
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"
        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L), counter = 5L, wantsAck = true)))
        assertThat(acks.last().status.name).isEqualTo("Ok")

        report!!(MasterActionExecutor.Failure("settings import", cancelled = true))

        val delivery = acks.last()
        assertThat(delivery.phase.name).isEqualTo("Delivery")
        assertThat(delivery.status.name).isEqualTo("Failed")
        assertThat(delivery.reason).isEqualTo(FailureReason.Cancelled.name)
        assertThat(delivery.payload).isEqualTo("settings import")
    }

    /** The same late ack for a real pump failure still says ExecutionFailed, so the client still alarms. */
    @Test
    fun lateDeliveryFailureAcksExecutionFailedWhenThePumpFailed() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        var report: ((MasterActionExecutor.Failure) -> Unit)? = null
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenAnswer {
            report = it.getArgument(2)
            MasterActionExecutor.ConfirmResult.Delivered
        }
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"
        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L), counter = 5L, wantsAck = true)))

        report!!(MasterActionExecutor.Failure("pump said no"))

        val delivery = acks.last()
        assertThat(delivery.phase.name).isEqualTo("Delivery")
        assertThat(delivery.reason).isEqualTo(FailureReason.ExecutionFailed.name)
    }

    @Test
    fun bolusCommitNoPendingAcksNoPendingBolus() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(MasterActionExecutor.ConfirmResult.NoPending)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L), counter = 5L, wantsAck = true)))

        val done = acks.last()
        assertThat(done.status.name).isEqualTo("Failed")
        assertThat(done.reason).isEqualTo(FailureReason.NoPendingBolus.name)
    }

    @Test
    fun controlOffRejectsCommandWithControlDisabledAckAndDoesNotExecute() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        // Master turned client control OFF → reject cleanly with a signed ACK (NOT a silent drop, which would
        // time the client out into a false "master offline" alarm) and do NOT execute the command.
        whenever(preferences.hardenedMode).thenReturn(false)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L), counter = 5L, wantsAck = true)))

        val done = acks.last()
        assertThat(done.status.name).isEqualTo("Failed")
        assertThat(done.reason).isEqualTo(FailureReason.ControlDisabled.name)
        verify(masterActionExecutor, never()).confirm(any(), any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun controlOffStillAnswersLiveness() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(preferences.hardenedMode).thenReturn(false)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}ping_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.Ping, counter = 5L, wantsAck = true)))

        // A probe is not a command: answering it keeps the client's liveness view honest.
        assertThat(acks.last().status.name).isEqualTo("Ok")
    }

    @Test
    fun bolusCommitAsAdvisorDeliversAdvisorVariant() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(MasterActionExecutor.ConfirmResult.Delivered)
        captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L, asAdvisor = true), counter = 5L, wantsAck = true)))

        verify(masterActionExecutor).confirm(eq(42L), eq(Sources.NSClient), any(), eq(true), any(), anyOrNull(), any())
    }

    @Test
    fun wizardPrepareAcksOkWithSignedPreviewPayload() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.prepareWizard(any(), anyOrNull())).thenReturn(
            MasterActionExecutor.PrepareResult.Preview(
                insulin = 2.0, carbs = 30, bolusId = 77L,
                lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "2 U")),
                advisorApplies = false, advisorLines = emptyList()
            )
        )
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}wizard_prepare_$clientId"

        sut.onSettingsDocChanged(
            identifier,
            wrap(
                envelope(
                    clientId, secret,
                    message = ClientControlMessage.WizardPrepare(
                        bg = 120.0, carbs = 30, percentage = 100, directCorrection = 0.0, carbTime = 0,
                        useBg = true, useCob = true, useIob = true, useTt = true, useTrend = false, alarm = false, notes = ""
                    ),
                    counter = 5L, wantsAck = true
                )
            )
        )

        val done = acks.last()
        assertThat(done.status.name).isEqualTo("Ok")
        val preview = Json.decodeFromString<BolusPreview>(done.payload!!)
        assertThat(preview.bolusId).isEqualTo(77L)
        assertThat(preview.lines.single().text).isEqualTo("2 U")
    }

    @Test
    fun batchPrepareAcksOkWithSignedPreviewPayload() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(masterActionExecutor.prepareBatch(any(), anyOrNull())).thenReturn(
            MasterActionExecutor.PrepareResult.Preview(
                insulin = 1.5, carbs = 0, bolusId = 99L,
                lines = listOf(ConfirmationLine(ConfirmationRole.BOLUS, "1.5 U")),
                advisorApplies = false, advisorLines = emptyList()
            )
        )
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}batch_prepare_$clientId"

        sut.onSettingsDocChanged(
            identifier,
            wrap(envelope(clientId, secret, message = ClientControlMessage.BatchPrepare(listOf(BatchActionDto(type = BatchActionDto.TYPE_BOLUS, insulin = 1.5))), counter = 5L, wantsAck = true))
        )

        val done = acks.last()
        assertThat(done.status.name).isEqualTo("Ok")
        val preview = Json.decodeFromString<BolusPreview>(done.payload!!)
        assertThat(preview.bolusId).isEqualTo(99L)
        assertThat(preview.lines.single().text).isEqualTo("1.5 U")
    }

    @Test
    fun wsPushSkipsOfferDocs() = runTest {
        val identifier = "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}any-client-id"
        val doc = buildJsonObject { put("identifier", identifier); put("date", now) }
        sut.onSettingsDocChanged(identifier, doc)
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    // -- two-step ACK -----------------------------------------------------------------------

    /** Captures the ACK envelopes the master writes to this client's ack identifier. */
    private suspend fun captureAcks(clientId: String): MutableList<AckEnvelope> {
        val acks = mutableListOf<AckEnvelope>()
        whenever(nsAndroidClient.updateSettings(eq(ClientControlPublisher.IDENTIFIER_ACK_PREFIX + clientId), any())).thenAnswer {
            val doc = it.getArgument<JsonObject>(1)
            acks += Json.decodeFromString<AckEnvelope>(doc["ack"]!!.jsonObject.toString())
            deleteOk
        }
        return acks
    }

    @Test
    fun fireAndForgetCommandWritesNoAck() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val acks = captureAcks(clientId)

        // wantsAck = false (default) → command is processed but no ACK doc is written.
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}ping_$clientId"
        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.Ping, counter = 5L, wantsAck = false)))

        assertThat(acks).isEmpty()
    }

    @Test
    fun pingAcksOkAndAdvancesCounter() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}ping_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.Ping, counter = 5L, wantsAck = true)))

        assertThat(acks.last().phase.name).isEqualTo("Done")
        assertThat(acks.last().status.name).isEqualTo("Ok")
        assertThat(authorizedRepository.current(now).single { it.clientId == clientId }.counterReceived).isEqualTo(5L)
    }

    @Test
    fun expiredCommandIsNotExecutedAndAcksExpired() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}ping_$clientId"
        // Timestamp within skew, but validUntil already elapsed → master must NOT execute (acks Expired before dispatch).
        val env = envelope(clientId, secret, message = ClientControlMessage.Ping, counter = 5L, validUntil = now - 1L, wantsAck = true)

        sut.onSettingsDocChanged(identifier, wrap(env))

        assertThat(acks.single().status.name).isEqualTo("Expired")
        // Counter consumed so a late replay can't fire it.
        assertThat(authorizedRepository.current(now).single { it.clientId == clientId }.counterReceived).isEqualTo(5L)
    }

    // -- preferences_update (generic synced-pref channel) ---------------------------------

    private val concentrationKey = BooleanKey.GeneralInsulinConcentration.key

    private suspend fun sendPreferencesUpdate(clientId: String, secret: ByteArray, prefs: Map<String, PrefEntry>, counter: Long = 5L) {
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}preferences_update_$clientId"
        val msg = ClientControlMessage.PreferencesUpdate(prefs)
        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = msg, counter = counter)))
    }

    @Test
    fun preferencesUpdateAppliesStrictlyNewerBidirectionalKey() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(preferences.get(concentrationKey)).thenReturn(BooleanKey.GeneralInsulinConcentration)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, concentrationKey)).thenReturn(1_000L)

        sendPreferencesUpdate(clientId, secret, mapOf(concentrationKey to PrefEntry("true", 2_000L)))

        verify(preferences).putRemote(BooleanKey.GeneralInsulinConcentration, true, 2_000L)
    }

    @Test
    fun preferencesUpdateAlwaysForcesColdRepublishEvenWhenLwwDrops() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(preferences.get(concentrationKey)).thenReturn(BooleanKey.GeneralInsulinConcentration)
        // Stored stamp newer than the push → LWW drops it (a no-op apply).
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, concentrationKey)).thenReturn(5_000L)

        sendPreferencesUpdate(clientId, secret, mapOf(concentrationKey to PrefEntry("true", 1_000L)))

        // The client must still get the authoritative value back so it can converge off its optimistic edit.
        verify(runningConfigurationPublisher).requestColdRepublish()
    }

    @Test
    fun preferencesUpdateDropsStaleByLww() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(preferences.get(concentrationKey)).thenReturn(BooleanKey.GeneralInsulinConcentration)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, concentrationKey)).thenReturn(5_000L)

        sendPreferencesUpdate(clientId, secret, mapOf(concentrationKey to PrefEntry("true", 1_000L)))

        verify(preferences, never()).putRemote(any<BooleanNonPreferenceKey>(), any(), any())
    }

    @Test
    fun preferencesUpdateAppliesStringKey() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(preferences.get(StringKey.AutomationLocation.key)).thenReturn(StringKey.AutomationLocation)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, StringKey.AutomationLocation.key)).thenReturn(1_000L)

        sendPreferencesUpdate(clientId, secret, mapOf(StringKey.AutomationLocation.key to PrefEntry("GPS", 2_000L)))

        verify(preferences).putRemote(StringKey.AutomationLocation, "GPS", 2_000L)
    }

    @Test
    fun preferencesUpdateAppliesIntKey() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(preferences.get(IntKey.OverviewBolusPercentage.key)).thenReturn(IntKey.OverviewBolusPercentage)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, IntKey.OverviewBolusPercentage.key)).thenReturn(1_000L)

        sendPreferencesUpdate(clientId, secret, mapOf(IntKey.OverviewBolusPercentage.key to PrefEntry("80", 2_000L)))

        verify(preferences).putRemote(IntKey.OverviewBolusPercentage, 80, 2_000L)
    }

    @Test
    fun preferencesUpdateRejectsUnknownKey() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(preferences.get("not_a_synced_key")).thenReturn(null)

        sendPreferencesUpdate(clientId, secret, mapOf("not_a_synced_key" to PrefEntry("true", 9_000L)))

        verify(preferences, never()).putRemote(any<BooleanNonPreferenceKey>(), any(), any())
    }

    // -- client roles -------------------------------------------------------------------------

    /** A paired, active client with [role]; returns (clientId, secret). */
    private suspend fun activeClient(role: ClientRole): Pair<String, ByteArray> =
        pair(role = role).also { (clientId, _) -> authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L) }

    private suspend fun sendBatch(clientId: String, secret: ByteArray, actions: List<BatchActionDto>) =
        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}batch_prepare_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.BatchPrepare(actions), counter = 5L, wantsAck = true))
        )

    private val bolusAction = BatchActionDto(type = BatchActionDto.TYPE_BOLUS, insulin = 1.5)
    private val tempTargetAction = BatchActionDto(type = BatchActionDto.TYPE_TEMP_TARGET, reason = "EATING_SOON", lowMgdl = 90.0, highMgdl = 90.0, durationMinutes = 30)

    @Test
    fun readOnlyClientIsRefusedABolusCommitWithNotAllowedForRole() = runTest {
        val (clientId, secret) = activeClient(ClientRole.ReadOnly)
        val acks = captureAcks(clientId)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L), counter = 5L, wantsAck = true))
        )

        assertThat(acks.last().reason).isEqualTo(FailureReason.NotAllowedForRole.name)
        verify(masterActionExecutor, never()).confirm(any(), any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun readOnlyClientStillAnswersLiveness() = runTest {
        val (clientId, secret) = activeClient(ClientRole.ReadOnly)
        val acks = captureAcks(clientId)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}ping_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.Ping, counter = 5L, wantsAck = true))
        )

        assertThat(acks.last().status.name).isEqualTo("Ok")
    }

    // -- the client's own Nightscout token ------------------------------------------------------

    /** [message] from [clientId], written to Nightscout with the token [subject] (Nightscout puts its name in `subject`). */
    private suspend fun sendWith(clientId: String, secret: ByteArray, message: ClientControlMessage, subject: String, counter: Long) {
        val type = if (message is ClientControlMessage.Ping) "ping" else "dismiss_alarm"
        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}${type}_$clientId",
            JsonObject(wrap(envelope(clientId, secret, message = message, counter = counter, wantsAck = true)) + ("subject" to JsonPrimitive(subject)))
        )
    }

    /** Once a client has used its own token, a command written with another one is refused, even with a valid signature. */
    @Test
    fun aClientThatUsedItsOwnTokenIsRefusedACommandWrittenWithAnother() = runTest {
        val (clientId, secret) = activeClient(ClientRole.Admin)
        val acks = captureAcks(clientId)
        sendWith(clientId, secret, ClientControlMessage.Ping, subject = "aaps-phone", counter = 2L)
        assertThat(authorizedRepository.current(now).single().ownToken).isEqualTo("aaps-phone")

        sendWith(clientId, secret, ClientControlMessage.DismissAlarm, subject = "mujtelefon", counter = 3L)

        assertThat(acks.last().reason).isEqualTo(FailureReason.OtherToken.name)
        verify(notificationManager, never()).dismiss(NotificationId.BOLUS_DELIVERY_FAILED)
    }

    @Test
    fun aClientWithItsOwnTokenIsServed() = runTest {
        val (clientId, secret) = activeClient(ClientRole.Admin)
        captureAcks(clientId)
        sendWith(clientId, secret, ClientControlMessage.Ping, subject = "aaps-phone", counter = 2L)

        sendWith(clientId, secret, ClientControlMessage.DismissAlarm, subject = "aaps-phone", counter = 3L)

        verify(notificationManager).dismiss(NotificationId.BOLUS_DELIVERY_FAILED)
    }

    /** The ping still passes with another token: the phone stays online and can show why its commands are refused. */
    @Test
    fun aBoundClientStillAnswersLivenessWithAnotherToken() = runTest {
        val (clientId, secret) = activeClient(ClientRole.Admin)
        val acks = captureAcks(clientId)
        sendWith(clientId, secret, ClientControlMessage.Ping, subject = "aaps-phone", counter = 2L)

        sendWith(clientId, secret, ClientControlMessage.Ping, subject = "mujtelefon", counter = 3L)

        assertThat(acks.last().status.name).isEqualTo("Ok")
    }

    /**
     * A client that never used its own token (the master could not make one, or the phone kept the token it had) is
     * served as before: before the key exchange a PIN client writes with the user's token too.
     */
    @Test
    fun aClientThatNeverUsedItsOwnTokenIsServedWithAnyToken() = runTest {
        val (clientId, secret) = activeClient(ClientRole.Admin)
        captureAcks(clientId)

        sendWith(clientId, secret, ClientControlMessage.DismissAlarm, subject = "mujtelefon", counter = 2L)

        verify(notificationManager).dismiss(NotificationId.BOLUS_DELIVERY_FAILED)
        assertThat(authorizedRepository.current(now).single().ownToken).isNull()
    }

    @Test
    fun mealAssistantMayPrepareABolusAndItIsParkedForThatClient() = runTest {
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        captureAcks(clientId)
        whenever(masterActionExecutor.prepareBatch(any(), anyOrNull())).thenReturn(MasterActionExecutor.PrepareResult.NoAction)

        sendBatch(clientId, secret, listOf(bolusAction))

        // The caller travels with the prepare, so only this client can commit it, and with its role re-checked.
        verify(masterActionExecutor).prepareBatch(any(), eq(RemoteCaller(clientId, ClientRole.MealAssistant)))
    }

    @Test
    fun mealAssistantCannotCarryATempTargetInWithABolus() = runTest {
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        val acks = captureAcks(clientId)

        sendBatch(clientId, secret, listOf(bolusAction, tempTargetAction))

        assertThat(acks.last().reason).isEqualTo(FailureReason.NotAllowedForRole.name)
        verify(masterActionExecutor, never()).prepareBatch(any(), anyOrNull())
    }

    @Test
    fun caregiverMayUseEverythingButNotChangeSettings() = runTest {
        val (clientId, secret) = activeClient(ClientRole.Caregiver)
        captureAcks(clientId)
        whenever(masterActionExecutor.prepareBatch(any(), anyOrNull())).thenReturn(MasterActionExecutor.PrepareResult.NoAction)
        whenever(preferences.get(concentrationKey)).thenReturn(BooleanKey.GeneralInsulinConcentration)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, concentrationKey)).thenReturn(1_000L)

        sendBatch(clientId, secret, listOf(bolusAction, tempTargetAction))
        // The same update, with the same stubs, is written for an admin in preferencesUpdateAppliesStrictlyNewerBidirectionalKey.
        sendPreferencesUpdate(clientId, secret, mapOf(concentrationKey to PrefEntry("true", 2_000L)), counter = 6L)

        verify(masterActionExecutor).prepareBatch(any(), anyOrNull())
        verify(preferences, never()).putRemote(any<BooleanNonPreferenceKey>(), any(), any())
    }

    @Test
    fun readOnlyClientCannotStopABolusOrClearTheAlarm() = runTest {
        val (clientId, secret) = activeClient(ClientRole.ReadOnly)
        captureAcks(clientId)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}stop_bolus_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.StopBolus, counter = 5L))
        )
        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}dismiss_alarm_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.DismissAlarm, counter = 6L))
        )

        verify(commandQueue, never()).cancelAllBoluses(anyOrNull())
        verify(notificationManager, never()).dismiss(NotificationId.BOLUS_DELIVERY_FAILED)
    }

    @Test
    fun mealAssistantMayStopABolus() = runTest {
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        captureAcks(clientId)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}stop_bolus_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.StopBolus, counter = 5L))
        )

        verify(commandQueue).cancelAllBoluses(anyOrNull())
    }

    @Test
    fun mealAssistantMayStopAndExtendTheEatReminder() = runTest {
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        captureAcks(clientId)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}eat_reminder_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.EatReminderControl(EatReminderAction.EXTEND.name), counter = 5L))
        )
        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}eat_reminder_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.EatReminderControl(EatReminderAction.STOP.name), counter = 6L))
        )

        verify(eatReminder).extend()
        verify(eatReminder).stop()
    }

    @Test
    fun anExtendTheMasterSetIsAnsweredOk() = runTest {
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        val acks = captureAcks(clientId)
        whenever(eatReminder.extend()).thenReturn(true)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}eat_reminder_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.EatReminderControl(EatReminderAction.EXTEND.name), counter = 5L, wantsAck = true))
        )

        assertThat(acks.last().status.name).isEqualTo("Ok")
    }

    /** The master could not set the new alarm (#5408): nothing changed there, so the client must not be told it worked. */
    @Test
    fun anExtendTheMasterCannotSetIsAnsweredFailed() = runTest {
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        val acks = captureAcks(clientId)
        whenever(eatReminder.extend()).thenReturn(false)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}eat_reminder_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.EatReminderControl(EatReminderAction.EXTEND.name), counter = 5L, wantsAck = true))
        )

        assertThat(acks.last().status.name).isEqualTo("Failed")
    }

    @Test
    fun readOnlyClientCannotTouchTheEatReminder() = runTest {
        val (clientId, secret) = activeClient(ClientRole.ReadOnly)
        captureAcks(clientId)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}eat_reminder_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.EatReminderControl(EatReminderAction.STOP.name), counter = 5L))
        )

        verify(eatReminder, never()).stop()
    }

    @Test
    fun anUnknownEatReminderActionChangesNothing() = runTest {
        val (clientId, secret) = activeClient(ClientRole.Admin)
        captureAcks(clientId)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}eat_reminder_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.EatReminderControl("SNOOZE_FOREVER"), counter = 5L))
        )

        verify(eatReminder, never()).stop()
        verify(eatReminder, never()).extend()
    }

    @Test
    fun mealAssistantCannotEndASceneOrChangeSettings() = runTest {
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        captureAcks(clientId)
        whenever(preferences.get(concentrationKey)).thenReturn(BooleanKey.GeneralInsulinConcentration)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, concentrationKey)).thenReturn(1_000L)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_stop_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.SceneStop(false), counter = 5L))
        )
        sendPreferencesUpdate(clientId, secret, mapOf(concentrationKey to PrefEntry("true", 2_000L)), counter = 6L)

        verify(sceneAutomationApi, never()).stopActiveScene()
        verify(preferences, never()).putRemote(any<BooleanNonPreferenceKey>(), any(), any())
    }

    @Test
    fun commitThatTheExecutorRefusesForTheRoleIsReportedAsNotAllowedForRole() = runTest {
        // The role was lowered between prepare and commit: confirm() says so, and the client is told the reason.
        val (clientId, secret) = activeClient(ClientRole.MealAssistant)
        val acks = captureAcks(clientId)
        whenever(masterActionExecutor.confirm(any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(MasterActionExecutor.ConfirmResult.NotAllowed)

        sut.onSettingsDocChanged(
            "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId",
            wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L), counter = 5L, wantsAck = true))
        )

        assertThat(acks.last().reason).isEqualTo(FailureReason.NotAllowedForRole.name)
    }
}
