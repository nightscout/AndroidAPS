package app.aaps.pump.danars.emulator

import android.content.Context
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSMessageHashTable
import app.aaps.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketAPSHistoryEvents
import app.aaps.pump.danars.comm.DanaRSPacketAPSSetEventHistory
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBolusGet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetBolusOption
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCalculationInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetStepBolusInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusSet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolus
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolusCancel
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStart
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import app.aaps.pump.danars.comm.DanaRSPacketEtcKeepConnection
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetPumpCheck
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetShippingInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralSetHistoryUploadMode
import app.aaps.pump.danars.comm.DanaRSPacketHistoryAlarm
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBasal
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBloodGlucose
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBolus
import app.aaps.pump.danars.comm.DanaRSPacketHistoryCarbohydrate
import app.aaps.pump.danars.comm.DanaRSPacketHistoryDaily
import app.aaps.pump.danars.comm.DanaRSPacketHistoryPrime
import app.aaps.pump.danars.comm.DanaRSPacketHistoryRefill
import app.aaps.pump.danars.comm.DanaRSPacketHistorySuspend
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetUserOption
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetUserOption
import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.pump.danars.services.BLEComm
import app.aaps.pump.danars.services.DanaRSService
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import javax.inject.Provider

/**
 * Integration tests for DanaRSService through the full emulator stack.
 *
 * Tests the complete path: DanaRSService → BLEComm → BleEncryption → EmulatorBleTransport → PumpEmulator.
 * This gives real end-to-end coverage of service methods including packet construction,
 * encryption, and response parsing — much higher validity than mocked unit tests.
 */
class DanaRSServiceIntegrationTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var danaRSMessageHashTable: DanaRSMessageHashTable
    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var decimalFormatter: DecimalFormatter
    @Mock lateinit var profileStoreProvider: Provider<ProfileStore>
    @Mock lateinit var constraintsChecker: ConstraintsChecker
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Mock lateinit var pumpEnactResult: PumpEnactResult
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var pumpWithConcentration: PumpWithConcentration

    private lateinit var danaPump: DanaPump
    private lateinit var bleEncryption: BleEncryption
    private lateinit var emulatorTransport: EmulatorBleTransport
    private lateinit var bleComm: BLEComm
    private lateinit var danaRSService: DanaRSService

    private val deviceName = "UHH00002TI"
    private val deviceAddress = "00:11:22:33:44:55"

    @BeforeEach
    fun setup() {
        // Mock basics
        whenever(rh.gs(anyInt())).thenReturn("test")
        whenever(rh.gs(anyInt(), any())).thenReturn("test")
        whenever(preferences.get(any<DanaStringKey>())).thenReturn("")
        whenever(preferences.get(any<StringComposedNonPreferenceKey>(), any())).thenReturn("")
        whenever(preferences.get(DanaStringKey.Password)).thenReturn("0000")
        whenever(preferences.get(DanaStringComposedKey.ParingKey, deviceName)).thenReturn("ABCD")
        whenever(danaRSPlugin.mDeviceName).thenReturn(deviceName)
        whenever(constraintsChecker.applyBolusConstraints(any<Constraint<Double>>())).thenAnswer { it.arguments[0] }
        whenever(pumpWithConcentration.pumpDescription).thenReturn(
            app.aaps.core.data.pump.defs.PumpDescription().apply { basalStep = 0.01 }
        )
        whenever(activePlugin.activePump).thenReturn(pumpWithConcentration)
        whenever(danaRSPlugin.pumpDescription).thenReturn(
            app.aaps.core.data.pump.defs.PumpDescription().apply { basalStep = 0.01 }
        )
        whenever(dateUtil.now()).thenReturn(System.currentTimeMillis())
        whenever(dateUtil.dateAndTimeAndSecondsString(any())).thenReturn("2026-01-01 12:00:00")

        // PumpSync returns empty expected state
        val emptyState = PumpSync.PumpState(null, null, null, null, "")
        kotlinx.coroutines.runBlocking { whenever(pumpSync.expectedPumpState()).thenReturn(emptyState) }

        // PumpEnactResult
        whenever(pumpEnactResult.success(any())).thenReturn(pumpEnactResult)
        whenever(pumpEnactResult.success).thenReturn(true)

        // Real instances
        bleEncryption = BleEncryption()
        emulatorTransport = EmulatorBleTransport(deviceName = deviceName)
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)

        bleComm = BLEComm(
            aapsLogger, rh, context, rxBus, danaRSMessageHashTable, danaPump,
            danaRSPlugin, bleEncryption, pumpSync, dateUtil, preferences,
            configBuilder, notificationManager, emulatorTransport
        )

        // Create service and wire all dependencies
        danaRSService = DanaRSService()
        danaRSService.aapsLogger = aapsLogger
        danaRSService.aapsSchedulers = aapsSchedulers
        danaRSService.rxBus = rxBus
        danaRSService.preferences = preferences
        danaRSService.rh = rh
        danaRSService.commandQueue = commandQueue
        danaRSService.context = context
        danaRSService.danaRSPlugin = danaRSPlugin
        danaRSService.danaPump = danaPump
        danaRSService.activePlugin = activePlugin
        danaRSService.constraintChecker = constraintsChecker
        danaRSService.uiInteraction = uiInteraction
        danaRSService.bleComm = bleComm
        danaRSService.fabricPrivacy = fabricPrivacy
        danaRSService.pumpSync = pumpSync
        danaRSService.dateUtil = dateUtil
        danaRSService.pumpEnactResultProvider = Provider { pumpEnactResult }
        danaRSService.notificationManager = notificationManager

        // Wire all packet providers with real instances
        danaRSService.danaRSPacketEtcKeepConnection = Provider { DanaRSPacketEtcKeepConnection(aapsLogger) }
        danaRSService.danaRSPacketGeneralGetShippingInformation = Provider { DanaRSPacketGeneralGetShippingInformation(aapsLogger, dateUtil, danaPump) }
        danaRSService.danaRSPacketGeneralGetPumpCheck = Provider { DanaRSPacketGeneralGetPumpCheck(aapsLogger, danaPump, notificationManager) }
        danaRSService.danaRSPacketBasalGetProfileNumber = Provider { DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump) }
        danaRSService.danaRSPacketBolusGetBolusOption = Provider { DanaRSPacketBolusGetBolusOption(aapsLogger, notificationManager, danaPump) }
        danaRSService.danaRSPacketBasalGetBasalRate = Provider { DanaRSPacketBasalGetBasalRate(aapsLogger, notificationManager, danaPump) }
        danaRSService.danaRSPacketBolusGetCalculationInformation = Provider { DanaRSPacketBolusGetCalculationInformation(aapsLogger, danaPump) }
        danaRSService.danaRSPacketBolusGet24CIRCFArray = Provider {
            DanaRSPacketBolusGet24CIRCFArray(aapsLogger).also { it.danaPump = danaPump }
        }
        danaRSService.danaRSPacketBolusGetCIRCFArray = Provider { DanaRSPacketBolusGetCIRCFArray(aapsLogger, danaPump) }
        danaRSService.danaRSPacketOptionGetUserOption = Provider { DanaRSPacketOptionGetUserOption(aapsLogger, danaPump) }
        danaRSService.danaRSPacketGeneralInitialScreenInformation = Provider { DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump) }
        danaRSService.danaRSPacketBolusGetStepBolusInformation = Provider { DanaRSPacketBolusGetStepBolusInformation(aapsLogger, dateUtil, danaPump) }
        danaRSService.danaRSPacketOptionGetPumpTime = Provider { DanaRSPacketOptionGetPumpTime(aapsLogger, dateUtil, danaPump) }
        danaRSService.danaRSPacketOptionGetPumpUTCAndTimeZone = Provider {
            DanaRSPacketOptionGetPumpUTCAndTimeZone(aapsLogger, dateUtil).also { it.danaPump = danaPump }
        }
        danaRSService.danaRSPacketOptionSetPumpTime = Provider { DanaRSPacketOptionSetPumpTime(aapsLogger, dateUtil) }
        danaRSService.danaRSPacketOptionSetPumpUTCAndTimeZone = Provider { DanaRSPacketOptionSetPumpUTCAndTimeZone(aapsLogger, dateUtil) }
        danaRSService.danaRSPacketOptionSetUserOption = Provider { DanaRSPacketOptionSetUserOption(aapsLogger, danaPump) }
        danaRSService.danaRSPacketAPSHistoryEvents = Provider {
            DanaRSPacketAPSHistoryEvents(aapsLogger, dateUtil, rxBus, rh, danaPump, detailedBolusInfoStorage, temporaryBasalStorage, preferences, pumpSync)
        }
        danaRSService.danaRSPacketAPSSetEventHistory = Provider { DanaRSPacketAPSSetEventHistory(aapsLogger, dateUtil, danaPump) }
        danaRSService.danaRSPacketBasalSetCancelTemporaryBasal = Provider { DanaRSPacketBasalSetCancelTemporaryBasal(aapsLogger) }
        danaRSService.danaRSPacketBasalSetTemporaryBasal = Provider { DanaRSPacketBasalSetTemporaryBasal(aapsLogger) }
        danaRSService.danaRSPacketAPSBasalSetTemporaryBasal = Provider { DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger) }
        danaRSService.danaRSPacketBolusSetExtendedBolus = Provider { DanaRSPacketBolusSetExtendedBolus(aapsLogger) }
        danaRSService.danaRSPacketBolusSetExtendedBolusCancel = Provider { DanaRSPacketBolusSetExtendedBolusCancel(aapsLogger) }
        danaRSService.danaRSPacketBolusSetStepBolusStart = Provider { DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump, constraintsChecker) }
        danaRSService.danaRSPacketBolusSetStepBolusStop = Provider { DanaRSPacketBolusSetStepBolusStop(aapsLogger, rxBus, rh, danaPump) }
        danaRSService.danaRSPacketBasalSetProfileBasalRate = Provider { DanaRSPacketBasalSetProfileBasalRate(aapsLogger) }
        danaRSService.danaRSPacketBasalSetProfileNumber = Provider { DanaRSPacketBasalSetProfileNumber(aapsLogger) }
        danaRSService.danaRSPacketGeneralSetHistoryUploadMode = Provider { DanaRSPacketGeneralSetHistoryUploadMode(aapsLogger) }
        danaRSService.danaRSPacketBolusSet24CIRCFArray = Provider { DanaRSPacketBolusSet24CIRCFArray(aapsLogger, danaPump, profileUtil) }
        danaRSService.danaRSPacketHistoryAlarm = Provider { DanaRSPacketHistoryAlarm(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistoryBasal = Provider { DanaRSPacketHistoryBasal(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistoryBloodGlucose = Provider { DanaRSPacketHistoryBloodGlucose(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistoryBolus = Provider { DanaRSPacketHistoryBolus(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistoryCarbohydrate = Provider { DanaRSPacketHistoryCarbohydrate(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistoryDaily = Provider { DanaRSPacketHistoryDaily(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistoryPrime = Provider { DanaRSPacketHistoryPrime(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistoryRefill = Provider { DanaRSPacketHistoryRefill(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
        danaRSService.danaRSPacketHistorySuspend = Provider { DanaRSPacketHistorySuspend(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) }
    }

    private fun connectAndHandshake() {
        val result = bleComm.connect("test", deviceAddress)
        assertThat(result).isTrue()
        assertThat(bleComm.isConnected).isTrue()
    }

    // ========== readPumpStatus ==========

    @Test
    fun `readPumpStatus reads all pump data through emulator`() {
        val state = emulatorTransport.pumpState
        state.reservoirRemainingUnits = 123.0
        state.batteryRemaining = 75
        state.currentBasal = 0.8
        state.dailyTotalUnits = 15.5
        state.maxBolus = 10.0
        state.bolusStep = 0.05
        state.activeProfileNumber = 0

        // Not initialized → loadEvents is skipped (returns early), avoiding SystemClock.sleep hang
        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        connectAndHandshake()
        danaRSService.readPumpStatus()

        // Verify pump data was read (all packets before loadEvents)
        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.01).of(123.0)
        assertThat(danaPump.batteryRemaining).isEqualTo(75)
        assertThat(danaPump.currentBasal).isWithin(0.01).of(0.8)
        assertThat(danaPump.dailyTotalUnits).isWithin(0.01).of(15.5)
        assertThat(danaPump.maxBolus).isWithin(0.01).of(10.0)
        assertThat(danaPump.pumpTime).isGreaterThan(0)
        assertThat(danaPump.lastConnection).isGreaterThan(0)
    }

    @Test
    fun `readPumpStatus with profile24 reads 24-hour CIR-CF`() {
        // hwModel >= 7 enables profile24
        emulatorTransport.pumpState.hwModel = 7
        emulatorTransport.pumpState.profile24 = true
        for (i in 0..23) {
            emulatorTransport.pumpState.cir24Values[i] = 12 + i
            emulatorTransport.pumpState.cf24Values[i] = 40 + i
        }

        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        connectAndHandshake()
        danaRSService.readPumpStatus()

        // Should have used Get24CIRCFArray path
        assertThat(danaPump.cir24[0]).isWithin(0.01).of(12.0)
        assertThat(danaPump.cir24[23]).isWithin(0.01).of(35.0)
        assertThat(danaPump.cf24[0]).isWithin(0.01).of(40.0)
    }

    // ========== tempBasal ==========

    @Test
    fun `tempBasal sets temporary basal on emulator`() {
        // Not initialized so loadEvents returns early (avoids SystemClock.sleep hang in test)
        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        connectAndHandshake()
        val result = danaRSService.tempBasal(150, 1)

        assertThat(result).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()
        assertThat(emulatorTransport.pumpState.tempBasalPercent).isEqualTo(150)
    }

    @Test
    fun `tempBasal cancels existing temp basal before setting new one`() {
        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        emulatorTransport.pumpState.isTempBasalRunning = true
        emulatorTransport.pumpState.tempBasalPercent = 120

        connectAndHandshake()
        val result = danaRSService.tempBasal(200, 2)

        assertThat(result).isTrue()
        assertThat(emulatorTransport.pumpState.tempBasalPercent).isEqualTo(200)
    }

    // ========== highTempBasal ==========

    @Test
    fun `highTempBasal sets APS temporary basal`() {
        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        connectAndHandshake()
        val result = danaRSService.highTempBasal(250)

        assertThat(result).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()
    }

    // ========== tempBasalStop ==========

    @Test
    fun `tempBasalStop cancels running temp basal`() {
        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        emulatorTransport.pumpState.isTempBasalRunning = true
        emulatorTransport.pumpState.tempBasalPercent = 150

        connectAndHandshake()
        val result = danaRSService.tempBasalStop()

        assertThat(result).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isFalse()
    }

    // ========== extendedBolus ==========

    @Test
    fun `extendedBolus sets extended bolus on emulator`() {
        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        connectAndHandshake()
        val result = danaRSService.extendedBolus(2.0, 4)

        assertThat(result).isTrue()
        assertThat(emulatorTransport.pumpState.isExtendedBolusRunning).isTrue()
        assertThat(emulatorTransport.pumpState.extendedBolusAmount).isWithin(0.01).of(2.0)
        assertThat(emulatorTransport.pumpState.extendedBolusDurationHalfHours).isEqualTo(4)
    }

    // ========== extendedBolusStop ==========

    @Test
    fun `extendedBolusStop cancels extended bolus`() {
        whenever(danaRSPlugin.isInitialized()).thenReturn(false)

        emulatorTransport.pumpState.isExtendedBolusRunning = true
        emulatorTransport.pumpState.extendedBolusAmount = 1.5

        connectAndHandshake()
        val result = danaRSService.extendedBolusStop()

        assertThat(result).isTrue()
        assertThat(emulatorTransport.pumpState.isExtendedBolusRunning).isFalse()
    }

    // ========== setUserSettings ==========

    @Test
    fun `setUserSettings sends user options to emulator`() {
        connectAndHandshake()

        danaPump.lcdOnTimeSec = 20
        danaPump.backlightOnTimeSec = 15
        danaPump.lowReservoirRate = 10

        val result = danaRSService.setUserSettings()

        assertThat(result.success).isTrue()
        assertThat(emulatorTransport.pumpState.lcdOnTimeSec).isEqualTo(20)
        assertThat(emulatorTransport.pumpState.backlightOnTimeSec).isEqualTo(15)
    }

    // ========== loadEvents ==========

    @Test
    fun `loadEvents completes with no history`() {
        whenever(danaRSPlugin.isInitialized()).thenReturn(true)

        connectAndHandshake()
        val result = danaRSService.loadEvents()

        assertThat(result.success).isTrue()
    }

    // ========== loadHistory ==========

    @Test
    fun `loadHistory sends correct history packet for each type`() {
        connectAndHandshake()

        // Each history type should work through the emulator
        val types = listOf(
            RecordTypes.RECORD_TYPE_ALARM,
            RecordTypes.RECORD_TYPE_BOLUS,
            RecordTypes.RECORD_TYPE_DAILY,
            RecordTypes.RECORD_TYPE_PRIME
        )

        types.forEach { type ->
            val result = danaRSService.loadHistory(type)
            assertThat(result).isNotNull()
        }
    }

    // ========== tempBasalShortDuration ==========

    @Test
    fun `tempBasalShortDuration with 15 min sets APS temp basal`() {
        whenever(danaRSPlugin.isInitialized()).thenReturn(true)

        connectAndHandshake()
        val result = danaRSService.tempBasalShortDuration(130, 15)

        assertThat(result).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()
    }

    @Test
    fun `tempBasalShortDuration with invalid duration returns false`() {
        val result = danaRSService.tempBasalShortDuration(130, 20)
        assertThat(result).isFalse()
    }
}
