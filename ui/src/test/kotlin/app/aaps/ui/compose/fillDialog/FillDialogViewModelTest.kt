package app.aaps.ui.compose.fillDialog

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class FillDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var translator: Translator
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor

    private val pump: PumpWithConcentration = mock()
    private val pumpDescription: PumpDescription = mock()
    private val maxBolusConstraint: Constraint<Double> = mock()

    private lateinit var sut: FillDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpDescription.bolusStep).thenReturn(0.1)
        whenever(maxBolusConstraint.value()).thenReturn(10.0)
        whenever(constraintChecker.getMaxBolusAllowed()).thenReturn(maxBolusConstraint)
        whenever(insulinManager.insulins).thenReturn(arrayListOf<ICfg>())
        // observeConcentrationEnabled() builds a flow from preferences.observe(key) in init (collection deferred).
        whenever(preferences.observe(BooleanKey.GeneralInsulinConcentration)).thenReturn(MutableStateFlow(false))
        sut = FillDialogViewModel(
            SavedStateHandle(), constraintChecker, activePlugin, persistenceLayer, preferences, config,
            decimalFormatter, rh, dateUtil, translator, aapsLogger, ch, insulinManager, profileFunction,
            wizardBolusExecutor, batchExecutor, CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `updateSiteChange and updateCartridgeChange toggle their flags`() {
        sut.updateSiteChange(true)
        sut.updateCartridgeChange(true)

        assertThat(sut.uiState.value.siteChange).isTrue()
        assertThat(sut.uiState.value.insulinCartridgeChange).isTrue()
    }

    @Test
    fun `updateNotes sets the notes`() {
        sut.updateNotes("prime 0.3")
        assertThat(sut.uiState.value.notes).isEqualTo("prime 0.3")
    }

    @Test
    fun `updateEventTime records the time and marks it changed`() {
        sut.updateEventTime(123_456L)

        assertThat(sut.uiState.value.eventTime).isEqualTo(123_456L)
        assertThat(sut.uiState.value.eventTimeChanged).isTrue()
    }
}
