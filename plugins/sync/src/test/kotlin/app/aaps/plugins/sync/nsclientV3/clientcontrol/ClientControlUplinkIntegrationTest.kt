package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

/**
 * End-to-end **uplink** (client → master) integration test for the generic bidirectional sync channel
 * — the path Automation/QuickWizard/TempTargetPresets now ride. Wires the *real* client publisher
 * chain to the *real* master receiver across an in-memory stand-in for the NS `settings` collection:
 *
 *   client [Preferences edit] → PreferencesClientPublisher → ClientControlPublisher (signs envelope)
 *      → nsAndroidClient.updateSettings  ──[NS bridge]──►  ClientControlReceiver.onSettingsDocChanged
 *      → verify signature → per-key last-writer-wins → Preferences.putRemote (master adopts)
 *
 * Client and master have **separate** `Preferences` stores (two devices); they share only the pairing
 * secret, established by registering the same client in the master's AuthorizedClientsRepository and
 * the client's ClientPairingRepository. The crypto (HMAC sign/verify) is real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientControlUplinkIntegrationTest {

    private val now = 1_700_000_000_000L
    private val clientVersion = 2_000L // client's SyncedPrefModified for AutomationEvents (its edit stamp)
    private val automationJson = """[{"id":"a","title":"client-edit","enabled":true}]"""

    // Deterministic symmetric SecureEncrypt — same fake the repository/receiver tests use.
    private val secureEncrypt = object : SecureEncrypt {
        override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = "ENC:$keystoreAlias:${plaintextSecret.reversed()}"
        override fun decrypt(encryptedSecret: String): String = encryptedSecret.removePrefix("ENC:NsClientControlSecret:").reversed()
        override fun isValidDataString(data: String?): Boolean = data != null && data.startsWith("ENC:")
        override fun deleteKey(keystoreAlias: String) {}
    }

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var nsClientRepository: NSClientRepository
    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var uel: UserEntryLogger

    // -- client side --
    @Mock private lateinit var clientPrefs: Preferences
    @Mock private lateinit var clientConfig: Config
    private val clientStrings = mutableMapOf<String, String>()
    private val clientLongs = mutableMapOf<String, Long>()
    private val syncedLocalChanges = MutableSharedFlow<NonPreferenceKey>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private lateinit var clientPairingRepository: ClientPairingRepository
    private lateinit var clientControlPublisher: ClientControlPublisher
    private lateinit var preferencesClientPublisher: PreferencesClientPublisher

    // -- master side --
    @Mock private lateinit var masterPrefs: Preferences
    @Mock private lateinit var sceneAutomationApi: SceneAutomationApi
    @Mock private lateinit var activeSceneSync: ActiveSceneSync
    @Mock private lateinit var offerPublisher: PairingOfferPublisher
    @Mock private lateinit var runningConfigurationPublisher: RunningConfigurationPublisher
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var masterConfig: Config
    @Mock private lateinit var bolusProgressData: BolusProgressData
    @Mock private lateinit var commandQueue: CommandQueue
    private var masterAuthorizedClients = "[]"
    private var masterModified = 0L
    private var masterAppliedValue: String? = null
    private var masterAppliedVersion: Long? = null
    private lateinit var masterAuthorizedRepository: AuthorizedClientsRepository
    private lateinit var masterReceiver: ClientControlReceiver

    // Scopes injected into the round-trip (client watchdog) and receiver (master progress-mirror + heartbeat).
    // Both host long-lived coroutines; cancelled in tearDown() so they can't fire after the test and touch a
    // cleared mock (a flaky "uncaught exception before the next test").
    private lateinit var clientScope: CoroutineScope
    private lateinit var masterScope: CoroutineScope

    @AfterEach
    fun tearDown() {
        if (::clientScope.isInitialized) clientScope.cancel()
        if (::masterScope.isInitialized) masterScope.cancel()
    }

    // -- NS bridge (the settings collection) --
    private var bridgeIdentifier: String? = null
    private var bridgeDoc: JSONObject? = null

    @BeforeEach
    fun setUp() = runBlocking {
        MockitoAnnotations.openMocks(this@ClientControlUplinkIntegrationTest)
        whenever(dateUtil.now()).thenReturn(now)

        // ---------- client Preferences fake ----------
        whenever(clientConfig.AAPSCLIENT).thenReturn(true)
        whenever(rh.gs(any<Int>())).thenReturn("update settings")
        whenever(clientPrefs.syncedLocalChanges).thenReturn(syncedLocalChanges)
        clientStrings[StringNonKey.AutomationEvents.key] = automationJson
        whenever(clientPrefs.get(any<StringNonPreferenceKey>())).thenAnswer { clientStrings[it.getArgument<StringNonPreferenceKey>(0).key] ?: "" }
        doAnswer { clientStrings[it.getArgument<StringNonPreferenceKey>(0).key] = it.getArgument(1); null }
            .whenever(clientPrefs).put(any<StringNonPreferenceKey>(), any<String>())
        whenever(clientPrefs.get(any<LongNonPreferenceKey>())).thenAnswer { clientLongs[it.getArgument<LongNonPreferenceKey>(0).key] ?: 0L }
        doAnswer { clientLongs[it.getArgument<LongNonPreferenceKey>(0).key] = it.getArgument(1); null }
            .whenever(clientPrefs).put(any<LongNonPreferenceKey>(), any<Long>())
        whenever(clientPrefs.get(LongComposedKey.SyncedPrefModified, StringNonKey.AutomationEvents.key)).thenReturn(clientVersion)

        // ---------- master Preferences fake ----------
        // Client control ON — the receiver now gates ALL client→master traffic (commands AND the preferences-update
        // settings sync) on this, matching the old WS-path drop. Off → the uplink is rejected (see ClientControlReceiverTest).
        whenever(masterPrefs.get(BooleanKey.NsClientAllowClientControl)).thenReturn(true)
        whenever(masterPrefs.get(StringNonKey.NsClientControlAuthorizedClients)).thenAnswer { masterAuthorizedClients }
        doAnswer { masterAuthorizedClients = it.getArgument(1); null }
            .whenever(masterPrefs).put(eq(StringNonKey.NsClientControlAuthorizedClients), any<String>())
        // String → key resolver used by the receiver to look up the pushed key.
        whenever(masterPrefs.get(StringNonKey.AutomationEvents.key)).thenReturn(StringNonKey.AutomationEvents)
        whenever(masterPrefs.get(LongComposedKey.SyncedPrefModified, StringNonKey.AutomationEvents.key)).thenAnswer { masterModified }
        doAnswer { masterAppliedValue = it.getArgument(1); masterAppliedVersion = it.getArgument(2); masterModified = it.getArgument(2); null }
            .whenever(masterPrefs).putRemote(eq(StringNonKey.AutomationEvents), any<String>(), any<Long>())

        // ---------- NS bridge: capture what the client publishes ----------
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        // Pref edits now ride the confirmed round-trip; dispatch reads masterReachable. No ack is
        // bridged back here, so the round-trip just times out to Unconfirmed — the command doc is still
        // captured + delivered to the master, which is what this uplink test asserts.
        whenever(nsClientV3Plugin.masterReachable).thenReturn(MutableStateFlow(true))
        whenever(nsAndroidClient.updateSettings(any<String>(), any<JSONObject>())).thenAnswer {
            val id = it.getArgument<String>(0)
            // The master now writes two-step ACK docs through this same NS bridge; ignore them here so
            // the capture reflects only the client's command publish (the uplink under test).
            if (!id.startsWith(ClientControlPublisher.IDENTIFIER_ACK_PREFIX)) {
                bridgeIdentifier = id
                bridgeDoc = it.getArgument(1)
            }
            CreateUpdateResponse(response = 200, identifier = null, isDeduplication = false, deduplicatedIdentifier = null, lastModified = null, errorResponse = null)
        }

        // ---------- real components ----------
        clientPairingRepository = ClientPairingRepository(clientPrefs, secureEncrypt, aapsLogger)
        clientControlPublisher = ClientControlPublisher(clientPairingRepository, Provider { nsClientV3Plugin }, nsClientRepository, dateUtil, aapsLogger)
        whenever(notificationManager.notifications).thenReturn(MutableStateFlow(emptyList<AapsNotification>()))
        whenever(bolusProgressData.state).thenReturn(MutableStateFlow<BolusProgressState?>(null))
        clientScope = CoroutineScope(Dispatchers.Unconfined)
        val clientControlRoundTrip =
            ClientControlRoundTrip(
                clientControlPublisher,
                clientPairingRepository,
                Provider { nsClientV3Plugin },
                nsClientRepository,
                clientConfig,
                dateUtil,
                notificationManager,
                rh,
                bolusProgressData,
                aapsLogger,
                clientScope
            )
        preferencesClientPublisher = PreferencesClientPublisher(clientPrefs, clientControlRoundTrip, clientConfig, rh, aapsLogger)

        masterAuthorizedRepository = AuthorizedClientsRepository(masterPrefs, secureEncrypt, aapsLogger)
        masterScope = CoroutineScope(Dispatchers.Unconfined)
        masterReceiver = ClientControlReceiver(
            masterAuthorizedRepository, Provider { nsClientV3Plugin }, nsClientRepository, sceneAutomationApi,
            offerPublisher, masterPrefs, dateUtil, uel, runningConfigurationPublisher, persistenceLayer, wizardBolusExecutor, notificationManager, masterConfig, bolusProgressData, commandQueue, aapsLogger,
            masterScope
        )

        // ---------- pairing: same secret on both sides ----------
        val (entry, secretHex) = masterAuthorizedRepository.addPending("phone", pairTtlMs = 60_000L, now = now - 10_000L)
        masterAuthorizedRepository.markActive(entry.clientId, counterReceived = 0L, now = now - 5_000L)
        clientPairingRepository.pair(
            PairingPayload(masterInstallId = "master-1", clientId = entry.clientId, secretHex = secretHex, expiresAt = now + 60_000L),
            now = now - 10_000L
        )
    }

    /** Drives the client publisher: emit a local edit, run the 2 s debounce, then deliver the captured doc to the master. */
    private suspend fun TestScope.runUplink(masterModifiedStart: Long): CoroutineScope {
        masterModified = masterModifiedStart
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        preferencesClientPublisher.start(scope)
        advanceUntilIdle() // collector subscribes to syncedLocalChanges
        syncedLocalChanges.tryEmit(StringNonKey.AutomationEvents)
        advanceUntilIdle() // 2 s debounce elapses → publisher signs + uploads to the bridge
        // Bridge → master.
        masterReceiver.onSettingsDocChanged(bridgeIdentifier!!, bridgeDoc!!)
        return scope
    }

    @Test
    fun clientEditPropagatesToMasterAcrossBridge() = runTest {
        val scope = runUplink(masterModifiedStart = 1_000L) // master older → adopt

        // The client published a signed envelope to the cmd slot…
        assertThat(bridgeIdentifier).isEqualTo("${ClientControlPublisher.IDENTIFIER_CMD_PREFIX}preferences_update_${clientId()}")
        assertThat(bridgeDoc).isNotNull()
        // …and the master verified it and adopted the value + version via putRemote (no echo).
        assertThat(masterAppliedValue).isEqualTo(automationJson)
        assertThat(masterAppliedVersion).isEqualTo(clientVersion)
        verify(masterPrefs).putRemote(eq(StringNonKey.AutomationEvents), eq(automationJson), eq(clientVersion))

        scope.cancel()
    }

    @Test
    fun staleClientEditIsDroppedByMasterLww() = runTest {
        val scope = runUplink(masterModifiedStart = 5_000L) // master newer than the client's 2_000 → drop

        // Envelope was still produced + verified, but LWW drops the stale value: no putRemote.
        assertThat(bridgeDoc).isNotNull()
        assertThat(masterAppliedValue).isNull()
        verify(masterPrefs, never()).putRemote(eq(StringNonKey.AutomationEvents), any<String>(), any<Long>())

        scope.cancel()
    }

    private fun clientId(): String = clientStrings[StringNonKey.NsClientControlClientId.key] ?: error("not paired")
}
