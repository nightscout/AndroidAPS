package app.aaps.ui.compose.manageSheet

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * A pump plugin is BOTH a [Pump] and a [PluginBase] in production. The VM casts
 * `activePlugin.activePumpInternal as PluginBase` at field init, so the returned mock must satisfy
 * both types. This test-local abstract combines them; Mockito/Objenesis bypasses the super-constructor,
 * so the `mock()` arguments are never evaluated.
 */
internal abstract class FakePumpPlugin : PluginBase(mock<PluginDescription>(), mock<AAPSLogger>(), mock<ResourceHelper>()), Pump

@OptIn(ExperimentalCoroutinesApi::class)
internal class ManageViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var config: Config
    @Mock private lateinit var processedTbrEbData: ProcessedTbrEbData
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var nsSettingStatus: NSSettingsStatus
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var nsClient: NsClient
    @Mock private lateinit var visibilityContext: VisibilityContext

    private lateinit var pumpPlugin: FakePumpPlugin
    private lateinit var sut: ManageViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher DEFERS the init{}-launched refreshState() coroutine (no advanceUntilIdle),
        // so the uiState stays at ManageUiState defaults and we test the synchronous methods.
        Dispatchers.setMain(StandardTestDispatcher())
        // setupEventListeners() builds cold flow chains synchronously in init — every source must be non-null.
        whenever(persistenceLayer.observeChanges(EB::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TB::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventInitializationChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventCustomActionsChanged::class.java)).thenReturn(emptyFlow())
        whenever(nsClient.masterOrPairedClientFlow).thenReturn(MutableStateFlow(false))
        // Field init casts activePumpInternal (a Pump) to PluginBase — the fake satisfies both.
        pumpPlugin = mock()
        whenever(activePlugin.activePumpInternal).thenReturn(pumpPlugin)
        sut = ManageViewModel(
            rh, activePlugin, profileFunction, loop, config, processedTbrEbData, persistenceLayer, uel,
            rxBus, dateUtil, nsSettingStatus, preferences, batchExecutor, nsClient, visibilityContext,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState exposes ManageUiState defaults and the cast pump plugin`() {
        val state = sut.uiState.value
        // refreshState() is deferred, so these are the untouched constructor defaults.
        assertThat(state.showMutatingActions).isTrue()
        assertThat(state.showPump).isTrue()
        assertThat(state.showTempTarget).isFalse()
        assertThat(state.showTempBasal).isFalse()
        assertThat(state.customActions).isEmpty()
        // The field-init cast succeeded and stored our fake.
        assertThat(state.pumpPlugin).isSameInstanceAs(pumpPlugin)
    }
}
