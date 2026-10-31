package app.aaps.ui.compose.treatments.viewmodels

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.generatedTextResolver
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExtendedBolusViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var config: Config

    private lateinit var sut: ExtendedBolusViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(persistenceLayer.observeChanges(EB::class)).thenReturn(emptyFlow())
        sut = ExtendedBolusViewModel(persistenceLayer, generatedTextResolver(), dateUtil, aapsLogger, rxBus, batchExecutor, config)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is not in removing mode and has no selection`() {
        val state = sut.uiState.value
        assertThat(state.isRemovingMode).isFalse()
        assertThat(state.selectedItems).isEmpty()
        assertThat(state.extendedBoluses).isEmpty()
        assertThat(state.showInvalidated).isFalse()
    }

    @Test
    fun `enterSelectionMode selects the item and enables removing mode`() {
        val item = mock<EB>()

        sut.enterSelectionMode(item)

        assertThat(sut.uiState.value.isRemovingMode).isTrue()
        assertThat(sut.uiState.value.selectedItems).containsExactly(item)
    }

    @Test
    fun `exitSelectionMode clears selection`() {
        sut.enterSelectionMode(mock<EB>())

        sut.exitSelectionMode()

        assertThat(sut.uiState.value.isRemovingMode).isFalse()
        assertThat(sut.uiState.value.selectedItems).isEmpty()
    }

    @Test
    fun `toggleSelection adds then removes an item`() {
        val first = mock<EB>()
        val second = mock<EB>()
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
    fun `requestDelete asks the master to remove the selected extended boluses by their ids`() = runTest {
        sut.enterSelectionMode(mock<EB> {
            on { id } doReturn 3L
            on { ids } doReturn IDs(nightscoutId = "ns3")
        })

        sut.requestDelete()

        val actions = argumentCaptor<List<BatchAction>>()
        verify(batchExecutor).prepare(actions.capture(), eq(Sources.Treatments), any())
        assertThat(actions.firstValue).containsExactly(
            BatchAction.Remove(BatchAction.Remove.Kind.EXTENDED_BOLUS, listOf(BatchAction.Remove.Ref(nsId = "ns3", localId = 3L)))
        )
    }
}
