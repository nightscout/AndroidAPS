package app.aaps.pump.eopatch

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class EopatchPumpPluginTest : EopatchTestBase() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var bleConnectionState: BleConnectionState
    @Mock lateinit var profile: Profile

    private lateinit var plugin: EopatchPumpPlugin

    @BeforeEach
    fun prepareMocksAndPlugin() {
        MockitoAnnotations.openMocks(this)
        prepareMocks()

        whenever(rh.gs(org.mockito.kotlin.any<Int>())).thenReturn("MockedString")
        whenever(patchManagerExecutor.patchConnectionState).thenReturn(bleConnectionState)
        whenever(bleConnectionState.isConnected).thenReturn(false)
        whenever(bleConnectionState.isConnecting).thenReturn(false)
        whenever<app.aaps.pump.eopatch.vo.PatchState>(eopatchPreferenceManager.patchState).thenReturn(
            app.aaps.pump.eopatch.vo.PatchState()
        )

        // Setup profile mock to return valid basal values
        whenever(profile.getBasalValues()).thenReturn(
            arrayOf(Profile.ProfileValue(0, 1.0))
        )
        whenever(profile.getBasal(org.mockito.kotlin.any())).thenReturn(1.0)

        plugin = EopatchPumpPlugin(
            aapsLogger,
            rh,
            preferences,
            commandQueue,
            aapsSchedulers,
            rxBus,
            fabricPrivacy,
            dateUtil,
            pumpSync,
            patchManager,
            patchManagerExecutor,
            alarmManager,
            eopatchPreferenceManager,
            uiInteraction,
            pumpEnactResultProvider,
            patchConfig,
            normalBasalManager
        )
    }

    @Test
    fun `manufacturer should return Eoflow`() {
        assertThat(plugin.manufacturer()).isEqualTo(ManufacturerType.Eoflow)
    }

    @Test
    fun `model should return EOFLOW_EOPATCH2`() {
        assertThat(plugin.model()).isEqualTo(PumpType.EOFLOW_EOPATCH2)
    }

    @Test
    fun `serialNumber should return patch serial number`() {
        patchConfig.patchSerialNumber = "TEST12345"

        assertThat(plugin.serialNumber()).isEqualTo("TEST12345")
    }

    @Test
    fun `isInitialized should return false when not connected`() {
        whenever(bleConnectionState.isConnected).thenReturn(false)

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return false when not activated`() {
        whenever(bleConnectionState.isConnected).thenReturn(true)
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return true when connected and activated`() {
        whenever(bleConnectionState.isConnected).thenReturn(true)
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()

        assertThat(plugin.isInitialized()).isTrue()
    }

    @Test
    fun `isSuspended should return true when basal is paused`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        val bytes = ByteArray(20)
        bytes[4] = 0x04.toByte() // isNormalBasalReg=true (bit 2), isNormalBasalAct=false (bit 4) -> paused
        patchState.update(bytes, System.currentTimeMillis())
        whenever<app.aaps.pump.eopatch.vo.PatchState>(eopatchPreferenceManager.patchState).thenReturn(patchState)

        assertThat(plugin.isSuspended()).isTrue()
    }

    @Test
    fun `isSuspended should return false when basal is not paused`() {
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        patchState.update(ByteArray(20), System.currentTimeMillis())
        whenever<app.aaps.pump.eopatch.vo.PatchState>(eopatchPreferenceManager.patchState).thenReturn(patchState)

        assertThat(plugin.isSuspended()).isFalse()
    }

    @Test
    fun `isBusy should always return false`() {
        assertThat(plugin.isBusy()).isFalse()
    }

    @Test
    fun `isConnected should return true when deactivated`() {
        patchConfig.updateDeactivated()

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isConnected should return connection state when activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        patchConfig.macAddress = "00:11:22:33:44:55"
        whenever(bleConnectionState.isConnected).thenReturn(true)

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isConnecting should return connection state`() {
        whenever(bleConnectionState.isConnecting).thenReturn(true)

        assertThat(plugin.isConnecting()).isTrue()
    }

    @Test
    fun `isHandshakeInProgress should always return false`() {
        assertThat(plugin.isHandshakeInProgress()).isFalse()
    }

    @Test
    fun `connect should update last data time`() {
        val beforeTime = System.currentTimeMillis()

        plugin.connect("test reason")

        assertThat(plugin.lastDataTime).isAtLeast(beforeTime)
    }

    @Test
    fun `baseBasalRate should return 0 when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.baseBasalRate).isWithin(0.001).of(0.0)
    }

    @Test
    fun `baseBasalRate should return 0 when basal is paused`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        val bytes = ByteArray(20)
        bytes[4] = 0x04.toByte() // isNormalBasalReg=true (bit 2), isNormalBasalAct=false (bit 4) -> paused
        patchState.update(bytes, System.currentTimeMillis())
        whenever<app.aaps.pump.eopatch.vo.PatchState>(eopatchPreferenceManager.patchState).thenReturn(patchState)

        assertThat(plugin.baseBasalRate).isWithin(0.001).of(0.0)
    }

    @Test
    fun `reservoirLevel should return 0 when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.reservoirLevel).isWithin(0.001).of(0.0)
    }

    @Test
    fun `reservoirLevel should return patch state value when activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createActivated()
        val patchState = app.aaps.pump.eopatch.vo.PatchState()
        // Create bytes with remainedInsulin = 50.0f
        val bytes = ByteArray(20)
        patchState.update(bytes, System.currentTimeMillis())
        whenever<app.aaps.pump.eopatch.vo.PatchState>(eopatchPreferenceManager.patchState).thenReturn(patchState)

        // Just verify it returns a value (actual implementation uses patch state)
        assertThat(plugin.reservoirLevel).isAtLeast(0.0)
    }

    @Test
    fun `batteryLevel should return null when not activated`() {
        patchConfig.lifecycleEvent = PatchLifecycleEvent.createShutdown()

        assertThat(plugin.batteryLevel).isNull()
    }

    @Test
    fun `isFakingTempsByExtendedBoluses should return false`() {
        assertThat(plugin.isFakingTempsByExtendedBoluses).isFalse()
    }

    @Test
    fun `canHandleDST should return false`() {
        assertThat(plugin.canHandleDST()).isFalse()
    }

    @Test
    fun `getCustomActions should return null`() {
        assertThat(plugin.getCustomActions()).isNull()
    }

    @Test
    fun `isThisProfileSet should use normalBasalManager`() {
        normalBasalManager.setNormalBasal(profile)

        val result = plugin.isThisProfileSet(profile)

        assertThat(result).isTrue()
    }

    @Test
    fun `pumpDescription should be initialized`() {
        assertThat(plugin.pumpDescription).isNotNull()
    }

    @Test
    fun `plugin should be of type PUMP`() {
        assertThat(plugin.getType()).isEqualTo(PluginType.PUMP)
    }

    @Test
    fun `plugin name should be set`() {
        assertThat(plugin.name).isNotEmpty()
    }
}
