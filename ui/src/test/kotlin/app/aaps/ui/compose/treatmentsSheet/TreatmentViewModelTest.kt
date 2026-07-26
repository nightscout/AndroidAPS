package app.aaps.ui.compose.treatmentsSheet

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.ui.compose.navigation.ElementAvailability
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class TreatmentViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var dexcomBoyda: DexcomBoyda
    @Mock private lateinit var elementAvailability: ElementAvailability

    private lateinit var sut: TreatmentViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{}-launched refreshState() coroutine (no advanceUntilIdle),
        // so construction stays clean and we assert the default uiState. The cold flow chains built in
        // setupEventListeners() (merge of preferences.observe(...).drop(1) + quickWizard.changes +
        // rxBus.toFlow(...)) are evaluated synchronously, so each source flow must be non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(preferences.observe(BooleanKey.OverviewShowCgmButton)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.OverviewShowCalibrationButton)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.OverviewShowTreatmentButton)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.OverviewShowInsulinButton)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.OverviewShowCarbsButton)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.OverviewShowWizardButton)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.GeneralSimpleMode)).thenReturn(MutableStateFlow(false))
        whenever(quickWizard.changes).thenReturn(MutableStateFlow(0))
        whenever(rxBus.toFlow(EventRefreshOverview::class.java)).thenReturn(emptyFlow())
        sut = TreatmentViewModel(
            rh, preferences, activePlugin, profileFunction, loop, iobCobCalculator,
            constraintChecker, quickWizard, rxBus, aapsLogger, dexcomBoyda, elementAvailability
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState exposes the data-class defaults before refreshState runs`() {
        val state = sut.uiState.value
        assertThat(state.showTreatment).isTrue()
        assertThat(state.showInsulin).isTrue()
        assertThat(state.showCarbs).isTrue()
        assertThat(state.showCalculator).isTrue()
        assertThat(state.showCgm).isFalse()
        assertThat(state.showCalibration).isFalse()
        assertThat(state.isDexcomSource).isFalse()
        assertThat(state.showSettingsIcon).isFalse()
        assertThat(state.quickWizardItems).isEmpty()
    }
}
