package app.aaps.pump.danars.emulator

import android.content.Context
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileStore
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
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketEtcKeepConnection
import app.aaps.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.pump.danars.encryption.EncryptionType
import app.aaps.pump.danars.services.BLEComm
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Integration tests for BLEComm with BLE5 encryption.
 *
 * Tests the full stack: BLEComm → BleEncryption (BLE5) → EmulatorBleTransport → PumpEmulator.
 * BLE5 uses a simple 3-byte key derived from a 6-digit pairing key sent during PUMP_CHECK.
 */
class BLECommBLE5IntegrationTest : TestBase() {

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
    private val ble5PairingKey = "474632"

    @BeforeEach
    fun setup() {
        whenever(rh.gs(anyInt())).thenReturn("test")
        whenever(rh.gs(anyInt(), any())).thenReturn("test")
        whenever(preferences.get(any<DanaStringNonKey>())).thenReturn("")
        whenever(preferences.get(any<StringComposedNonPreferenceKey>(), any())).thenReturn("")
        whenever(preferences.get(DanaStringNonKey.Password)).thenReturn("0000")
        whenever(danaRSPlugin.mDeviceName).thenReturn(deviceName)
        whenever(constraintsChecker.applyBolusConstraints(any<Constraint<Double>>())).thenAnswer { it.arguments[0] }

        // Provide stored BLE5 pairing key
        whenever(preferences.get(DanaStringComposedKey.Ble5PairingKey, deviceName))
            .thenReturn(ble5PairingKey)

        bleEncryption = BleEncryption()
        emulatorTransport = EmulatorBleTransport(
            deviceName = deviceName,
            encryptionType = EncryptionType.ENCRYPTION_BLE5
        ).apply {
            pairingDelayMs = 0
            emulator.historyEventDelayMs = 0
        }
        // Set hwModel to Dana-i (0x09) for BLE5
        emulatorTransport.pumpState.hwModel = 0x09
        emulatorTransport.pumpState.ble5PairingKey = ble5PairingKey
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)

        bleComm = BLEComm(
            aapsLogger, rh, context, rxBus,
            danaRSMessageHashTable, danaPump, danaRSPlugin, bleEncryption,
            pumpSync, dateUtil, preferences, configBuilder, notificationManager,
            emulatorTransport
        ).apply {
            messageTimeoutMs = 500
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
    fun `connect completes BLE5 handshake and sets connected`() {
        connectAndHandshake()
    }

    @Test
    fun `BLE5 handshake sets ignoreUserPassword`() {
        connectAndHandshake()
        assertThat(danaPump.ignoreUserPassword).isTrue()
    }

    @Test
    fun `BLE5 handshake reads hwModel from pump`() {
        connectAndHandshake()
        assertThat(danaPump.hwModel).isEqualTo(0x09)
    }

    // ========== Commands after BLE5 handshake ==========

    @Test
    fun `getProfileNumber works with BLE5 encryption`() {
        connectAndHandshake()

        emulatorTransport.pumpState.activeProfileNumber = 2
        val packet = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.activeProfile).isEqualTo(2)
    }

    @Test
    fun `initialScreenInformation works with BLE5 encryption`() {
        connectAndHandshake()

        val state = emulatorTransport.pumpState
        state.reservoirRemainingUnits = 175.0
        state.batteryRemaining = 95
        state.currentBasal = 0.75

        val packet = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.01).of(175.0)
        assertThat(danaPump.batteryRemaining).isEqualTo(95)
    }

    @Test
    fun `temp basal set and cancel with BLE5 encryption`() {
        connectAndHandshake()

        val setPacket = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(180)
        bleComm.sendMessage(setPacket)
        assertThat(setPacket.isReceived).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()

        val cancelPacket = DanaRSPacketBasalSetCancelTemporaryBasal(aapsLogger)
        bleComm.sendMessage(cancelPacket)
        assertThat(cancelPacket.isReceived).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isFalse()
    }

    @Test
    fun `multiple commands work in sequence with BLE5`() {
        connectAndHandshake()

        // Command 1: Get profile
        emulatorTransport.pumpState.activeProfileNumber = 0
        val profilePacket = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(profilePacket)
        assertThat(profilePacket.isReceived).isTrue()

        // Command 2: Set temp basal
        val tempPacket = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(110)
        bleComm.sendMessage(tempPacket)
        assertThat(tempPacket.isReceived).isTrue()

        // Command 3: Screen info
        val screenPacket = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(screenPacket)
        assertThat(screenPacket.isReceived).isTrue()
        assertThat(screenPacket.isTempBasalInProgress).isTrue()

        // Command 4: Keep connection
        val keepPacket = DanaRSPacketEtcKeepConnection(aapsLogger)
        bleComm.sendMessage(keepPacket)
        assertThat(keepPacket.isReceived).isTrue()

        // Command 5: Pump time
        val timePacket = DanaRSPacketOptionGetPumpTime(aapsLogger, dateUtil, danaPump)
        bleComm.sendMessage(timePacket)
        assertThat(timePacket.isReceived).isTrue()
    }
}
