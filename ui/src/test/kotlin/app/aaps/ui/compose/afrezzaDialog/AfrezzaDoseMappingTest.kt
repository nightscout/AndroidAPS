package app.aaps.ui.compose.afrezzaDialog

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies the Afrezza dose-halving rule in [AfrezzaDialogViewModel.confirmAndLog].
 *
 * Afrezza inhaled cartridges are logged as U100-equivalent IU: cartridge units / 2.0.
 * So 4U -> 2.0, 8U -> 4.0, 12U -> 6.0 — the value stored as the bolus amount (what IOB is
 * computed from), NOT the printed cartridge size. This locks the mapping against regressions,
 * including the silent revert where the /2.0 edit was in source but never reached the built app.
 *
 * A green test proves the code stores half as designed. It does NOT establish that halving is
 * correct for any individual; /2.0 is the label's population estimate, a clinical question.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AfrezzaDoseMappingTest {

    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: AfrezzaDialogViewModel

    private val afrezzaCfg = ICfg(
        insulinLabel = "Afrezza (Inhaled)",
        // Must match InsulinType.OREF_INHALED_AFREZZA.insulinPeakTime (15 min) - findAfrezzaIcfg()
        // matches on this exact peak, and 40 falls outside the 10-30 min clinical range this
        // branch's own adjustable-range limits allow, so it never actually matched.
        peak = 15,
        dia = 2.5,
        concentration = 1.0
    )

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // UnconfinedTestDispatcher runs viewModelScope.launch {} synchronously,
        // so confirmAndLog() completes (incl. the suspend insertOrUpdateBolus) before assertions.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        whenever(insulinManager.insulins).thenReturn(arrayListOf(afrezzaCfg))
        whenever(rh.gs(eq(R.string.afrezza_inhaled))).thenReturn("Afrezza inhaled")
        whenever(rh.gs(eq(R.string.afrezza_logged), any())).thenReturn("logged")
        whenever(dateUtil.now()).thenReturn(1_000L)

        sut = AfrezzaDialogViewModel(
            insulinManager, persistenceLayer, uel, dateUtil, rh,
            aapsLogger, commandQueue, profileFunction, preferences
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loggedAmountFor(cartridgeUnits: Int): Double {
        sut.selectCartridge(cartridgeUnits)
        sut.confirmAndLog()
        val captor = argumentCaptor<BS>()
        // insertOrUpdateBolus is a suspend fun -> verify with verifyBlocking.
        verifyBlocking(persistenceLayer) {
            insertOrUpdateBolus(
                bolus = captor.capture(),
                action = eq(Action.BOLUS),
                source = eq(Sources.AfrezzaDialog),
                note = any()
            )
        }
        return captor.firstValue.amount
    }

    @Test
    fun `4U cartridge is stored as 2_0`() {
        assertThat(loggedAmountFor(4)).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `8U cartridge is stored as 4_0`() {
        assertThat(loggedAmountFor(8)).isWithin(1e-9).of(4.0)
    }

    @Test
    fun `12U cartridge is stored as 6_0`() {
        assertThat(loggedAmountFor(12)).isWithin(1e-9).of(6.0)
    }

    @Test
    fun `user-entry log records the halved amount, not the cartridge size`() {
        sut.selectCartridge(12)
        sut.confirmAndLog()
        val captor = argumentCaptor<ValueWithUnit>()
        // uel.log(action, source, note, value) is NOT suspend -> plain verify.
        verify(uel).log(
            eq(Action.BOLUS),
            eq(Sources.InsulinDialog),
            any<String>(),
            captor.capture()
        )
        val insulin = captor.firstValue as ValueWithUnit.Insulin
        assertThat(insulin.value).isWithin(1e-9).of(6.0)
    }
}
