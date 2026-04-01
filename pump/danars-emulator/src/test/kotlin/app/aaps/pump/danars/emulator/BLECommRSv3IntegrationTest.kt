package app.aaps.pump.danars.emulator

import android.content.Context
import android.content.Intent
import android.util.Base64
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Integration tests for BLEComm with RSv3 encryption.
 *
 * Tests the full stack: BLEComm → BleEncryption (RSv3) → EmulatorBleTransport → PumpEmulator.
 * RSv3 uses second-level encryption with pairing keys after the handshake.
 */
class BLECommRSv3IntegrationTest : TestBase() {

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
    private lateinit var mockedBase64: MockedStatic<Base64>

    private val deviceName = "UHH00002TI"
    private val deviceAddress = "00:11:22:33:44:55"

    // RSv3 pairing keys (must match PumpState defaults)
    private val v3PairingKey = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
    private val v3RandomPairingKey = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
    private val v3RandomSyncKey: Byte = 0x42

    @BeforeEach
    fun setup() {
        // Mock android.util.Base64 to delegate to java.util.Base64
        mockedBase64 = Mockito.mockStatic(Base64::class.java)
        mockedBase64.`when`<ByteArray> { Base64.decode(any<String>(), anyInt()) }.thenAnswer { invocation ->
            java.util.Base64.getDecoder().decode(invocation.getArgument<String>(0))
        }
        mockedBase64.`when`<String> { Base64.encodeToString(any<ByteArray>(), anyInt()) }.thenAnswer { invocation ->
            java.util.Base64.getEncoder().encodeToString(invocation.getArgument<ByteArray>(0))
        }

        whenever(rh.gs(anyInt())).thenReturn("test")
        whenever(rh.gs(anyInt(), any())).thenReturn("test")
        whenever(preferences.get(any<DanaStringNonKey>())).thenReturn("")
        whenever(preferences.get(any<StringComposedNonPreferenceKey>(), any())).thenReturn("")
        whenever(preferences.get(DanaStringNonKey.Password)).thenReturn("0000")
        whenever(danaRSPlugin.mDeviceName).thenReturn(deviceName)
        whenever(constraintsChecker.applyBolusConstraints(any<Constraint<Double>>())).thenAnswer { it.arguments[0] }

        // Provide RSv3 pairing keys so handshake completes without PIN entry
        val encoder = java.util.Base64.getEncoder()
        whenever(preferences.get(DanaStringComposedKey.V3ParingKey, deviceName))
            .thenReturn(encoder.encodeToString(v3PairingKey))
        whenever(preferences.get(DanaStringComposedKey.V3RandomParingKey, deviceName))
            .thenReturn(encoder.encodeToString(v3RandomPairingKey))
        whenever(preferences.get(DanaStringComposedKey.V3RandomSyncKey, deviceName))
            .thenReturn(String.format("%02x", v3RandomSyncKey))

        // Suppress context.startActivity calls (EnterPinActivity)
        whenever(context.startActivity(any<Intent>())).then { }

        bleEncryption = BleEncryption()
        emulatorTransport = EmulatorBleTransport(
            deviceName = deviceName,
            encryptionType = EncryptionType.ENCRYPTION_RSv3
        ).apply {
            pairingDelayMs = 0
            emulator.historyEventDelayMs = 0
        }
        // Set hwModel to Dana RS (0x05) for RSv3
        emulatorTransport.pumpState.hwModel = 0x05
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

    @AfterEach
    fun tearDown() {
        mockedBase64.close()
    }

    private fun connectAndHandshake() {
        val result = bleComm.connect("test", deviceAddress)
        assertThat(result).isTrue()
        assertThat(bleComm.isConnected).isTrue()
        assertThat(bleComm.isConnecting).isFalse()
    }

    // ========== Connection & Handshake ==========

    @Test
    fun `connect completes RSv3 handshake and sets connected`() {
        connectAndHandshake()
    }

    @Test
    fun `RSv3 handshake sets ignoreUserPassword`() {
        connectAndHandshake()
        assertThat(danaPump.ignoreUserPassword).isTrue()
    }

    @Test
    fun `RSv3 handshake reads hwModel from pump`() {
        connectAndHandshake()
        assertThat(danaPump.hwModel).isEqualTo(0x05)
    }

    // ========== Commands after RSv3 handshake ==========

    @Test
    fun `getProfileNumber works with RSv3 encryption`() {
        connectAndHandshake()

        emulatorTransport.pumpState.activeProfileNumber = 3
        val packet = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.activeProfile).isEqualTo(3)
    }

    @Test
    fun `initialScreenInformation works with RSv3 encryption`() {
        connectAndHandshake()

        val state = emulatorTransport.pumpState
        state.reservoirRemainingUnits = 200.0
        state.batteryRemaining = 60
        state.currentBasal = 1.5

        val packet = DanaRSPacketGeneralInitialScreenInformation(aapsLogger, danaPump)
        bleComm.sendMessage(packet)

        assertThat(packet.isReceived).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(danaPump.reservoirRemainingUnits).isWithin(0.01).of(200.0)
        assertThat(danaPump.batteryRemaining).isEqualTo(60)
    }

    @Test
    fun `temp basal set and cancel with RSv3 encryption`() {
        connectAndHandshake()

        val setPacket = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(150)
        bleComm.sendMessage(setPacket)
        assertThat(setPacket.isReceived).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isTrue()

        val cancelPacket = DanaRSPacketBasalSetCancelTemporaryBasal(aapsLogger)
        bleComm.sendMessage(cancelPacket)
        assertThat(cancelPacket.isReceived).isTrue()
        assertThat(emulatorTransport.pumpState.isTempBasalRunning).isFalse()
    }

    @Test
    fun `multiple commands work in sequence with RSv3`() {
        connectAndHandshake()

        // Command 1: Get profile
        emulatorTransport.pumpState.activeProfileNumber = 1
        val profilePacket = DanaRSPacketBasalGetProfileNumber(aapsLogger, danaPump)
        bleComm.sendMessage(profilePacket)
        assertThat(profilePacket.isReceived).isTrue()

        // Command 2: Set temp basal
        val tempPacket = DanaRSPacketAPSBasalSetTemporaryBasal(aapsLogger).with(120)
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
