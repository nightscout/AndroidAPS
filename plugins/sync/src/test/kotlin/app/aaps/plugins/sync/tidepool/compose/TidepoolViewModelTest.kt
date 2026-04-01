package app.aaps.plugins.sync.tidepool.compose

import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
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
internal class TidepoolViewModelTest {

    @Mock private lateinit var tidepoolRepository: TidepoolRepository
    @Mock private lateinit var authFlowOut: AuthFlowOut

    private val connectionStatusFlow = MutableStateFlow(AuthFlowOut.ConnectionStatus.NONE)
    private val logListFlow = MutableStateFlow<List<TidepoolLog>>(emptyList())

    private lateinit var sut: TidepoolViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(tidepoolRepository.connectionStatus).thenReturn(connectionStatusFlow)
        whenever(tidepoolRepository.logList).thenReturn(logListFlow)
        sut = TidepoolViewModel(tidepoolRepository, authFlowOut)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects NONE status`() {
        assertThat(sut.uiState.value.connectionStatus).isEqualTo("NONE")
        assertThat(sut.uiState.value.logList).isEmpty()
    }

    @Test
    fun `connectionStatus flow updates uiState with status name`() {
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED
        assertThat(sut.uiState.value.connectionStatus).isEqualTo("SESSION_ESTABLISHED")
    }

    @Test
    fun `logList flow updates uiState`() {
        val logs = listOf(TidepoolLog("uploading..."))
        logListFlow.value = logs
        assertThat(sut.uiState.value.logList).hasSize(1)
        assertThat(sut.uiState.value.logList[0].status).isEqualTo("uploading...")
    }

    @Test
    fun `loadInitialData delegates to repository with authFlowOut connectionStatus`() {
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
        sut.loadInitialData()
        verify(tidepoolRepository).updateConnectionStatus(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
    }
}
