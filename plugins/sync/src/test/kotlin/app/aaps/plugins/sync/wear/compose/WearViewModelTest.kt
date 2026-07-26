package app.aaps.plugins.sync.wear.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.wear.WearPlugin
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class WearViewModelTest {

    @Mock private lateinit var wearPlugin: WearPlugin
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var versionCheckerUtils: VersionCheckerUtils
    @Mock private lateinit var fileListProvider: FileListProvider
    @Mock private lateinit var aapsLogger: AAPSLogger

    private val connectedDeviceFlow = MutableStateFlow<String?>(null)
    private val savedCustomWatchfaceFlow = MutableStateFlow<CwfData?>(null)
    private val eventWearUpdateGuiFlow = MutableSharedFlow<EventWearUpdateGui>()

    private lateinit var sut: WearViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(wearPlugin.connectedDevice).thenReturn(connectedDeviceFlow)
        whenever(wearPlugin.savedCustomWatchface).thenReturn(savedCustomWatchfaceFlow)
        whenever(rxBus.toFlow(EventWearUpdateGui::class.java)).thenReturn(eventWearUpdateGuiFlow)
        whenever(rh.gs(R.string.no_watch_connected)).thenReturn("No watch connected")
        sut = WearViewModel(wearPlugin, rxBus, rh, dateUtil, preferences, versionCheckerUtils, fileListProvider, aapsLogger)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows no device connected`() {
        assertThat(sut.uiState.value.isDeviceConnected).isFalse()
        assertThat(sut.uiState.value.hasCustomWatchface).isFalse()
    }

    @Test
    fun `null connected device shows no watch connected text`() {
        connectedDeviceFlow.value = null
        assertThat(sut.uiState.value.connectedDevice).isEqualTo("No watch connected")
        assertThat(sut.uiState.value.isDeviceConnected).isFalse()
    }

    @Test
    fun `non-null connected device shows device name`() {
        connectedDeviceFlow.value = "Galaxy Watch 5"
        assertThat(sut.uiState.value.connectedDevice).isEqualTo("Galaxy Watch 5")
        assertThat(sut.uiState.value.isDeviceConnected).isTrue()
    }

    @Test
    fun `device disconnection updates state`() {
        connectedDeviceFlow.value = "Galaxy Watch 5"
        assertThat(sut.uiState.value.isDeviceConnected).isTrue()
        connectedDeviceFlow.value = null
        assertThat(sut.uiState.value.isDeviceConnected).isFalse()
    }

    @Test
    fun `savedCustomWatchface emits watchface metadata`() {
        val cwfData = CwfData(
            json = "{}",
            metadata = mutableMapOf(CwfMetadataKey.CWF_NAME to "MyWatchface"),
            resData = mutableMapOf()
        )
        savedCustomWatchfaceFlow.value = cwfData
        assertThat(sut.uiState.value.hasCustomWatchface).isTrue()
        assertThat(sut.uiState.value.watchfaceName).isEqualTo("MyWatchface")
        // BitmapFactory returns null in unit tests, so watchfaceImage will be null
        assertThat(sut.uiState.value.watchfaceImage).isNull()
    }

    @Test
    fun `null savedCustomWatchface clears watchface state`() {
        val cwfData = CwfData(
            json = "{}",
            metadata = mutableMapOf(CwfMetadataKey.CWF_NAME to "MyWatchface"),
            resData = mutableMapOf()
        )
        savedCustomWatchfaceFlow.value = cwfData
        assertThat(sut.uiState.value.hasCustomWatchface).isTrue()

        savedCustomWatchfaceFlow.value = null
        assertThat(sut.uiState.value.hasCustomWatchface).isFalse()
        assertThat(sut.uiState.value.watchfaceName).isEmpty()
    }

    @Test
    fun `requestCustomWatchface sends event when no saved watchface`() {
        sut.requestCustomWatchface()
        val captor = argumentCaptor<Event>()
        verify(rxBus).send(captor.capture())
        val event = captor.firstValue as EventMobileToWear
        assertThat(event.payload).isInstanceOf(EventData.ActionrequestCustomWatchface::class.java)
    }

    @Test
    fun `requestCustomWatchface does nothing when watchface exists`() {
        savedCustomWatchfaceFlow.value = CwfData(
            json = "{}",
            metadata = mutableMapOf(CwfMetadataKey.CWF_NAME to "existing"),
            resData = mutableMapOf()
        )
        sut.requestCustomWatchface()
        verify(rxBus, never()).send(any<EventMobileToWear>())
    }

    @Test
    fun `resendData sends ActionResendData event`() {
        sut.resendData()
        val captor = argumentCaptor<Event>()
        verify(rxBus).send(captor.capture())
        val event = captor.firstValue as EventData.ActionResendData
        assertThat(event.from).isEqualTo("WearScreen")
    }

    @Test
    fun `openSettingsOnWear sends OpenSettings event`() {
        whenever(dateUtil.now()).thenReturn(12345L)
        sut.openSettingsOnWear()
        val captor = argumentCaptor<Event>()
        verify(rxBus).send(captor.capture())
        val event = captor.firstValue as EventMobileToWear
        assertThat(event.payload).isInstanceOf(EventData.OpenSettings::class.java)
    }

    @Test
    fun `showCwfInfos and hideCwfInfos toggle showInfos`() {
        // Without cwfData, showCwfInfos should be a no-op
        sut.showCwfInfos()
        assertThat(sut.uiState.value.showInfos).isFalse()

        // Set up cwfData
        val cwfData = CwfData(
            json = "{}",
            metadata = mutableMapOf(
                CwfMetadataKey.CWF_NAME to "TestWatch",
                CwfMetadataKey.CWF_AUTHOR to "Author",
                CwfMetadataKey.CWF_CREATED_AT to "2024-01-01",
                CwfMetadataKey.CWF_VERSION to "1.0"
            ),
            resData = mutableMapOf()
        )
        savedCustomWatchfaceFlow.value = cwfData

        // Mock resource helper calls
        whenever(rh.gs(any<Int>(), any())).thenReturn("mocked")
        whenever(versionCheckerUtils.versionDigits(any())).thenReturn(intArrayOf(1, 0, 0))

        sut.showCwfInfos()
        assertThat(sut.uiState.value.showInfos).isTrue()
        assertThat(sut.uiState.value.cwfInfosState).isNotNull()

        sut.hideCwfInfos()
        assertThat(sut.uiState.value.showInfos).isFalse()
    }
}
