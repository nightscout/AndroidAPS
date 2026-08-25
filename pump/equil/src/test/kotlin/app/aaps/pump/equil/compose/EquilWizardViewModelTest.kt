package app.aaps.pump.equil.compose

import android.content.Context
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.manager.EquilManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

/**
 * Unit test for the SYNCHRONOUS state logic of [EquilWizardViewModel].
 *
 * The VM has no `init {}` block; every asynchronous action runs through `viewModelScope.launch`,
 * so a StandardTestDispatcher on Main defers all of them (no `advanceUntilIdle` is called). That
 * leaves the default state and the pure setter/navigation methods to assert deterministically.
 * `initializeWorkflow(...)` and the BLE/command flows are intentionally NOT exercised (they need a
 * real dispatcher to drain and stubbed pump-command round-trips).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EquilWizardViewModelTest {

    @Mock private lateinit var context: Context
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var constraintsChecker: ConstraintsChecker
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var hardLimits: HardLimits

    // Concrete final / abstract classes -> mockito-kotlin mock() (Mockito 5 inline mock maker)
    private val equilPumpPlugin: EquilPumpPlugin = mock()
    private val equilManager: EquilManager = mock()
    private val equilHistoryRecordDao: EquilHistoryRecordDao = mock()
    private val bleTransport: EquilBleTransport = mock()

    private lateinit var sut: EquilWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher never auto-runs the viewModelScope.launch bodies, so construction
        // stays clean and we assert against the default state + synchronous methods.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = EquilWizardViewModel(
            context, rh, aapsLogger, preferences, commandQueue, equilPumpPlugin, equilManager,
            pumpSync, persistenceLayer, equilHistoryRecordDao, constraintsChecker, ch, profileFunction,
            profileRepository, rxBus, insulinManager, bleTransport, hardLimits
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default state exposes the PAIR workflow and cleared progress`() {
        assertThat(sut.workflow.value).isEqualTo(EquilWorkflow.PAIR)
        assertThat(sut.totalSteps.value).isEqualTo(6)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.wizardStep.value).isNull()
        assertThat(sut.isLoading.value).isFalse()
        assertThat(sut.errorMessage.value).isNull()
        assertThat(sut.fillComplete.value).isFalse()
        assertThat(sut.autoFilling.value).isFalse()
        assertThat(sut.scanning.value).isFalse()
        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
        assertThat(sut.showInsulinStep).isFalse()
        assertThat(sut.serialNumberDisplay.value).isEqualTo("")
        assertThat(sut.canGoBack.value).isFalse()
    }

    @Test
    fun `moveStep sets the step, its title and clears loading and error`() {
        sut.moveStep(EquilWizardStep.FILL)

        assertThat(sut.wizardStep.value).isEqualTo(EquilWizardStep.FILL)
        assertThat(sut.isLoading.value).isFalse()
        assertThat(sut.errorMessage.value).isNull()
        // workflowSteps is still empty before a workflow is initialized -> index coerced to 0
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.titleResId.value).isEqualTo(EquilWizardStep.FILL.titleResId)
    }

    @Test
    fun `selectInsulin updates the selected insulin`() {
        val iCfg: ICfg = mock()

        sut.selectInsulin(iCfg)

        assertThat(sut.selectedInsulin.value).isSameInstanceAs(iCfg)
    }

    @Test
    fun `selectProfile updates the selected profile`() {
        sut.selectProfile("Morning")

        assertThat(sut.selectedProfile.value).isEqualTo("Morning")
    }

    @Test
    fun `updateSiteLocation and updateSiteArrow update state`() {
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)
        sut.updateSiteArrow(TE.Arrow.UP)

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.UP)
    }

    @Test
    fun `skipSiteLocation resets location and arrow to NONE`() {
        sut.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)
        sut.updateSiteArrow(TE.Arrow.UP)

        sut.skipSiteLocation()

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
    }

    @Test
    fun `hasPreviousStep is false before a workflow is initialized`() {
        assertThat(sut.hasPreviousStep(EquilWizardStep.ASSEMBLE)).isFalse()
        assertThat(sut.hasPreviousStep(EquilWizardStep.CONFIRM)).isFalse()
    }
}
