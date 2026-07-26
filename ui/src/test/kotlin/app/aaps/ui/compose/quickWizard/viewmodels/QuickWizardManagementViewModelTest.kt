package app.aaps.ui.compose.quickWizard.viewmodels

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.ui.events.EventQuickWizardChange
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class QuickWizardManagementViewModelTest {

    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var aapsSchedulers: AapsSchedulers
    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: QuickWizardManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} loadData() launch and the changes.launchIn collection,
        // so we test the synchronous update methods against the default uiState.
        Dispatchers.setMain(StandardTestDispatcher())
        // Synchronous init wiring that must return non-null flows/streams to avoid construction NPEs.
        whenever(quickWizard.changes).thenReturn(MutableStateFlow(0))
        whenever(rxBus.toObservable(EventQuickWizardChange::class.java)).thenReturn(Observable.empty())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        sut = QuickWizardManagementViewModel(
            quickWizard, rxBus, aapsSchedulers, constraintChecker, preferences, rh, dateUtil, aapsLogger
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState exposes expected editor defaults`() {
        val state = sut.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.editorMode).isEqualTo(QuickWizardMode.WIZARD)
        assertThat(state.editorUseBG).isTrue()
        assertThat(state.editorUseIOB).isTrue()
        assertThat(state.editorUseCOB).isFalse()
        assertThat(state.editorPercentage).isEqualTo(100)
        assertThat(state.screenMode).isEqualTo(ScreenMode.EDIT)
        assertThat(state.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `editor setters update the state and mark unsaved changes`() {
        sut.updateButtonText("Breakfast")
        sut.updateInsulin(1.5)
        sut.updateCarbs(40)
        sut.updatePercentage(80)
        sut.updateDuration(3)
        sut.updateCurrentCardIndex(2)

        val state = sut.uiState.value
        assertThat(state.editorButtonText).isEqualTo("Breakfast")
        assertThat(state.editorInsulin).isEqualTo(1.5)
        assertThat(state.editorCarbs).isEqualTo(40)
        assertThat(state.editorPercentage).isEqualTo(80)
        assertThat(state.editorDuration).isEqualTo(3)
        assertThat(state.currentCardIndex).isEqualTo(2)
        assertThat(state.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `updateCarbTime auto-enables alarm when positive and disables when zero`() {
        sut.updateCarbTime(15)
        assertThat(sut.uiState.value.editorCarbTime).isEqualTo(15)
        assertThat(sut.uiState.value.editorUseAlarm).isTrue()

        sut.updateCarbTime(0)
        assertThat(sut.uiState.value.editorCarbTime).isEqualTo(0)
        assertThat(sut.uiState.value.editorUseAlarm).isFalse()
    }

    @Test
    fun `updateUseCOB true auto-enables IOB`() {
        sut.updateUseCOB(true)

        val state = sut.uiState.value
        assertThat(state.editorUseCOB).isTrue()
        assertThat(state.editorUseIOB).isTrue()
    }

    @Test
    fun `updateUseIOB false clears COB and positive-IOB-only`() {
        sut.updateUseCOB(true)              // turns COB (and IOB) on
        sut.updateUsePositiveIOBOnly(true)

        sut.updateUseIOB(false)

        val state = sut.uiState.value
        assertThat(state.editorUseIOB).isFalse()
        assertThat(state.editorUseCOB).isFalse()
        assertThat(state.editorUsePositiveIOBOnly).isFalse()
    }

    @Test
    fun `updateMode updates the editor mode`() {
        sut.updateMode(QuickWizardMode.INSULIN)

        assertThat(sut.uiState.value.editorMode).isEqualTo(QuickWizardMode.INSULIN)
        assertThat(sut.uiState.value.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `setScreenMode switches the screen mode`() {
        sut.setScreenMode(ScreenMode.PLAY)

        assertThat(sut.uiState.value.screenMode).isEqualTo(ScreenMode.PLAY)
    }
}
