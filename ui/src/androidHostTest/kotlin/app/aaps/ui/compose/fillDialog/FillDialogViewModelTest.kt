package app.aaps.ui.compose.fillDialog

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.generatedTextResolver
import app.aaps.ui.UiStringsValues
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class FillDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var translator: Translator
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var masterActionExecutor: MasterActionExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

    private val pump: PumpWithConcentration = mock()
    private val pumpDescription: PumpDescription = mock()

    private lateinit var sut: FillDialogViewModel

    /**
     * Real English instead of a marker string, so the assertions below are the wording the user reads.
     * This screen names strings from `:ui` as well, which `:shared:tests` cannot see - this module can,
     * so it hands the generated map over rather than taking a new dependency.
     */
    private lateinit var rh: TextResolver

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        rh = generatedTextResolver("ui" to UiStringsValues::textOf)
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(activePlugin.activePumpInternal).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpDescription.bolusStep).thenReturn(0.1)
        // A tube pump unless a test says otherwise: it can be primed from this dialog.
        whenever(pumpDescription.isRefillingCapable).thenReturn(true)
        // Up to 10 pump units, the rest as asked.
        whenever(constraintChecker.applyPrimingConstraints(any())).thenAnswer { PumpInsulin(it.getArgument<PumpInsulin>(0).cU.coerceAtMost(10.0)) }
        whenever(insulinManager.insulins).thenReturn(arrayListOf<ICfg>())
        // observeConcentrationEnabled() builds a flow from preferences.observe(key) in init (collection deferred).
        whenever(preferences.observe(BooleanKey.GeneralInsulinConcentration)).thenReturn(MutableStateFlow(false))
        sut = buildSut()
    }

    private fun buildSut() = FillDialogViewModel(
        SavedStateHandle(), constraintChecker, activePlugin, persistenceLayer, preferences, config,
        decimalFormatter, rh, dateUtil, translator, aapsLogger, ch, insulinManager, profileFunction,
        masterActionExecutor, batchExecutor, rxBus, CoroutineScope(UnconfinedTestDispatcher())
    )

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // ---------------------------------------------------------------------------------------------
    // The promised insulin switch must never fail silently
    // ---------------------------------------------------------------------------------------------

    /**
     * Messages the user actually gets: the app-level dialog bus, which outlives the closed dialog. Asserting on
     * the ViewModel's own SharedFlow would prove nothing — the screen has already navigated away by then, so a
     * screen-scoped collector no longer exists.
     */
    private fun reportedMessages(): List<String> {
        val captor = argumentCaptor<Event>()
        verify(rxBus, atLeast(0)).send(captor.capture())
        return captor.allValues.filterIsInstance<EventShowDialog.Ok>().map { it.message }
    }

    private fun reportedTitles(): List<String> {
        val captor = argumentCaptor<Event>()
        verify(rxBus, atLeast(0)).send(captor.capture())
        return captor.allValues.filterIsInstance<EventShowDialog.Ok>().map { it.title }
    }

    /**
     * Drive a cartridge change whose selected insulin differs from the active one (there is none), with no prime
     * bolus — so `confirmAndSave` takes the "switch immediately" branch and the outcome of [outcome] is what the
     * user is told about. The confirmation has already promised "profile switch will be applied".
     */
    private suspend fun runCartridgeChangeWithInsulinSwitch(outcome: ActionProgress): List<String> {
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(outcome)
        // A cartridge change makes hasAction true without a prime bolus, so the switch runs immediately;
        // selecting an insulin while nothing is active makes insulinChanged true.
        sut.selectInsulin(ICfg(insulinLabel = "Fiasp", insulinEndTime = 480, insulinPeakTime = 55, concentration = 1.0))
        sut.updateCartridgeChange(true)
        sut.buildConfirmationSummary()   // sets confirmedState
        sut.confirmAndSave()
        return reportedMessages()
    }

    @Test
    fun `a rejected insulin activation is reported, not silently dropped`() = runTest {
        val seen = runCartridgeChangeWithInsulinSwitch(ActionProgress.Rejected(FailureReason.NotReachable, "offline"))

        // Previously this only hit aapsLogger.warn — the user was told the insulin changed when it had not.
        // The reason is a whole sentence and brings its own full stop, so the template adds none.
        assertThat(seen).contains("The insulin was not switched: The master is offline. Your previous insulin is still in use.")
    }

    @Test
    fun `an unconfirmed insulin activation is reported as unknown, not as failed`() = runTest {
        val seen = runCartridgeChangeWithInsulinSwitch(ActionProgress.Unconfirmed(FailureReason.NoReply, "timeout"))

        // Unknown must be neither claimed nor denied: it may still land via sync-back.
        assertThat(seen).contains("The insulin switch could not be confirmed. Check which insulin is active in your profile before dosing.")
    }

    @Test
    fun `a successful insulin activation says nothing`() = runTest {
        val seen = runCartridgeChangeWithInsulinSwitch(ActionProgress.Applied)

        assertThat(seen).isEmpty()
    }

    /**
     * Drive a prime bolus that fails. The insulin switch is chained to the prime's `onSuccess`, so a failed prime
     * skips it entirely — and the confirmation has already promised it. [changeInsulin] selects a different insulin
     * (there is no active one) to make that promise, or leaves it alone so no switch was ever promised.
     */
    private suspend fun runFailingPrime(changeInsulin: Boolean, cancelled: Boolean = false): List<String> {
        // A non-zero constrained amount makes hasPrimeBolus true, so the switch is chained to the prime.
        whenever(masterActionExecutor.deliverFillBolus(any(), anyOrNull(), any(), any(), any())).thenAnswer { inv ->
            inv.getArgument<(MasterActionExecutor.Failure) -> Unit>(3).invoke(MasterActionExecutor.Failure("pump error", cancelled))
        }
        if (changeInsulin) sut.selectInsulin(ICfg(insulinLabel = "Fiasp", insulinEndTime = 480, insulinPeakTime = 55, concentration = 1.0))
        sut.updateCartridgeChange(true)
        sut.updateInsulin(0.3)
        sut.buildConfirmationSummary()
        sut.confirmAndSave()
        return reportedMessages()
    }

    /**
     * A prime dropped from the queue (a settings import cleared it) did not fail, so calling it a delivery error
     * is wrong twice over: it names the pump as the culprit, and "error" is the word the alarm tier uses.
     */
    @Test
    fun `a cancelled prime is not reported as a delivery error`() = runTest {
        runFailingPrime(changeInsulin = false, cancelled = true)

        assertThat(reportedTitles()).contains("Cancelled")
        assertThat(reportedTitles()).doesNotContain("Bolus reported an error. Manually check real delivered insulin and carb amount")
    }

    @Test
    fun `a failed prime says the promised insulin switch did not happen either`() = runTest {
        val seen = runFailingPrime(changeInsulin = true)

        // One combined message, not two dialogs stacked on the user.
        assertThat(seen).contains("pump error\n\nThe insulin was also not switched.")
        assertThat(seen).doesNotContain("pump error")
    }

    @Test
    fun `a failed prime with no insulin change reports only the delivery error`() = runTest {
        val seen = runFailingPrime(changeInsulin = false)

        // Nothing was promised, so the message must not claim an insulin switch was skipped.
        assertThat(seen).contains("pump error")
        assertThat(seen).doesNotContain("PRIME_FAILED_AND_NOT_SWITCHED")
    }

    // ---------------------------------------------------------------------------------------------
    // A pump that fills and primes itself (patch pump)
    // ---------------------------------------------------------------------------------------------

    /**
     * A patch pump primes in its own activation wizard and its cannula is already in the body. A prime from
     * here would be insulin outside IOB, so the dialog offers none and never sends one - even if an amount
     * somehow got into the state.
     */
    @Test
    fun `a pump that primes itself gets no prime, only the site change`() = runTest {
        whenever(pumpDescription.isRefillingCapable).thenReturn(false)
        sut = buildSut()
        whenever(batchExecutor.prepare(any(), any(), any())).thenReturn(ActionProgress.Applied)

        sut.updateInsulin(0.3)
        sut.updateSiteChange(true)
        val lines = sut.buildConfirmationSummary()
        sut.confirmAndSave()

        assertThat(sut.uiState.value.primeAvailable).isFalse()
        assertThat(sut.uiState.value.hasPrime).isFalse()
        // No prime amount line and no prime warning in the confirmation.
        assertThat(lines.map { it.role }).doesNotContain(ConfirmationRole.BOLUS)
        assertThat(lines.map { it.role }).doesNotContain(ConfirmationRole.WARNING)
        verify(masterActionExecutor, never()).deliverFillBolus(any(), anyOrNull(), any(), any(), any())
        // The site change is still recorded.
        verify(batchExecutor).prepare(any(), any(), any())
    }

    @Test
    fun `on a pump that primes itself an amount alone is not an action`() {
        whenever(pumpDescription.isRefillingCapable).thenReturn(false)
        sut = buildSut()

        sut.updateInsulin(0.3)

        assertThat(sut.uiState.value.hasAction).isFalse()
    }

    // ---------------------------------------------------------------------------------------------
    // A prime is in pump units
    // ---------------------------------------------------------------------------------------------

    /**
     * The amount goes to the pump as it is, so it is limited by the priming constraint (pump units), and the
     * step of the dialog is the pump's own, not the one of the concentration wrapper (insulin units).
     */
    @Test
    fun `the prime amount, its max and its step are in pump units`() {
        val driverDescription: PumpDescription = mock()
        whenever(driverDescription.bolusStep).thenReturn(0.05)
        val driver: Pump = mock()
        whenever(driver.pumpDescription).thenReturn(driverDescription)
        whenever(activePlugin.activePumpInternal).thenReturn(driver)
        whenever(constraintChecker.applyPrimingConstraints(any())).thenAnswer { PumpInsulin(it.getArgument<PumpInsulin>(0).cU.coerceAtMost(5.0)) }
        sut = buildSut()

        sut.updateInsulin(7.0)

        assertThat(sut.uiState.value.maxInsulin).isEqualTo(5.0)
        assertThat(sut.uiState.value.bolusStep).isEqualTo(0.05)
        assertThat(sut.uiState.value.insulinAfterConstraints).isEqualTo(5.0)
        assertThat(sut.uiState.value.constraintApplied).isTrue()
        verify(constraintChecker, never()).applyBolusConstraints(any())
    }

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
