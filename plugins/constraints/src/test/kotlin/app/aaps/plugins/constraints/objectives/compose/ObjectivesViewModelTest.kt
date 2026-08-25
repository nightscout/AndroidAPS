package app.aaps.plugins.constraints.objectives.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ObjectivesViewModelTest {

    private val objectivesPlugin: ObjectivesPlugin = mock()
    private val sntpClient: SntpClient = mock()
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: ObjectivesViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // scope.launch { updateState() } + the update timer are deferred by StandardTestDispatcher, so the
        // objectivesPlugin.objectives list is never enumerated at construction; only the init flow chains are built.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(rxBus.toFlow(EventObjectivesUpdateGui::class.java)).thenReturn(emptyFlow())
        // The 8 completion-tracking prefs are observed (.drop(1).merge()) in init — each must be non-null.
        listOf(
            BooleanNonKey.ObjectivesBgIsAvailableInNs, BooleanNonKey.ObjectivesPumpStatusIsAvailableInNS,
            BooleanNonKey.ObjectivesProfileSwitchUsed, BooleanNonKey.ObjectivesDisconnectUsed,
            BooleanNonKey.ObjectivesReconnectUsed, BooleanNonKey.ObjectivesTempTargetUsed,
            BooleanNonKey.ObjectivesLoopUsed, BooleanNonKey.ObjectivesScaleUsed
        ).forEach { whenever(preferences.observe(it)).thenReturn(MutableStateFlow(false)) }
        sut = ObjectivesViewModel(
            objectivesPlugin, rxBus, rh, dateUtil, sntpClient, receiverStatusStore, aapsLogger, uel, preferences
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is empty and not in fake mode`() {
        val state = sut.uiState.value
        assertThat(state.objectives).isEmpty()
        assertThat(state.isFakeMode).isFalse()
        assertThat(state.confirmUnstartDialog).isNull()
        assertThat(sut.scrollToIndex.value).isEqualTo(-1)
    }

    @Test
    fun `onFakeModeToggle flips fake mode`() {
        sut.onFakeModeToggle(true)
        assertThat(sut.uiState.value.isFakeMode).isTrue()

        sut.onFakeModeToggle(false)
        assertThat(sut.uiState.value.isFakeMode).isFalse()
    }

    @Test
    fun `unstart confirmation dialog is requested then dismissed`() {
        sut.onRequestUnstart(2)
        assertThat(sut.uiState.value.confirmUnstartDialog).isEqualTo(2)

        sut.onDismissUnstartDialog()
        assertThat(sut.uiState.value.confirmUnstartDialog).isNull()
    }

    @Test
    fun `onSnackbarShown clears the snackbar message`() {
        sut.onSnackbarShown()
        assertThat(sut.snackbarMessage.value).isNull()
    }
}
