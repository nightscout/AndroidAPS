package app.aaps.pump.danars.services

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import app.aaps.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetUserOption
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class DanaRSServiceTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var bleComm: BLEComm
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var danaRSPacketGeneralInitialScreenInformationProvider: () -> DanaRSPacketGeneralInitialScreenInformation
    @Mock lateinit var danaRSPacketOptionSetUserOptionProvider: () -> DanaRSPacketOptionSetUserOption
    @Mock lateinit var danaRSPacketBolusSetStepBolusStopProvider: () -> DanaRSPacketBolusSetStepBolusStop
    @Mock lateinit var danaRSPacketAPSBasalSetTemporaryBasalProvider: () -> DanaRSPacketAPSBasalSetTemporaryBasal
    @Mock lateinit var danaRSPacketBasalSetCancelTemporaryBasalProvider: () -> DanaRSPacketBasalSetCancelTemporaryBasal
    @Mock lateinit var packetGeneralInitialScreenInfo: DanaRSPacketGeneralInitialScreenInformation
    @Mock lateinit var packetOptionSetUserOption: DanaRSPacketOptionSetUserOption
    @Mock lateinit var packetBolusSetStepBolusStop: DanaRSPacketBolusSetStepBolusStop
    @Mock lateinit var packetAPSBasalSetTemporaryBasal: DanaRSPacketAPSBasalSetTemporaryBasal
    @Mock lateinit var packetBasalSetCancelTemporaryBasal: DanaRSPacketBasalSetCancelTemporaryBasal

    private lateinit var danaRSService: DanaRSService

    @BeforeEach
    fun setup() {
        danaRSService = DanaRSService()
        danaRSService.aapsLogger = aapsLogger
        danaRSService.rxBus = rxBus
        danaRSService.preferences = preferences
        danaRSService.rh = rh
        //danaRSService.profileFunction = profileFunction
        danaRSService.commandQueue = commandQueue
        danaRSService.context = context
        danaRSService.danaRSPlugin = danaRSPlugin
        danaRSService.danaPump = danaPump
        danaRSService.activePlugin = activePlugin
        danaRSService.uiInteraction = uiInteraction
        danaRSService.bleComm = bleComm
        danaRSService.pumpSync = pumpSync
        danaRSService.dateUtil = dateUtil
        danaRSService.bolusProgressData = BolusProgressData(ch, CoroutineScope(Dispatchers.Unconfined))
        danaRSService.detailedBolusInfoStorage = detailedBolusInfoStorage
        danaRSService.pumpEnactResultProvider = pumpEnactResultProvider
        danaRSService.danaRSPacketGeneralInitialScreenInformation = danaRSPacketGeneralInitialScreenInformationProvider
        danaRSService.danaRSPacketOptionSetUserOption = danaRSPacketOptionSetUserOptionProvider
        danaRSService.danaRSPacketBolusSetStepBolusStop = danaRSPacketBolusSetStepBolusStopProvider
        danaRSService.danaRSPacketAPSBasalSetTemporaryBasal = danaRSPacketAPSBasalSetTemporaryBasalProvider
        danaRSService.danaRSPacketBasalSetCancelTemporaryBasal = danaRSPacketBasalSetCancelTemporaryBasalProvider

        `when`(rh.gs(anyInt())).thenReturn("test string")
        `when`(rh.gs(anyInt(), any())).thenReturn("test string")
        `when`(activePlugin.activePumpInternal).thenReturn(danaRSPlugin)
        `when`(danaRSPlugin.pumpDescription).thenReturn(mockPumpDescription())

        // Setup packet providers
        `when`(danaRSPacketGeneralInitialScreenInformationProvider()).thenReturn(packetGeneralInitialScreenInfo)
        `when`(danaRSPacketOptionSetUserOptionProvider()).thenReturn(packetOptionSetUserOption)
        `when`(danaRSPacketBolusSetStepBolusStopProvider()).thenReturn(packetBolusSetStepBolusStop)
        `when`(danaRSPacketAPSBasalSetTemporaryBasalProvider()).thenReturn(packetAPSBasalSetTemporaryBasal)
        `when`(danaRSPacketBasalSetCancelTemporaryBasalProvider()).thenReturn(packetBasalSetCancelTemporaryBasal)

        // Setup packet behavior
        `when`(packetGeneralInitialScreenInfo.failed).thenReturn(true)
        `when`(packetOptionSetUserOption.success()).thenReturn(true)
        `when`(packetAPSBasalSetTemporaryBasal.with(anyInt())).thenReturn(packetAPSBasalSetTemporaryBasal)
        `when`(packetAPSBasalSetTemporaryBasal.success()).thenReturn(false)
    }

    @Test
    fun testIsConnected() {
        `when`(bleComm.isConnected).thenReturn(false)
        assertThat(danaRSService.isConnected).isFalse()

        `when`(bleComm.isConnected).thenReturn(true)
        assertThat(danaRSService.isConnected).isTrue()
    }

    @Test
    fun testIsConnecting() {
        `when`(bleComm.isConnecting).thenReturn(false)
        assertThat(danaRSService.isConnecting).isFalse()

        `when`(bleComm.isConnecting).thenReturn(true)
        assertThat(danaRSService.isConnecting).isTrue()
    }

    @Test
    fun testConnect() {
        `when`(bleComm.connect(anyString(), anyString())).thenReturn(true)

        val result = danaRSService.connect("test", "00:11:22:33:44:55")

        assertThat(result).isTrue()
    }

    @Test
    fun testDisconnect() {
        danaRSService.disconnect("test")
        // Should not throw exception
    }

    @Test
    fun testStopConnecting() {
        danaRSService.stopConnecting()
        // Should not throw exception
    }

    @Test
    fun testLoadEvents_notInitialized() {
        `when`(danaRSPlugin.isInitialized()).thenReturn(false)

        val result = danaRSService.loadEvents()

        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("pump not initialized")
    }

    @Test
    fun testSetUserSettings() {
        val result = danaRSService.setUserSettings()

        assertThat(result).isNotNull()
    }

    @Test
    fun testBolus_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = 5.0

        val result = danaRSService.bolus(detailedBolusInfo)

        assertThat(result).isFalse()
    }

    @Test
    fun `stopped bolus info gets the real end time`() {
        // Plugin stored 6 U with the estimated end (start + 6 U * 12 s), the bolus was stopped earlier
        val estimatedEnd = 1_790_885_933_000L
        val realEnd = 1_790_885_865_000L
        val stored = DetailedBolusInfo().apply { insulin = 6.0; timestamp = estimatedEnd; bolusType = BS.Type.SMB }
        `when`(detailedBolusInfoStorage.findDetailedBolusInfo(estimatedEnd, 6.0)).thenReturn(stored)

        danaRSService.updateStoredBolusTime(stored, realEnd)

        val captor = argumentCaptor<DetailedBolusInfo>()
        verify(detailedBolusInfoStorage).add(captor.capture())
        assertThat(captor.firstValue.timestamp).isEqualTo(realEnd)
        assertThat(captor.firstValue.insulin).isEqualTo(6.0)
        assertThat(captor.firstValue.bolusType).isEqualTo(BS.Type.SMB)
        // The caller's object keeps its time
        assertThat(stored.timestamp).isEqualTo(estimatedEnd)
    }

    @Test
    fun `bolus info not found is not stored again`() {
        val info = DetailedBolusInfo().apply { insulin = 1.0; timestamp = 1_000_000L }

        danaRSService.updateStoredBolusTime(info, 2_000_000L)

        verify(detailedBolusInfoStorage).findDetailedBolusInfo(1_000_000L, 1.0)
        verifyNoMoreInteractions(detailedBolusInfoStorage)
    }

    @Test
    fun testBolusStop() {
        `when`(bleComm.isConnected).thenReturn(false)

        danaRSService.bolusStop()

        // Should not throw exception
    }

    @Test
    fun testTempBasal_notConnected() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasal(120, 1)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalStop_notConnected() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasalStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_invalidDuration() = runTest {
        val result = danaRSService.tempBasalShortDuration(120, 20)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_validDuration15() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasalShortDuration(120, 15)

        // Returns false because not connected
        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_validDuration30() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.tempBasalShortDuration(120, 30)

        // Returns false because not connected
        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolus_notConnected() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.extendedBolus(2.0, 2)

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolusStop_notConnected() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.extendedBolusStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testUpdateBasalsInPump_notConnected() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)
        `when`(profileFunction.getProfile()).thenReturn(effectiveProfile)

        val result = danaRSService.updateBasalsInPump(validProfile)

        assertThat(result).isFalse()
    }

    @Test
    fun testLoadHistory_notConnected() {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.loadHistory(RecordTypes.RECORD_TYPE_BOLUS)

        assertThat(result).isNotNull()
        assertThat(result.success).isFalse()
    }

    @Test
    fun testLoadHistory_allTypes() {
        `when`(bleComm.isConnected).thenReturn(false)

        val types = arrayOf(
            RecordTypes.RECORD_TYPE_ALARM,
            RecordTypes.RECORD_TYPE_PRIME,
            RecordTypes.RECORD_TYPE_BASALHOUR,
            RecordTypes.RECORD_TYPE_BOLUS,
            RecordTypes.RECORD_TYPE_CARBO,
            RecordTypes.RECORD_TYPE_DAILY,
            RecordTypes.RECORD_TYPE_GLUCOSE,
            RecordTypes.RECORD_TYPE_REFILL,
            RecordTypes.RECORD_TYPE_SUSPEND
        )

        types.forEach { type ->
            val result = danaRSService.loadHistory(type)
            assertThat(result).isNotNull()
        }
    }

    @Test
    fun testHighTempBasal() = runTest {
        `when`(bleComm.isConnected).thenReturn(false)

        val result = danaRSService.highTempBasal(150)

        // Returns false when status fails (not connected)
        assertThat(result).isFalse()
    }

    private fun mockPumpDescription(): app.aaps.core.data.pump.defs.PumpDescription {
        return app.aaps.core.data.pump.defs.PumpDescription().apply {
            basalStep = 0.01
        }
    }
}
