package app.aaps.ui.compose.treatments.viewmodels

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.R
import app.aaps.ui.compose.treatments.MealLink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
internal class BolusCarbsViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus

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
        whenever(persistenceLayer.observeChanges(BS::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(CA::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(BCR::class.java)).thenReturn(emptyFlow())
        sut = BolusCarbsViewModel(persistenceLayer, profileFunction, rh, dateUtil, decimalFormatter, aapsLogger, rxBus)
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
    fun `getDeleteConfirmationMessage is empty when nothing is selected`() {
        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("")
    }

    @Test
    fun `getDeleteConfirmationMessage uses the plural string for multiple selection`() {
        whenever(rh.gs(R.string.confirm_remove_multiple_items, 2)).thenReturn("Remove 2 items")
        sut.enterSelectionMode(link())
        sut.toggleSelection(link())

        assertThat(sut.getDeleteConfirmationMessage()).isEqualTo("Remove 2 items")
    }
}
