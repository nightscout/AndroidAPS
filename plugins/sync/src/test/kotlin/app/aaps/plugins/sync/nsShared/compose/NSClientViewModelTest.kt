package app.aaps.plugins.sync.nsShared.compose

import app.aaps.core.interfaces.nsclient.NSClientLog
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
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
internal class NSClientViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var nsClientRepository: NSClientRepository
    @Mock private lateinit var preferences: Preferences

    private val queueSizeFlow = MutableStateFlow(Long.MIN_VALUE)
    private val statusFlow = MutableStateFlow("")
    private val urlFlow = MutableStateFlow("")
    private val logListFlow = MutableStateFlow<List<NSClientLog>>(emptyList())

    private lateinit var sut: NSClientViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)).thenReturn("UNAVAILABLE")
        whenever(nsClientRepository.queueSize).thenReturn(queueSizeFlow)
        whenever(nsClientRepository.statusUpdate).thenReturn(statusFlow)
        whenever(nsClientRepository.urlUpdate).thenReturn(urlFlow)
        whenever(nsClientRepository.logList).thenReturn(logListFlow)
        sut = NSClientViewModel(rh, activePlugin, nsClientRepository, preferences)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has defaults from flows`() {
        assertThat(sut.uiState.value.url).isEmpty()
        assertThat(sut.uiState.value.status).isEmpty()
        assertThat(sut.uiState.value.paused).isFalse()
        assertThat(sut.uiState.value.logList).isEmpty()
    }

    @Test
    fun `positive queue size is formatted as number`() {
        queueSizeFlow.value = 42
        assertThat(sut.uiState.value.queue).isEqualTo("42")
    }

    @Test
    fun `negative queue size shows UNAVAILABLE`() {
        queueSizeFlow.value = -1
        assertThat(sut.uiState.value.queue).isEqualTo("UNAVAILABLE")
    }

    @Test
    fun `zero queue size is formatted as zero`() {
        queueSizeFlow.value = 0
        assertThat(sut.uiState.value.queue).isEqualTo("0")
    }

    @Test
    fun `status flow updates uiState`() {
        statusFlow.value = "Connected"
        assertThat(sut.uiState.value.status).isEqualTo("Connected")
    }

    @Test
    fun `url flow updates uiState`() {
        urlFlow.value = "https://nightscout.example.com"
        assertThat(sut.uiState.value.url).isEqualTo("https://nightscout.example.com")
    }

    @Test
    fun `logList flow updates uiState`() {
        val logs = listOf(NSClientLog("action", "text"))
        logListFlow.value = logs
        assertThat(sut.uiState.value.logList).hasSize(1)
        assertThat(sut.uiState.value.logList[0].action).isEqualTo("action")
    }

    @Test
    fun `loadInitialData reads NsPaused from preferences`() {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        sut.loadInitialData()
        assertThat(sut.uiState.value.paused).isTrue()
    }

    @Test
    fun `updatePaused sets paused in uiState`() {
        assertThat(sut.uiState.value.paused).isFalse()
        sut.updatePaused(true)
        assertThat(sut.uiState.value.paused).isTrue()
        sut.updatePaused(false)
        assertThat(sut.uiState.value.paused).isFalse()
    }
}
