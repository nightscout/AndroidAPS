package app.aaps.integration

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import app.aaps.plugins.automation.AutomationEventObject
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.automation.TimerUtil
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.configuration.configBuilder.RunningConfigurationImpl
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * End-to-end **downlink** (master → client) integration test for the generic bidirectional sync
 * channel — the path Automation now rides after dropping its dedicated channel.
 *
 *   MASTER AutomationRuntime edit → storeToSP  ──[cold running-config doc: bridge]──►
 *      CLIENT RunningConfigurationImpl.applyCold → applySyncedPrefs → Preferences.putRemote
 *      → CLIENT AutomationRuntime self-observe → loadFromSP  (list updated, no echo)
 *
 * Both AutomationRuntimes are real and use separate `Preferences` stores (two devices); the "bridge"
 * is the cold doc the master's running-config publisher would emit. Lives in `:app` because it's the
 * only module where AutomationRuntime and RunningConfigurationImpl are both visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomationDownlinkIntegrationTest : TestBaseWithProfile() {

    // RunningConfigurationImpl deps (client side) not provided by the base.
    // insulin / notificationManager / constraintsChecker come from TestBaseWithProfile.
    @Mock private lateinit var activeSceneSync: ActiveSceneSync
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var nsClientRepository: NSClientRepository

    // AutomationRuntime deps not provided by the base.
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var locationServiceHelper: LocationServiceHelper
    @Mock private lateinit var timerUtil: TimerUtil
    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var sceneApi: SceneAutomationApi

    // -- master device --
    @Mock private lateinit var masterPreferences: Preferences
    @Mock private lateinit var masterConfig: Config
    private val masterAutoFlow = MutableStateFlow("[]")
    private lateinit var masterRuntime: AutomationRuntime

    // -- client device --  (uses the base's `preferences` + `config` mocks)
    private val clientAutoFlow = MutableStateFlow("[]")
    private var clientLocalPutCount = 0
    private lateinit var clientRuntime: AutomationRuntime
    private lateinit var runningConfig: RunningConfigurationImpl

    init {
        addInjector {
            if (it is Trigger) {
                it.rh = rh
                it.aapsLogger = aapsLogger
            }
        }
    }

    @BeforeEach fun prepare() {
        // ----- master Preferences fake (produces the config; no self-observe → AAPSCLIENT=false) -----
        whenever(masterConfig.APS).thenReturn(false)
        whenever(masterConfig.AAPSCLIENT).thenReturn(false)
        whenever(masterPreferences.observe(StringNonKey.AutomationEvents)).thenReturn(masterAutoFlow)
        whenever(masterPreferences.get(StringNonKey.AutomationEvents)).thenAnswer { masterAutoFlow.value }
        doAnswer { masterAutoFlow.value = it.getArgument(1); null }
            .whenever(masterPreferences).put(eq(StringNonKey.AutomationEvents), any<String>())

        // ----- client Preferences fake: putRemote (cold apply) feeds observe; put = local edit -----
        whenever(config.APS).thenReturn(false)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(preferences.observe(StringNonKey.AutomationEvents)).thenReturn(clientAutoFlow)
        whenever(preferences.get(StringNonKey.AutomationEvents)).thenAnswer { clientAutoFlow.value }
        whenever(preferences.get(StringNonKey.AutomationEvents.key)).thenReturn(StringNonKey.AutomationEvents) // String → key resolver for applySyncedPrefs
        doAnswer { clientAutoFlow.value = it.getArgument(1); clientLocalPutCount++; null }
            .whenever(preferences).put(eq(StringNonKey.AutomationEvents), any<String>())
        doAnswer { clientAutoFlow.value = it.getArgument(1); null } // master-wins apply, no echo emit
            .whenever(preferences).putRemote(eq(StringNonKey.AutomationEvents), any<String>(), any<Long>())

        masterRuntime = AutomationRuntime(
            injector, aapsLogger, rh, masterPreferences, context, fabricPrivacy, loop, rxBus, constraintsChecker,
            aapsSchedulers, masterConfig, locationServiceHelper, dateUtil, activePlugin, timerUtil, receiverStatusStore,
            uel, profileRepository, sceneApi
        )
        clientRuntime = AutomationRuntime(
            injector, aapsLogger, rh, preferences, context, fabricPrivacy, loop, rxBus, constraintsChecker,
            aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin, timerUtil, receiverStatusStore,
            uel, profileRepository, sceneApi
        )
        runningConfig = RunningConfigurationImpl(
            activePlugin, activeSceneSync, preferences, aapsLogger, config,
            pumpSync, notificationManager, nsClientRepository, constraintsChecker
        )
    }

    private fun TestScope.scope() = CoroutineScope(StandardTestDispatcher(testScheduler))

    @Test
    fun masterEditReachesClientRuntimeThroughTheColdDoc() = runTest {
        // MASTER: edit an automation; capture the JSON it persists (what the running-config publisher ships).
        val masterScope = scope()
        masterRuntime.start(masterScope)
        advanceUntilIdle()
        masterRuntime.add(AutomationEventObject(injector).apply { title = "from-master"; trigger = TriggerConnector(injector) })
        advanceUntilIdle()
        val coldJson = masterAutoFlow.value
        assertThat(coldJson).contains("from-master")

        // CLIENT: start the runtime (self-observe active), then apply the master's cold doc.
        val clientScope = scope()
        clientRuntime.start(clientScope)
        advanceUntilIdle()
        clientLocalPutCount = 0

        // The cold doc is built directly here rather than via the master's RunningConfigurationImpl.toJson()
        // (which needs the full plugin graph); the master-side String-key serialization is covered by
        // RunningConfigurationImplTest, and the apply routing by applyColdSyncedPrefsAdoptsStringColdKeyViaPutRemote.
        runningConfig.applyCold(NSRunningConfiguration(syncedPrefs = mapOf(StringNonKey.AutomationEvents.key to coldJson)))
        advanceUntilIdle()

        // The cold-doc apply routed AutomationEvents → putRemote → client self-observe → loadFromSP.
        assertThat(clientRuntime.events.value.map { it.title }).contains("from-master")
        // Band-aid: the client re-serializes to the same canonical JSON → no echo write back to the master.
        assertThat(clientLocalPutCount).isEqualTo(0)

        masterScope.cancel()
        clientScope.cancel()
    }
}
