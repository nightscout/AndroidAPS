package app.aaps.ui.compose.fillDialog

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.ui.UiStringIds
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var translator: Translator
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var rxBus: RxBus

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
            wizardBolusExecutor, batchExecutor, rxBus, CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // ---------------------------------------------------------------------------------------------
    // The promised insulin switch must never fail silently
    // ---------------------------------------------------------------------------------------------

    /**
     * buildConfirmationSummary() calls rh.gs heavily and the @Mock returns null → NPE in the line builder, so stub
     * a catch-all first. The specific overrides afterwards let the assertions tell the failure messages apart.
     */
    private fun stubStrings() {
        whenever(rh.gs(any<Int>())).thenReturn("s")
        // The screens name their strings now, so the TextRef overload is the one they call.
        whenever(rh.gs(any<TextRef>())).thenReturn("s")
        whenever(rh.gs(any<Int>(), anyOrNull())).thenReturn("s")
        whenever(rh.gs(any<TextRef>(), anyOrNull())).thenReturn("s")
        whenever(rh.gs(any<Int>(), anyOrNull(), anyOrNull())).thenReturn("s")
        whenever(rh.gs(any<TextRef>(), anyOrNull(), anyOrNull())).thenReturn("s")
        whenever(rh.gs(CoreUiStrings.insulin_activation_unconfirmed)).thenReturn("UNCONFIRMED")
        whenever(rh.gs(eq(CoreUiStrings.insulin_activation_failed_reason), anyOrNull())).thenReturn("FAILED_REASON")
    }

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

    /**
     * Drive a cartridge change whose selected insulin differs from the active one (there is none), with no prime
     * bolus — so `confirmAndSave` takes the "switch immediately" branch and the outcome of [outcome] is what the
     * user is told about. The confirmation has already promised "profile switch will be applied".
     */
    private suspend fun runCartridgeChangeWithInsulinSwitch(outcome: ActionProgress): List<String> {
        stubStrings()
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
        assertThat(seen).contains("FAILED_REASON")
    }

    @Test
    fun `an unconfirmed insulin activation is reported as unknown, not as failed`() = runTest {
        val seen = runCartridgeChangeWithInsulinSwitch(ActionProgress.Unconfirmed(FailureReason.NoReply, "timeout"))

        // Unknown must be neither claimed nor denied: it may still land via sync-back.
        assertThat(seen).contains("UNCONFIRMED")
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
    private suspend fun runFailingPrime(changeInsulin: Boolean): List<String> {
        stubStrings()
        whenever(rh.gs(eq(CoreUiStrings.fill_prime_failed_insulin_not_switched), anyOrNull())).thenReturn("PRIME_FAILED_AND_NOT_SWITCHED")
        // A non-zero constrained amount makes hasPrimeBolus true, so the switch is chained to the prime.
        val constrained: Constraint<Double> = mock()
        whenever(constrained.value()).thenReturn(0.3)
        whenever(constraintChecker.applyBolusConstraints(any())).thenReturn(constrained)
        whenever(wizardBolusExecutor.deliverFillBolus(any(), anyOrNull(), any(), any(), any())).thenAnswer { inv ->
            inv.getArgument<(String) -> Unit>(3).invoke("pump error")
        }
        if (changeInsulin) sut.selectInsulin(ICfg(insulinLabel = "Fiasp", insulinEndTime = 480, insulinPeakTime = 55, concentration = 1.0))
        sut.updateCartridgeChange(true)
        sut.updateInsulin(0.3)
        sut.buildConfirmationSummary()
        sut.confirmAndSave()
        return reportedMessages()
    }

    @Test
    fun `a failed prime says the promised insulin switch did not happen either`() = runTest {
        val seen = runFailingPrime(changeInsulin = true)

        // One combined message, not two dialogs stacked on the user.
        assertThat(seen).contains("PRIME_FAILED_AND_NOT_SWITCHED")
        assertThat(seen).doesNotContain("pump error")
    }

    @Test
    fun `a failed prime with no insulin change reports only the delivery error`() = runTest {
        val seen = runFailingPrime(changeInsulin = false)

        // Nothing was promised, so the message must not claim an insulin switch was skipped.
        assertThat(seen).contains("pump error")
        assertThat(seen).doesNotContain("PRIME_FAILED_AND_NOT_SWITCHED")
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
