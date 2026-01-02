package app.aaps.pump.danaR.services

import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.pump.danar.comm.MsgBolusStop
import app.aaps.pump.danar.services.AbstractDanaRExecutionService
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AbstractDanaRExecutionServiceTest : TestBaseWithProfile() {

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var messageHashTable: MessageHashTableBase
    @Mock lateinit var bluetoothManager: BluetoothManager
    @Mock lateinit var pumpEnactResult: PumpEnactResult

    lateinit var danaPump: DanaPump

    init {
        addInjector { injector ->
            if (injector is MsgBolusStop) {
                injector.aapsLogger = aapsLogger
            }
        }
    }

    private lateinit var testService: TestDanaRExecutionService

    inner class TestDanaRExecutionService : AbstractDanaRExecutionService() {
        override fun messageHashTable(): MessageHashTableBase = messageHashTable
        override fun updateBasalsInPump(profile: Profile): Boolean = true
        override fun getPumpStatus() {}
        override fun loadEvents(): PumpEnactResult? = pumpEnactResult
        override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean = true
        override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean = false
        override fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean = false
        override fun tempBasal(percent: Int, durationInHours: Int): Boolean = true
        override fun tempBasalStop(): Boolean = true
        override fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean = true
        override fun extendedBolusStop(): Boolean = true
        override fun setUserOptions(): PumpEnactResult? = pumpEnactResult
    }

    @BeforeEach
    fun setup() {
        `when`(rh.gs(anyInt())).thenReturn("test string")
        `when`(rh.gs(anyInt(), any())).thenReturn("test string")

        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
        testService = TestDanaRExecutionService()
        testService.aapsLogger = aapsLogger
        testService.rxBus = rxBus
        testService.preferences = preferences
        testService.context = context
        testService.rh = rh
        testService.danaPump = danaPump
        testService.fabricPrivacy = fabricPrivacy
        testService.dateUtil = dateUtil
        testService.aapsSchedulers = aapsSchedulers
        testService.pumpSync = pumpSync
        testService.activePlugin = activePlugin
        testService.uiInteraction = uiInteraction
        testService.pumpEnactResultProvider = pumpEnactResultProvider
        testService.injector = injector
    }

    @Test
    fun testIsConnected() {
        assertThat(testService.isConnected).isFalse()
    }

    @Test
    fun testIsHandshakeInProgress() {
        assertThat(testService.isHandshakeInProgress).isFalse()
    }

    @Test
    fun testDisconnect() {
        testService.disconnect("test")
        // Verify no crash when mSerialIOThread is null
    }

    @Test
    fun testStopConnecting() {
        testService.stopConnecting()
        // Verify no crash when mSerialIOThread is null
    }

    @Test
    fun testBolusStop_notConnected() {
        testService.bolusStop()

        assertThat(danaPump.bolusStopForced).isTrue()
        assertThat(danaPump.bolusStopped).isTrue()
    }

    @Test
    fun testLoadHistory_notConnected() {
        val result = testService.loadHistory(RecordTypes.RECORD_TYPE_BOLUS)

        assertThat(result).isNotNull()
    }

    @Test
    fun testLoadHistory_withDifferentTypes() {
        // Test all record types
        val types = arrayOf(
            RecordTypes.RECORD_TYPE_ALARM,
            RecordTypes.RECORD_TYPE_BASALHOUR,
            RecordTypes.RECORD_TYPE_BOLUS,
            RecordTypes.RECORD_TYPE_CARBO,
            RecordTypes.RECORD_TYPE_DAILY,
            RecordTypes.RECORD_TYPE_ERROR,
            RecordTypes.RECORD_TYPE_GLUCOSE,
            RecordTypes.RECORD_TYPE_REFILL,
            RecordTypes.RECORD_TYPE_SUSPEND
        )

        types.forEach { type ->
            val result = testService.loadHistory(type)
            assertThat(result).isNotNull()
        }
    }

    @Test
    fun testDoSanityCheck_temporaryBasalMismatch() {
        val temporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        `when`(temporaryBasal.rate).thenReturn(150.0)
        `when`(temporaryBasal.timestamp).thenReturn(1000000L)

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(temporaryBasal, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Set underlying properties to make isTempBasalInProgress true
        danaPump.tempBasalPercent = 100
        danaPump.tempBasalStart = 1000000L
        danaPump.tempBasalDuration = 1800000L // 30 minutes in milliseconds
        `when`(dateUtil.now()).thenReturn(1500000L) // Within the temp basal range
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        // Verify that synchronization was attempted
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_noTemporaryBasalInAAPSButInPump() {
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(null, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Set underlying properties to make isTempBasalInProgress true
        danaPump.tempBasalPercent = 120
        danaPump.tempBasalStart = 1000000L
        danaPump.tempBasalDuration = 1800000L // 30 minutes in milliseconds
        `when`(dateUtil.now()).thenReturn(1500000L) // Within the temp basal range
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        // Verify notification was added
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_temporaryBasalInAAPSButNotInPump() {
        val temporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        // Set dateUtil.now() first, before checking isTempBasalInProgress
        `when`(dateUtil.now()).thenReturn(2000000L)

        `when`(temporaryBasal.rate).thenReturn(150.0)
        `when`(temporaryBasal.timestamp).thenReturn(1000000L)

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(temporaryBasal, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Ensure pump shows no temp basal (tempBasalStart = 0 by default)
        danaPump.isTempBasalInProgress = false
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        // Verify synchronization
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_extendedBolusMismatch() {
        val extendedBolus = mock(PumpSync.PumpState.ExtendedBolus::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.Pump::class.java)

        // Set dateUtil.now() FIRST to ensure it's available for all property getters
        `when`(dateUtil.now()).thenReturn(1500000L) // Within the extended bolus range

        `when`(extendedBolus.rate).thenReturn(1.5)
        `when`(extendedBolus.timestamp).thenReturn(500000L) // Make timestamp different enough

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(null, extendedBolus, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Set underlying properties to make isExtendedInProgress true
        danaPump.extendedBolusStart = 1000000L
        danaPump.extendedBolusAmount = 3.0
        danaPump.extendedBolusDuration = 7200000L // 120 minutes in milliseconds
        danaPump.extendedBolusAbsoluteRate = 2.0 // Different rate to trigger mismatch

        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")
        `when`(activePump.isFakingTempsByExtendedBoluses).thenReturn(false)

        testService.doSanityCheck()

        // Verify notification
        verify(uiInteraction).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testDoSanityCheck_allMatch() {
        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(null, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        danaPump.isTempBasalInProgress = false
        danaPump.isExtendedInProgress = false
        `when`(dateUtil.now()).thenReturn(2000000L)

        testService.doSanityCheck()

        // Verify no notifications when everything matches
        verify(uiInteraction, never()).addNotification(anyInt(), anyString(), anyInt())
    }

    @Test
    fun testGetBTSocketForSelectedPump_noBluetoothAdapter() {
        `when`(preferences.get(DanaStringKey.RName)).thenReturn("TestPump")
        `when`(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(bluetoothManager)
        `when`(bluetoothManager.adapter).thenReturn(null)

        testService.getBTSocketForSelectedPump()

        // Should handle null adapter gracefully
    }
}
