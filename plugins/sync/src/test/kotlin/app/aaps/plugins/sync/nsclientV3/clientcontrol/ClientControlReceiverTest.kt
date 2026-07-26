package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
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
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.BatchActionDto
import app.aaps.core.nssdk.localmodel.clientcontrol.BolusPreview
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.localmodel.clientcontrol.PrefEntry
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

internal class ClientControlReceiverTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var nsClientRepository: NSClientRepository
    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient
    @Mock private lateinit var sceneAutomationApi: SceneAutomationApi
    @Mock private lateinit var offerPublisher: PairingOfferPublisher
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var runningConfigurationPublisher: RunningConfigurationPublisher
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var config: Config
    @Mock private lateinit var bolusProgressData: BolusProgressData
    @Mock private lateinit var commandQueue: CommandQueue

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
        // Client control ON by default — the receiver now gates on this (rejects commands when off); see controlDisabled test.
        whenever(preferences.get(BooleanKey.NsClientAllowClientControl)).thenReturn(true)
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
            Provider { nsClientV3Plugin },
            nsClientRepository,
            sceneAutomationApi,
            offerPublisher,
            preferences,
            dateUtil,
            uel,
            runningConfigurationPublisher,
            persistenceLayer,
            wizardBolusExecutor,
            notificationManager,
            config,
            bolusProgressData,
            commandQueue,
            aapsLogger,
            appScope
        )
    }

    /** Adds a pending client and returns (clientId, secretBytes). */
    private fun pair(name: String = "phone"): Pair<String, ByteArray> {
        val (entry, secretHex) = authorizedRepository.addPending(name, pairTtlMs = 60_000L, now = now - 10_000L)
        val secretBytes = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return entry.clientId to secretBytes
    }

    /**
     * Adds a pending client whose pairing window already expired relative to `now`.
     * Created 2h ago with a 5-min TTL, so pairExpiresAt is well in the past.
     */
    private fun pairExpired(name: String = "phone"): Pair<String, ByteArray> {
        val (entry, secretHex) = authorizedRepository.addPending(name, pairTtlMs = 5 * 60_000L, now = now - 2 * 60 * 60 * 1000L)
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

    private fun wrap(envelope: SignedEnvelope): JSONObject =
        JSONObject().apply {
            put("date", envelope.timestamp)
            put("utcOffset", 0)
            put("app", "AAPS")
            put("schemaVersion", 1)
            put("envelope", JSONObject(Json.encodeToString(SignedEnvelope.serializer(), envelope)))
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
        val noEnvelope = JSONObject().apply {
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
        val malformed = JSONObject().apply { put("envelope", JSONObject().apply { put("garbage", true) }) }

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
        whenever(sceneAutomationApi.prepareScene("sleep", 30))
            .thenReturn(WizardBolusExecutor.PrepareResult.Preview(0.0, 0, 77L, emptyList(), false, emptyList()))

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = msg, counter = 5L)))

        // Prepare only authors the confirmation + parks — it must NOT activate the scene.
        verify(sceneAutomationApi).prepareScene("sleep", 30)
        verify(sceneAutomationApi, never()).runScene(any(), anyOrNull())
        verify(nsAndroidClient, never()).deleteSettings(identifier)
    }

    @Test
    fun sceneCommitActivatesParkedScene() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_commit_$clientId"
        val msg = ClientControlMessage.SceneCommit(bolusId = 77L)
        whenever(sceneAutomationApi.commitScene(eq(77L), any())).thenReturn(WizardBolusExecutor.ConfirmResult.Delivered)

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = msg, counter = 5L)))

        verify(sceneAutomationApi).commitScene(eq(77L), any())
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
        whenever(wizardBolusExecutor.confirm(any(), any(), any(), any(), any())).thenReturn(WizardBolusExecutor.ConfirmResult.Delivered)
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
        whenever(wizardBolusExecutor.confirm(any(), any(), any(), any(), any())).thenReturn(WizardBolusExecutor.ConfirmResult.Delivered)
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
        whenever(wizardBolusExecutor.confirm(any(), any(), any(), any(), any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST") val onError = inv.arguments[2] as (String) -> Unit
            onError("executing right now")
            WizardBolusExecutor.ConfirmResult.Delivered
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
        insulin = 2.0, isSMB = false, isPriming = false, percent = percent, status = "x",
        wearStatus = "x", delivered = PumpInsulin(1.0), stopPressed = false, stopDeliveryEnabled = true
    )

    @Test
    fun bolusPrepareAcksOkWithSignedPreviewPayload() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(wizardBolusExecutor.prepareQuickWizard(any())).thenReturn(
            WizardBolusExecutor.PrepareResult.Preview(
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
        whenever(wizardBolusExecutor.prepareQuickWizard(any())).thenReturn(WizardBolusExecutor.PrepareResult.Error("no bg"))
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
        whenever(wizardBolusExecutor.confirm(any(), any(), any(), any(), any())).thenReturn(WizardBolusExecutor.ConfirmResult.Delivered)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L, asAdvisor = false), counter = 5L, wantsAck = true)))

        verify(wizardBolusExecutor).confirm(eq(42L), eq(Sources.NSClient), any(), eq(false), any())
        assertThat(acks.last().status.name).isEqualTo("Ok")
    }

    @Test
    fun bolusCommitNoPendingAcksNoPendingBolus() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(wizardBolusExecutor.confirm(any(), any(), any(), any(), any())).thenReturn(WizardBolusExecutor.ConfirmResult.NoPending)
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
        whenever(preferences.get(BooleanKey.NsClientAllowClientControl)).thenReturn(false)
        val acks = captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L), counter = 5L, wantsAck = true)))

        val done = acks.last()
        assertThat(done.status.name).isEqualTo("Failed")
        assertThat(done.reason).isEqualTo(FailureReason.ControlDisabled.name)
        verify(wizardBolusExecutor, never()).confirm(any(), any(), any(), any(), any())
    }

    @Test
    fun bolusCommitAsAdvisorDeliversAdvisorVariant() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(wizardBolusExecutor.confirm(any(), any(), any(), any(), any())).thenReturn(WizardBolusExecutor.ConfirmResult.Delivered)
        captureAcks(clientId)
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}bolus_commit_$clientId"

        sut.onSettingsDocChanged(identifier, wrap(envelope(clientId, secret, message = ClientControlMessage.BolusCommit(42L, asAdvisor = true), counter = 5L, wantsAck = true)))

        verify(wizardBolusExecutor).confirm(eq(42L), eq(Sources.NSClient), any(), eq(true), any())
    }

    @Test
    fun wizardPrepareAcksOkWithSignedPreviewPayload() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        whenever(wizardBolusExecutor.prepareWizard(any())).thenReturn(
            WizardBolusExecutor.PrepareResult.Preview(
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
        whenever(wizardBolusExecutor.prepareBatch(any())).thenReturn(
            WizardBolusExecutor.PrepareResult.Preview(
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
    fun pollingListsSettingsAndDispatchesEachClientControlDoc() = runTest {
        val (clientId, secret) = pair()
        authorizedRepository.markActive(clientId, counterReceived = 1L, now = now - 5_000L)
        val cmdIdentifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_stop_$clientId"
        val cmdDoc = wrap(envelope(clientId, secret, message = ClientControlMessage.SceneStop(false), counter = 5L)).also {
            it.put("identifier", cmdIdentifier)
        }
        // Mix in a non-clientcontrol doc that polling should ignore.
        val unrelatedDoc = JSONObject().apply {
            put("identifier", "aaps")
            put("date", now)
        }
        whenever(nsAndroidClient.searchSettings(limit = 100)).thenReturn(
            NSAndroidClient.ReadResponse(code = 200, lastServerModified = null, values = listOf(cmdDoc, unrelatedDoc))
        )
        whenever(sceneAutomationApi.stopActiveScene()).thenReturn(SceneAutomationResult.Success)

        sut.processPending()

        verify(nsAndroidClient).searchSettings(limit = 100)
        verify(sceneAutomationApi).stopActiveScene()
        // Success path no longer deletes — counter dedup handles replay; leaving the doc avoids
        // tombstoning the slot. Unrelated doc is also untouched.
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    @Test
    fun pollingSkipsOfferDocsWithoutDeleting() = runTest {
        // Master writes its own pairing-offer docs under IDENTIFIER_OFFER_PREFIX. Without an
        // explicit skip, verifyAndAck would treat them as envelopes, find no `envelope` field,
        // and delete a still-live offer mid-pairing — leaving the client unable to fetch it.
        val offerDoc = JSONObject().apply {
            put("identifier", "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}any-client-id")
            put("date", now)
            put("offer", JSONObject().apply { put("clientId", "any-client-id") })
        }
        whenever(nsAndroidClient.searchSettings(limit = 100)).thenReturn(
            NSAndroidClient.ReadResponse(code = 200, lastServerModified = null, values = listOf(offerDoc))
        )

        sut.processPending()

        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    @Test
    fun wsPushSkipsOfferDocs() = runTest {
        val identifier = "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}any-client-id"
        val doc = JSONObject().apply { put("identifier", identifier); put("date", now) }
        sut.onSettingsDocChanged(identifier, doc)
        verify(nsAndroidClient, never()).deleteSettings(any())
    }

    @Test
    fun pollingLeavesUnknownClientDocUntouchedAndDoesNotExecute() = runTest {
        // A known client is paired, but the doc below carries a DIFFERENT clientId the master never
        // paired. On every poll the master re-lists it and must leave it completely alone — never
        // delete (soft OR permanent: a tombstone would 410 the rightful owner's next command) and
        // never execute it. This locks in the fix against re-delivery via the poll fallback.
        pair()
        val identifier = "${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}scene_stop_stranger-uuid"
        val doc = wrap(envelope("stranger-uuid", ByteArray(32) { 0x11 }, message = ClientControlMessage.SceneStop(false), counter = 5L)).also {
            it.put("identifier", identifier)
        }
        whenever(nsAndroidClient.searchSettings(limit = 100)).thenReturn(
            NSAndroidClient.ReadResponse(code = 200, lastServerModified = null, values = listOf(doc))
        )

        sut.processPending()

        verify(nsAndroidClient, never()).deleteSettings(any())
        verify(nsAndroidClient, never()).deleteSettingsPermanent(any())
        verify(sceneAutomationApi, never()).stopActiveScene()
    }

    // -- two-step ACK -----------------------------------------------------------------------

    /** Captures the ACK envelopes the master writes to this client's ack identifier. */
    private suspend fun captureAcks(clientId: String): MutableList<AckEnvelope> {
        val acks = mutableListOf<AckEnvelope>()
        whenever(nsAndroidClient.updateSettings(eq(ClientControlPublisher.IDENTIFIER_ACK_PREFIX + clientId), any())).thenAnswer {
            val doc = it.getArgument<JSONObject>(1)
            acks += Json.decodeFromString<AckEnvelope>(doc.getJSONObject("ack").toString())
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
}
