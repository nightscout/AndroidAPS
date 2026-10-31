package app.aaps.ui.compose.treatments.viewmodels

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.shared.tests.generatedTextResolver
import app.aaps.ui.compose.treatments.MealLink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class BolusCarbsViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var config: Config

    private lateinit var sut: BolusCarbsViewModel

    // MealLink is a data class (value equality); wrapping distinct CA mocks yields identity-distinct links.
    private fun link() = MealLink(carbs = mock<CA>())

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // observeTreatmentChanges() merges these three in init; empty flows complete and never re-trigger.
        // The initial loadData() background load is intentionally left unstubbed — it fails and is swallowed
        // by the VM's try/catch, never mutating the structural fields asserted below.
        whenever(persistenceLayer.observeChanges(BS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(CA::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(BCR::class)).thenReturn(emptyFlow())
        sut = BolusCarbsViewModel(persistenceLayer, profileFunction, generatedTextResolver(), dateUtil, decimalFormatter, aapsLogger, rxBus, batchExecutor, config)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default uiState is not in removing mode and has no selection`() {
        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isFalse()
        assertThat(state.selectedItems).isEmpty()
        assertThat(state.mealLinks).isEmpty()
        assertThat(state.showInvalidated).isFalse()
    }

    @Test
    fun `enterSelectionMode selects the item and enables removing mode`() {
        val item = link()

        sut.enterSelectionMode(item)

        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isTrue()
        assertThat(state.selectedItems).containsExactly(item)
    }

    @Test
    fun `exitSelectionMode clears selection and disables removing mode`() {
        sut.enterSelectionMode(link())

        sut.exitSelectionMode()

        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isFalse()
        assertThat(state.selectedItems).isEmpty()
    }

    @Test
    fun `toggleSelection adds an item and then removes it`() {
        val first = link()
        val second = link()
        sut.enterSelectionMode(first)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first, second)

        sut.toggleSelection(second)
        assertThat(sut.uiState.value.selectedItems).containsExactly(first)
    }

    @Test
    fun `toggleInvalidated flips the showInvalidated flag`() {
        assertThat(sut.uiState.value.showInvalidated).isFalse()

        sut.toggleInvalidated()

        assertThat(sut.uiState.value.showInvalidated).isTrue()
    }

    @Test
    fun `requestDelete asks the master to remove the bolus, carbs and wizard result of a meal`() = runTest {
        val bolus = mock<BS> {
            on { id } doReturn 1L
            on { ids } doReturn IDs(nightscoutId = "bs1")
        }
        val carbs = mock<CA> {
            on { id } doReturn 2L
            on { ids } doReturn IDs(nightscoutId = "ca2")
        }
        val bcr = mock<BCR> {
            on { id } doReturn 3L
            on { ids } doReturn IDs(nightscoutId = "bcr3")
        }
        sut.enterSelectionMode(MealLink(bolus = bolus, carbs = carbs, bolusCalculatorResult = bcr))

        sut.requestDelete()

        val actions = argumentCaptor<List<BatchAction>>()
        verify(batchExecutor).prepare(actions.capture(), eq(Sources.Treatments), any())
        assertThat(actions.firstValue).containsExactly(
            BatchAction.Remove(BatchAction.Remove.Kind.BOLUS, listOf(BatchAction.Remove.Ref(nsId = "bs1", localId = 1L))),
            BatchAction.Remove(BatchAction.Remove.Kind.CARBS, listOf(BatchAction.Remove.Ref(nsId = "ca2", localId = 2L))),
            BatchAction.Remove(BatchAction.Remove.Kind.BOLUS_CALCULATOR_RESULT, listOf(BatchAction.Remove.Ref(nsId = "bcr3", localId = 3L)))
        )
    }

    @Test
    fun `requestDelete with nothing selected asks nothing`() = runTest {
        sut.requestDelete()

        verify(batchExecutor, never()).prepare(any(), any(), any())
    }
}
