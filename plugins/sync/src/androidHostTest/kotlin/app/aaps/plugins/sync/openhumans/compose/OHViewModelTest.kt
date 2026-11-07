package app.aaps.plugins.sync.openhumans.compose

import app.aaps.plugins.sync.openhumans.OpenHumansState
import app.aaps.plugins.sync.openhumans.delegates.OHStateDelegate
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class OHViewModelTest {

    @Mock private lateinit var stateDelegate: OHStateDelegate

    private val stateFlow = MutableStateFlow<OpenHumansState?>(null)

    private lateinit var sut: OHViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(stateDelegate.stateFlow).thenReturn(stateFlow)
        sut = OHViewModel(stateDelegate)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `null state means logged out`() {
        stateFlow.value = null
        assertThat(sut.uiState.value.isLoggedIn).isFalse()
        assertThat(sut.uiState.value.projectMemberId).isNull()
    }

    @Test
    fun `non-null state means logged in with projectMemberId`() {
        stateFlow.value = OpenHumansState(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = 1000L,
            projectMemberId = "member-123",
            uploadOffset = 0L
        )
        assertThat(sut.uiState.value.isLoggedIn).isTrue()
        assertThat(sut.uiState.value.projectMemberId).isEqualTo("member-123")
    }

    @Test
    fun `transition from logged in back to null resets state`() {
        stateFlow.value = OpenHumansState(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = 1000L,
            projectMemberId = "member-456",
            uploadOffset = 0L
        )
        assertThat(sut.uiState.value.isLoggedIn).isTrue()

        stateFlow.value = null
        assertThat(sut.uiState.value.isLoggedIn).isFalse()
        assertThat(sut.uiState.value.projectMemberId).isNull()
    }
}
