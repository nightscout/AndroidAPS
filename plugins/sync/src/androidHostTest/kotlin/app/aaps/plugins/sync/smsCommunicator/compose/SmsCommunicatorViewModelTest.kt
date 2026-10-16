package app.aaps.plugins.sync.smsCommunicator.compose

import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.utils.DateUtil
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class SmsCommunicatorViewModelTest {

    // Real repository (no-arg @Inject constructor) so the VM observes its actual StateFlow.
    private val repository = SmsCommunicatorRepository()

    @Mock private lateinit var dateUtil: DateUtil

    private lateinit var sut: SmsCommunicatorViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // viewModelScope dispatches on Dispatchers.Main; back it with an Unconfined test dispatcher so
        // the init collector runs eagerly and inline.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(dateUtil.timeString(any<Long>())).thenReturn("12:00")
        sut = SmsCommunicatorViewModel(repository, dateUtil)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has no messages`() {
        assertThat(sut.uiState.value.messages).isEmpty()
    }

    @Test
    fun `repository messages are mapped into uiState items`() {
        repository.updateMessages(listOf(Sms("+15551234567", "BOLUS 1.5")))

        assertThat(sut.uiState.value.messages).hasSize(1)
        val item = sut.uiState.value.messages[0]
        assertThat(item.phoneNumber).isEqualTo("+15551234567")
        assertThat(item.text).isEqualTo("BOLUS 1.5")
        assertThat(item.time).isEqualTo("12:00")
        // Sms(phoneNumber, text) constructor marks the message as sent.
        assertThat(item.isSent).isTrue()
        assertThat(item.isReceived).isFalse()
    }

    @Test
    fun `messages are ordered by date ascending`() {
        val older = Sms("+1000000000", "older").apply { date = 1_000L }
        val newer = Sms("+2000000000", "newer").apply { date = 2_000L }
        repository.updateMessages(listOf(newer, older))

        assertThat(sut.uiState.value.messages).hasSize(2)
        assertThat(sut.uiState.value.messages[0].text).isEqualTo("older")
        assertThat(sut.uiState.value.messages[1].text).isEqualTo("newer")
    }
}
