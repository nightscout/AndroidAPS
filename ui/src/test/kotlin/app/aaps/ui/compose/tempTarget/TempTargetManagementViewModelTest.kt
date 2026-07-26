package app.aaps.ui.compose.tempTarget

import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class TempTargetManagementViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var config: Config

    private lateinit var sut: TempTargetManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        // init observers must be built (their launchIn is deferred); loadData() launch is deferred too.
        whenever(persistenceLayer.observeChanges(TT::class.java)).thenReturn(emptyFlow())
        whenever(preferences.observe(StringNonKey.TempTargetPresets)).thenReturn(MutableStateFlow("[]"))
        sut = TempTargetManagementViewModel(
            persistenceLayer, profileFunction, profileUtil, preferences, rh, dateUtil, aapsLogger,
            rxBus, batchExecutor, config, CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `editor setters update the state`() {
        sut.updateEditorName("Morning")
        sut.updateEditorTarget(5.5)
        sut.updateEditorDuration(3_600_000L)
        sut.updateNotes("note")
        sut.updateCurrentCardIndex(2)

        val state = sut.uiState.value
        assertThat(state.editorName).isEqualTo("Morning")
        assertThat(state.editorTarget).isEqualTo(5.5)
        assertThat(state.editorDuration).isEqualTo(3_600_000L)
        assertThat(state.notes).isEqualTo("note")
        assertThat(state.currentCardIndex).isEqualTo(2)
    }

    @Test
    fun `updateEventTime records the time and marks it changed`() {
        sut.updateEventTime(123_456L)

        assertThat(sut.uiState.value.eventTime).isEqualTo(123_456L)
        assertThat(sut.uiState.value.eventTimeChanged).isTrue()
    }

    @Test
    fun `hasUnsavedChanges is false with no selected preset`() {
        assertThat(sut.hasUnsavedChanges()).isFalse()
        assertThat(sut.isEditorDifferentFromDefaults()).isFalse()
    }
}
