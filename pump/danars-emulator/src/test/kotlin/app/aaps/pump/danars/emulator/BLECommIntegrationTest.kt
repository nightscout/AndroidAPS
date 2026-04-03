package app.aaps.pump.danars.emulator

import android.content.Context
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSMessageHashTable
import app.aaps.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketAPSHistoryEvents
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBolusGet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetBolusOption
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCalculationInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetStepBolusInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolus
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolusCancel
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStart
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import app.aaps.pump.danars.comm.DanaRSPacketEtcKeepConnection
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetPumpCheck
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetShippingInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetUserOption
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetUserOption
import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.pump.danars.services.BLEComm
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Integration tests for BLEComm + EmulatorBleTransport.
 *
 * Tests the full stack: BLEComm → BleEncryption → EmulatorBleTransport → PumpEmulator.
 * No Android framework needed — runs as pure JVM tests.
 *
 * The EmulatorBleTransport processes packets synchronously, so the full
 * encryption handshake completes within connect() and each sendMessage()
 * returns immediately with the response already parsed.
 */
class BLECommIntegrationTest : TestBase() {

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
    @Mock lateinit var profileStoreProvider: javax.inject.Provider<ProfileStore>
    @Mock lateinit var constraintsChecker: ConstraintsChecker
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage

    private lateinit var danaPump: DanaPump
    private lateinit var bleEncryption: BleEncryption
    private lateinit var emulatorTransport: EmulatorBleTransport
    private lateinit var bleComm: BLEComm

    private val deviceName = "UHH00002TI"
    private val deviceAddress = "00:11:22:33:44:55"

    @BeforeEach
    fun setup() {
        // Mock basics
        whenever(rh.gs(anyInt())).thenReturn("test")
        whenever(rh.gs(anyInt(), any())).thenReturn("test")
        whenever(preferences.get(any<DanaStringNonKey>())).thenReturn("")
        whenever(preferences.get(any<StringComposedNonPreferenceKey>(), any())).thenReturn("")
        // Pump password "0000" matches emulator default
        whenever(preferences.get(DanaStringNonKey.Password)).thenReturn("0000")
        // Provide a stored pairing key so handshake skips pairing request
        whenever(preferences.get(DanaStringComposedKey.ParingKey, deviceName)).thenReturn("ABCD")
        whenever(danaRSPlugin.mDeviceName).thenReturn(deviceName)
        // Constraint checker passes through the value unchanged
        whenever(constraintsChecker.applyBolusConstraints(any<Constraint<Double>>())).thenAnswer { it.arguments[0] }

        // Real instances (no Android deps)
        bleEncryption = BleEncryption()
        emulatorTransport = EmulatorBleTransport(deviceName = deviceName).apply {
            pumpState.bolusDeliveryIntervalMs = 0 // Instant delivery in tests
            pairingDelayMs = 0
            emulator.historyEventDelayMs = 0
        }
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)

        bleComm = BLEComm(
            aapsLogger,
            rh,
            context,
            rxBus,
            danaRSMessageHashTable,
            danaPump,
            danaRSPlugin,
            bleEncryption,
            pumpSync,
            dateUtil,
            preferences,
            configBuilder,
            notificationManager,
            emulatorTransport
        ).apply {
            messageTimeoutMs = 500
        }
    }

    @AfterEach
    fun tearDown() {
        if (::bleComm.isInitialized && bleComm.isConnected) {
            bleComm.disconnect("test cleanup")
        }
        if (::emulatorTransport.isInitialized) {
            emulatorTransport.awaitPendingCallbacks()
        }
    }

    private fun connectAndHandshake() {
        val result = bleComm.connect("test", deviceAddress)
        assertThat(result).isTrue()
        assertThat(bleComm.isConnected).isTrue()
        assertThat(bleComm.isConnecting).isFalse()
    }

    // ========== Connection & Handshake ==========

    @Test
    fun `connect completes v1 handshake and sets connected`() {
        connectAndHandshake()
    }

    @Test
    fun `password is extracted correctly from handshake`() {
        connectAndHandshake()
        assertThat(danaPump.rsPassword).isEqualTo("0000")
    }

    // ========== Query Commands ==========

    @Test
    fun `getProfileNumber reads active profile from emulator`() {
        connectAndHandshake()

        emulatorTransport.pumpState.activeProfileNumber = 2
        val packet = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.activeProfile).isEqualTo(2)
    }

    @Test
    fun `initialScreenInformation reads pump status`() {
        connectAndHandshake()

        val state = emulatorTransport.pumpState
        state.reservoirRemainingUnits = 123.45
        state.batteryRemaining = 75
        state.dailyTotalUnits = 12.34
        state.currentBasal = 0.85
        state.iob = 2.5

        val packet = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.01).of(123.45)
        assertThat(danaPump.batteryRemaining).isEqualTo(75)
        assertThat(danaPump.dailyTotalUnits).isWithin(0.01).of(12.34)
        assertThat(danaPump.currentBasal).isWithin(0.01).of(0.85)
        assertThat(danaPump.iob).isWithin(0.01).of(2.5)
    }

    @Test
    fun `getBasalRate reads basal profile from emulator`() {
        connectAndHandshake()

        val state = emulatorTransport.pumpState
        state.activeProfileNumber = 0
        danaPump.activeProfile = 0
        for (i in 0 until 24) {
            state.basalProfiles[0][i] = 0.5 + i * 0.1
        }

        val packet = DanaRSPacketBasalGetBasalRate(aapsLogger, notificationManager, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.maxBasal).isWithin(0.01).of(state.maxBasal)
        assertThat(danaPump.basalStep).isWithin(0.001).of(0.01)
        assertThat(danaPump.pumpProfiles!![0][0]).isWithin(0.01).of(0.5)
        assertThat(danaPump.pumpProfiles!![0][23]).isWithin(0.01).of(0.5 + 23 * 0.1)
    }

    @Test
    fun `getPumpTime reads pump time`() {
        connectAndHandshake()

        val packet = DanaRSPacketOptionGetPumpTime(aapsLogger, dateUtil, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.pumpTime).isGreaterThan(0)
    }

    @Test
    fun `keepConnection succeeds`() {
        connectAndHandshake()

        val packet = DanaRSPacketEtcKeepConnection(aapsLogger)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test
    fun `getShippingInformation reads serial number and country`() {
        connectAndHandshake()

        emulatorTransport.pumpState.serialNumber = "AAA12345BB"
        emulatorTransport.pumpState.shippingCountry = "INT"

        val packet = DanaRSPacketGeneralGetShippingInformation(aapsLogger, dateUtil, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.serialNumber).isEqualTo("AAA12345BB")
        assertThat(danaPump.shippingCountry).isEqualTo("INT")
    }

    @Test
    fun `getPumpCheck reads hardware model and protocol`() {
        connectAndHandshake()

        emulatorTransport.pumpState.hwModel = 0x05
        emulatorTransport.pumpState.protocol = 10
        emulatorTransport.pumpState.productCode = 2

        val packet = DanaRSPacketGeneralGetPumpCheck(aapsLogger, danaPump, notificationManager)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.hwModel).isEqualTo(0x05)
        assertThat(danaPump.protocol).isEqualTo(10)
        assertThat(danaPump.productCode).isEqualTo(2)
    }

    @Test
    fun `getUserOption reads pump settings`() {
        connectAndHandshake()

        val state = emulatorTransport.pumpState
        state.lcdOnTimeSec = 10
        state.backlightOnTimeSec = 8
        state.batteryRemaining = 90
        state.lowReservoirRate = 15
        state.cannulaVolume = 20
        state.units = 0 // mg/dL

        val packet = DanaRSPacketOptionGetUserOption(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.lcdOnTimeSec).isEqualTo(10)
        assertThat(danaPump.backlightOnTimeSec).isEqualTo(8)
        assertThat(danaPump.lowReservoirRate).isEqualTo(15)
        assertThat(danaPump.cannulaVolume).isEqualTo(20)
        assertThat(danaPump.units).isEqualTo(0)
    }

    @Test
    fun `getBolusStepInformation reads bolus settings`() {
        connectAndHandshake()

        emulatorTransport.pumpState.maxBolus = 15.0
        emulatorTransport.pumpState.bolusStep = 0.05
        emulatorTransport.pumpState.lastBolusAmount = 3.5

        val packet = DanaRSPacketBolusGetStepBolusInformation(aapsLogger, dateUtil, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.maxBolus).isWithin(0.01).of(15.0)
        assertThat(danaPump.bolusStep).isWithin(0.001).of(0.05)
        assertThat(danaPump.lastBolusAmount).isWithin(0.01).of(3.5)
    }

    // ========== Temp Basal Flow ==========

    @Test
    fun `setTemporaryBasal changes emulator state`() {
        connectAndHandshake()

        val packet = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(150)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()
        assertThat(emulatorTransport.pumpState.tempBasalPercent).isEqualTo(150)
    }

    @Test
    fun `cancelTemporaryBasal clears emulator state`() {
        connectAndHandshake()

        val setPacket = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(200)
        bleComm.sendMessage(setPacket)
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()

        val cancelPacket = DanaRSPacketBasalSetCancelTemporaryBasal(aapsLogger)
        bleComm.sendMessage(cancelPacket)

        assertThat(cancelPacket.isReceived).isTrue()
        assertThat(cancelPacket.failed).isFalse()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isFalse()
    }

    @Test
    fun `temp basal reflected in initialScreenInformation`() {
        connectAndHandshake()

        // Set temp basal
        val setPacket = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(130)
        bleComm.sendMessage(setPacket)

        // Verify via screen info
        val screenPacket = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(screenPacket)

        assertThat(screenPacket.isReceived).isTrue()
        assertThat(screenPacket.isTempBasalInProgress).isTrue()
    }

    // ========== Bolus Flow ==========

    @Test
    fun `bolusStart delivers bolus to emulator`() {
        connectAndHandshake()

        val packet = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump, constraintsChecker)
            .with(amount = 2.5, speed = 0)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.bolusStartErrorCode).isEqualTo(0)
    }

    @Test
    fun `bolusStop succeeds`() {
        connectAndHandshake()

        // Start a bolus first
        val startPacket = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump, constraintsChecker)
            .with(amount = 1.0, speed = 0)
        bleComm.sendMessage(startPacket)

        // Stop the bolus
        val stopPacket = DanaRSPacketBolusSetStepBolusStop(aapsLogger, BolusProgressData(), rh, danaPump)
        bleComm.sendMessage(stopPacket)

        assertThat(stopPacket.isReceived).isTrue()
        assertThat(stopPacket.failed).isFalse()
        assertThat(danaPump.bolusStopped).isTrue()
    }

    // ========== Extended Bolus Flow ==========

    @Test
    fun `extendedBolus sets emulator state`() {
        connectAndHandshake()

        val packet = DanaRSPacketBolusSetExtendedBolus(aapsLogger)
            .with(extendedAmount = 2.0, extendedBolusDurationInHalfHours = 4)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(emulatorTransport.pumpState.isExtendedBolusRunning).isTrue()
        assertThat(emulatorTransport.pumpState.extendedBolusAmount).isWithin(0.01).of(2.0)
        assertThat(emulatorTransport.pumpState.extendedBolusDurationHalfHours).isEqualTo(4)
    }

    @Test
    fun `extendedBolusCancel clears emulator state`() {
        connectAndHandshake()

        // Start extended bolus
        val setPacket = DanaRSPacketBolusSetExtendedBolus(aapsLogger)
            .with(extendedAmount = 1.5, extendedBolusDurationInHalfHours = 6)
        bleComm.sendMessage(setPacket)
        assertThat(emulatorTransport.pumpState.isExtendedBolusRunning).isTrue()

        // Cancel it
        val cancelPacket = DanaRSPacketBolusSetExtendedBolusCancel(aapsLogger)
        bleComm.sendMessage(cancelPacket)

        assertThat(cancelPacket.isReceived).isTrue()
        assertThat(cancelPacket.failed).isFalse()
        assertThat(emulatorTransport.pumpState.isExtendedBolusRunning).isFalse()
    }

    @Test
    fun `extendedBolus reflected in initialScreenInformation`() {
        connectAndHandshake()

        val setPacket = DanaRSPacketBolusSetExtendedBolus(aapsLogger)
            .with(extendedAmount = 1.0, extendedBolusDurationInHalfHours = 2)
        bleComm.sendMessage(setPacket)

        val screenPacket = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(screenPacket)

        assertThat(screenPacket.isReceived).isTrue()
        assertThat(screenPacket.isExtendedInProgress).isTrue()
    }

    // ========== Profile Update Flow ==========

    @Test
    fun `setProfileBasalRate uploads profile to emulator`() {
        connectAndHandshake()

        val rates = Array(24) { 0.5 + it * 0.05 }
        val packet = DanaRSPacketBasalSetProfileBasalRate(aapsLogger)
            .with(profileNumber = 1, profileBasalRate = rates)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        // Verify emulator stored the profile
        assertThat(emulatorTransport.pumpState.basalProfiles[1][0]).isWithin(0.01).of(0.5)
        assertThat(emulatorTransport.pumpState.basalProfiles[1][23]).isWithin(0.01).of(0.5 + 23 * 0.05)
    }

    @Test
    fun `setProfileNumber activates profile on emulator`() {
        connectAndHandshake()

        val packet = DanaRSPacketBasalSetProfileNumber(aapsLogger).with(profileNumber = 2)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(emulatorTransport.pumpState.activeProfileNumber).isEqualTo(2)
    }

    @Test
    fun `profile upload and activate round-trip`() {
        connectAndHandshake()

        // Upload new rates to profile 1
        val rates = Array(24) { 1.0 }
        rates[0] = 0.8
        rates[12] = 1.2
        val uploadPacket = DanaRSPacketBasalSetProfileBasalRate(aapsLogger)
            .with(profileNumber = 1, profileBasalRate = rates)
        bleComm.sendMessage(uploadPacket)
        assertThat(uploadPacket.failed).isFalse()

        // Activate profile 1
        val activatePacket = DanaRSPacketBasalSetProfileNumber(aapsLogger).with(profileNumber = 1)
        bleComm.sendMessage(activatePacket)
        assertThat(activatePacket.failed).isFalse()

        // Read back active profile number
        val getNumPacket = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(getNumPacket)
        assertThat(danaPump.activeProfile).isEqualTo(1)

        // Read back basal rates
        danaPump.activeProfile = 1
        val getRatePacket = DanaRSPacketBasalGetBasalRate(aapsLogger, notificationManager, danaPump)
        bleComm.sendMessage(getRatePacket)
        assertThat(getRatePacket.failed).isFalse()
        assertThat(danaPump.pumpProfiles!![1][0]).isWithin(0.01).of(0.8)
        assertThat(danaPump.pumpProfiles!![1][12]).isWithin(0.01).of(1.2)
    }

    // ========== History Loading ==========

    @Test
    fun `historyEvents with no history returns done`() {
        connectAndHandshake()

        // Emulator has no history events by default (historyDone=true)
        val packet = DanaRSPacketAPSHistoryEvents(
            aapsLogger, dateUtil, rxBus, rh, danaPump,
            detailedBolusInfoStorage, temporaryBasalStorage, preferences, pumpSync
        ).with(0L)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(danaPump.historyDoneReceived).isTrue()
    }

    // ========== Multi-step Flows ==========

    @Test
    fun `multiple commands work in sequence after handshake`() {
        connectAndHandshake()

        // Get profile number
        emulatorTransport.pumpState.activeProfileNumber = 1
        val profilePacket = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(profilePacket)
        assertThat(profilePacket.isReceived).isTrue()
        assertThat(danaPump.activeProfile).isEqualTo(1)

        // Set temp basal
        val tempPacket = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(120)
        bleComm.sendMessage(tempPacket)
        assertThat(tempPacket.isReceived).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()

        // Get initial screen (should reflect temp basal)
        val screenPacket = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(screenPacket)
        assertThat(screenPacket.isReceived).isTrue()
        assertThat(screenPacket.isTempBasalInProgress).isTrue()

        // Cancel temp basal
        val cancelPacket = DanaRSPacketBasalSetCancelTemporaryBasal(aapsLogger)
        bleComm.sendMessage(cancelPacket)
        assertThat(cancelPacket.isReceived).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isFalse()

        // Keep connection
        val keepPacket = DanaRSPacketEtcKeepConnection(aapsLogger)
        bleComm.sendMessage(keepPacket)
        assertThat(keepPacket.isReceived).isTrue()
    }

    @Test
    fun `full readPumpStatus-like sequence`() {
        connectAndHandshake()

        // Mirrors DanaRSService.readPumpStatus() packet sequence
        val state = emulatorTransport.pumpState
        state.reservoirRemainingUnits = 100.0
        state.batteryRemaining = 85
        state.currentBasal = 1.0
        state.activeProfileNumber = 0
        state.maxBolus = 10.0
        state.bolusStep = 0.05

        // 1. Initial screen info
        val screen = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(screen)
        assertThat(screen.isReceived).isTrue()
        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.01).of(100.0)

        // 2. Shipping info
        val shipping = DanaRSPacketGeneralGetShippingInformation(aapsLogger, dateUtil, danaPump)
        bleComm.sendMessage(shipping)
        assertThat(shipping.isReceived).isTrue()

        // 3. Pump check
        val check = DanaRSPacketGeneralGetPumpCheck(aapsLogger, danaPump, notificationManager)
        bleComm.sendMessage(check)
        assertThat(check.isReceived).isTrue()

        // 4. Get profile number
        val profileNum = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(profileNum)
        assertThat(profileNum.isReceived).isTrue()

        // 5. Get basal rate
        val basalRate = DanaRSPacketBasalGetBasalRate(aapsLogger, notificationManager, danaPump)
        bleComm.sendMessage(basalRate)
        assertThat(basalRate.isReceived).isTrue()

        // 6. Get bolus info
        val bolusInfo = DanaRSPacketBolusGetStepBolusInformation(aapsLogger, dateUtil, danaPump)
        bleComm.sendMessage(bolusInfo)
        assertThat(bolusInfo.isReceived).isTrue()
        assertThat(danaPump.maxBolus).isWithin(0.01).of(10.0)

        // 7. Get user options
        val userOpt = DanaRSPacketOptionGetUserOption(aapsLogger, danaPump)
        bleComm.sendMessage(userOpt)
        assertThat(userOpt.isReceived).isTrue()

        // 8. Get pump time
        val pumpTime = DanaRSPacketOptionGetPumpTime(aapsLogger, dateUtil, danaPump)
        bleComm.sendMessage(pumpTime)
        assertThat(pumpTime.isReceived).isTrue()
        assertThat(danaPump.pumpTime).isGreaterThan(0)
    }

    // ========== 0% Coverage Packets ==========

    @Test
    fun `get24CIRCFArray reads 24-hour CIR and CF from emulator`() {
        connectAndHandshake()

        // Set emulator CIR/CF values (IntArray — pump stores integers)
        val state = emulatorTransport.pumpState
        state.units = DanaPump.UNITS_MGDL
        for (i in 0..23) {
            state.cir24Values[i] = 10 + i
            state.cf24Values[i] = 30 + i * 2
        }

        val packet = DanaRSPacketBolusGet24CIRCFArray(aapsLogger)
        packet.danaPump = danaPump  // field injection
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.units).isEqualTo(DanaPump.UNITS_MGDL)
        assertThat(danaPump.cir24[0]).isWithin(0.01).of(10.0)
        assertThat(danaPump.cir24[23]).isWithin(0.01).of(33.0)
        assertThat(danaPump.cf24[0]).isWithin(0.01).of(30.0)
        assertThat(danaPump.cf24[23]).isWithin(0.01).of(76.0)
    }

    @Test
    fun `getBolusOption reads extended bolus enabled flag`() {
        connectAndHandshake()

        emulatorTransport.pumpState.isExtendedEnabled = true

        val packet = DanaRSPacketBolusGetBolusOption(aapsLogger, notificationManager, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.isExtendedBolusEnabled).isTrue()
    }

    @Test
    fun `getCalculationInformation reads target values`() {
        connectAndHandshake()

        val state = emulatorTransport.pumpState
        state.units = DanaPump.UNITS_MGDL
        state.currentTarget = 120

        val packet = DanaRSPacketBolusGetCalculationInformation(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test
    fun `getCIRCFArray reads non-24h CIR and CF`() {
        connectAndHandshake()

        val state = emulatorTransport.pumpState
        state.units = DanaPump.UNITS_MGDL

        val packet = DanaRSPacketBolusGetCIRCFArray(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test
    fun `getPumpUTCAndTimeZone reads UTC time and zone offset`() {
        // hwModel >= 7 enables UTC mode on DanaPump
        emulatorTransport.pumpState.hwModel = 7
        emulatorTransport.pumpState.zoneOffset = 3
        connectAndHandshake()

        // Read pump check to set hwModel on danaPump
        val pumpCheck = DanaRSPacketGeneralGetPumpCheck(aapsLogger, danaPump, notificationManager)
        bleComm.sendMessage(pumpCheck)
        assertThat(danaPump.hwModel).isEqualTo(7)
        assertThat(danaPump.usingUTC).isTrue()

        val packet = DanaRSPacketOptionGetPumpUTCAndTimeZone(aapsLogger, dateUtil)
        packet.danaPump = danaPump  // field injection
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.pumpTime).isGreaterThan(0)
        assertThat(danaPump.zoneOffset).isEqualTo(3)
    }

    @Test
    fun `setPumpUTCAndTimeZone sets time on emulator`() {
        connectAndHandshake()

        val now = System.currentTimeMillis()
        val packet = DanaRSPacketOptionSetPumpUTCAndTimeZone(aapsLogger, dateUtil).with(now, 2)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(emulatorTransport.pumpState.zoneOffset).isEqualTo(2)
    }

    @Test
    fun `setUserOption writes user settings to emulator`() {
        connectAndHandshake()

        danaPump.lcdOnTimeSec = 15
        danaPump.backlightOnTimeSec = 10
        danaPump.cannulaVolume = 20
        danaPump.lowReservoirRate = 10
        danaPump.selectedLanguage = 1

        val packet = DanaRSPacketOptionSetUserOption(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        // Verify emulator received the settings
        assertThat(emulatorTransport.pumpState.lcdOnTimeSec).isEqualTo(15)
        assertThat(emulatorTransport.pumpState.backlightOnTimeSec).isEqualTo(10)
    }

}
