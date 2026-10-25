package app.aaps.plugins.sync.tidepool.compose

import app.aaps.core.interfaces.sync.SyncLogEntry
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
    private val logListFlow = MutableStateFlow<List<SyncLogEntry>>(emptyList())

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
        assertThat(sut.uiState.value.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.NONE)
        assertThat(sut.uiState.value.logList).isEmpty()
    }

    @Test
    fun `connectionStatus flow updates uiState`() {
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED
        assertThat(sut.uiState.value.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)
    }

    @Test
    fun `logout is offered while a saved login can be used`() {
        whenever(authFlowOut.hasSavedLogin).thenReturn(true)
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.FAILED
        assertThat(sut.uiState.value.loggedIn).isTrue()
    }

    @Test
    fun `login is offered when the saved token stopped working`() {
        whenever(authFlowOut.hasSavedLogin).thenReturn(true)
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN
        assertThat(sut.uiState.value.loggedIn).isFalse()
    }

    @Test
    fun `login is offered when no login is saved`() {
        whenever(authFlowOut.hasSavedLogin).thenReturn(false)
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.BLOCKED
        assertThat(sut.uiState.value.loggedIn).isFalse()
    }

    @Test
    fun `logList flow updates uiState`() {
        val logs = listOf(SyncLogEntry("uploading..."))
        logListFlow.value = logs
        assertThat(sut.uiState.value.logList).hasSize(1)
        assertThat(sut.uiState.value.logList[0].action).isEqualTo("uploading...")
    }

    @Test
    fun `loadInitialData delegates to repository with authFlowOut connectionStatus`() {
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
        sut.loadInitialData()
        verify(tidepoolRepository).updateConnectionStatus(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
    }

    @Test
    fun `status and log are tracked independently in uiState`() {
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.FETCHING_TOKEN
        logListFlow.value = listOf(SyncLogEntry("a"), SyncLogEntry("b"))

        assertThat(sut.uiState.value.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
        assertThat(sut.uiState.value.logList).hasSize(2)

        // A later status change must not clobber the already-collected log list.
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED
        assertThat(sut.uiState.value.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)
        assertThat(sut.uiState.value.logList).hasSize(2)
    }

    @Test
    fun `latest status wins after several updates`() {
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.BLOCKED
        connectionStatusFlow.value = AuthFlowOut.ConnectionStatus.NO_SESSION
        assertThat(sut.uiState.value.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.NO_SESSION)
    }
}
