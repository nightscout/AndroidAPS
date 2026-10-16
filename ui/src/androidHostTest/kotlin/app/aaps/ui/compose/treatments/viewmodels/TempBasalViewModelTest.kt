package app.aaps.ui.compose.treatments.viewmodels

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class TempBasalViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: TempBasalViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(persistenceLayer.observeChanges(TB::class)).thenReturn(emptyFlow())
        sut = TempBasalViewModel(persistenceLayer, profileFunction, activePlugin, generatedTextResolver(), dateUtil, decimalFormatter, aapsLogger, rxBus)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is not in removing mode and has no selection`() {
        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isFalse()
        assertThat(state.selectedItems).isEmpty()
        assertThat(state.tempBasals).isEmpty()
        assertThat(state.showInvalidated).isFalse()
    }

    @Test
    fun `enterSelectionMode selects the item and enables removing mode`() {
        val item = mock<TB>()

        sut.enterSelectionMode(item)

        assertThat(sut.uiState.value.isRemovingMode).isTrue()
        assertThat(sut.uiState.value.selectedItems).containsExactly(item)
    }

    @Test
    fun `exitSelectionMode clears selection`() {
        sut.enterSelectionMode(mock<TB>())

        sut.exitSelectionMode()

        assertThat(sut.uiState.value.isRemovingMode).isFalse()
        assertThat(sut.uiState.value.selectedItems).isEmpty()
    }

    @Test
    fun `toggleSelection adds then removes an item`() {
        val first = mock<TB>()
        val second = mock<TB>()
        sut.enterSelectionMode(first)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first, second)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first)
    }

    @Test
    fun `toggleInvalidated flips the flag`() {
        sut.toggleInvalidated()
        assertThat(sut.uiState.value.showInvalidated).isTrue()
    }

    @Test
    fun `getDeleteConfirmationMessage empty when nothing selected, plural for many`() = runBlocking {
        // getDeleteConfirmationMessage is suspend on this VM (unlike the sibling VMs).
        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("")

        sut.enterSelectionMode(mock<TB>())
        sut.toggleSelection(mock<TB>())
        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("Are you sure you want to remove 2 items")
    }

    /**
     * A pump that fakes temp basals with extended boluses shows each extended bolus as a row here,
     * built in memory. Every such row had id 0, so two of them gave the list a duplicate key and the
     * Treatments screen crashed when the tab was opened.
     */
    @Test
    fun `extended boluses shown as temp basals get their own ids`() = runBlocking<Unit> {
        val now = 1_700_000_000_000L
        val pump = mock<PumpWithConcentration>()
        whenever(pump.isFakingTempsByExtendedBoluses).thenReturn(true)
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(dateUtil.now()).thenReturn(now)
        val profile = mock<EffectiveProfile>()
        whenever(profile.getBasal(any())).thenReturn(1.0)
        whenever(profileFunction.getProfile(any<Long>())).thenReturn(profile)
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTime(any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getExtendedBolusesStartingFromTime(any(), any())).thenReturn(
            listOf(
                EB(id = 7, timestamp = now - 3_600_000, duration = 1_800_000, amount = 1.0),
                EB(id = 8, timestamp = now - 7_200_000, duration = 1_800_000, amount = 1.0)
            )
        )

        sut.loadData()

        val rows = sut.uiState.value.tempBasals
        assertThat(rows.map { it.type }).containsExactly(TB.Type.FAKE_EXTENDED, TB.Type.FAKE_EXTENDED)
        assertThat(rows.map { it.id }).containsExactly(7L, 8L)
    }
}
