package app.aaps.plugins.sync.xdrip.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class XdripViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var xdripMvvmRepository: XdripMvvmRepository
    @Mock private lateinit var dataSyncSelector: DataSyncSelectorXdrip

    private val queueSizeFlow = MutableStateFlow(Long.MIN_VALUE)
    private val logListFlow = MutableStateFlow<List<XdripLog>>(emptyList())

    private lateinit var sut: XdripViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)).thenReturn("UNAVAILABLE")
        whenever(xdripMvvmRepository.queueSize).thenReturn(queueSizeFlow)
        whenever(xdripMvvmRepository.logList).thenReturn(logListFlow)
        sut = XdripViewModel(rh, xdripMvvmRepository, dataSyncSelector)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `positive queue size is formatted as number`() {
        queueSizeFlow.value = 15
        assertThat(sut.uiState.value.queue).isEqualTo("15")
    }

    @Test
    fun `negative queue size shows UNAVAILABLE`() {
        queueSizeFlow.value = -1
        assertThat(sut.uiState.value.queue).isEqualTo("UNAVAILABLE")
    }

    @Test
    fun `logList flow updates uiState`() {
        val logs = listOf(XdripLog("action", "text"))
        logListFlow.value = logs
        assertThat(sut.uiState.value.logList).hasSize(1)
        assertThat(sut.uiState.value.logList[0].action).isEqualTo("action")
    }

    @Test
    fun `loadInitialData reads queue size from dataSyncSelector`() {
        whenever(dataSyncSelector.queueSize()).thenReturn(99L)
        sut.loadInitialData()
        verify(xdripMvvmRepository).updateQueueSize(99L)
    }

    @Test
    fun `initial uiState logList is empty`() {
        assertThat(sut.uiState.value.logList).isEmpty()
    }
}
