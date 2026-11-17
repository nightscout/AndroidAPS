package app.aaps.pump.carelevo.ble.data

import app.aaps.pump.carelevo.ble.data.DeviceModuleState.Companion.codeToDeviceResult
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Pure-logic coverage for the Carelevo BLE data layer:
 *  - CarelevoBleEnums.kt   (the adapter state code lookup)
 *  - CarelevoBleModels.kt  (BleState and its one predicate)
 *
 * The rest of the old stack's types went with it; Bluetooth v2 carries the link state now.
 */
internal class CarelevoBleDataTest {

    @Test
    fun `BondingState enumerates all four values`() {
        assertThat(BondingState.entries).containsExactly(
            BondingState.BOND_NONE, BondingState.BOND_BONDING, BondingState.BOND_BONDED, BondingState.BOND_ERROR
        ).inOrder()
        assertThat(BondingState.valueOf("BOND_ERROR")).isEqualTo(BondingState.BOND_ERROR)
    }

    @Test
    fun `PeripheralConnectionState enumerates all five values`() {
        assertThat(PeripheralConnectionState.entries).hasSize(5)
    }

    @Test
    fun `ServiceDiscoverState enumerates all four values`() {
        assertThat(ServiceDiscoverState.entries).hasSize(4)
    }

    @Test
    fun `NotificationState enumerates all three values`() {
        assertThat(NotificationState.entries).hasSize(3)
    }

    // ---------------------------------------------------------------------------------------------
    // DeviceModuleState.codeToDeviceResult — the Android adapter state broadcast
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `device code lookups map to the right state`() {
        assertThat((-1).codeToDeviceResult()).isEqualTo(DeviceModuleState.DEVICE_NONE)
        assertThat(10.codeToDeviceResult()).isEqualTo(DeviceModuleState.DEVICE_STATE_OFF)
        assertThat(11.codeToDeviceResult()).isEqualTo(DeviceModuleState.DEVICE_STATE_TURNING_ON)
        assertThat(12.codeToDeviceResult()).isEqualTo(DeviceModuleState.DEVICE_STATE_ON)
        assertThat(13.codeToDeviceResult()).isEqualTo(DeviceModuleState.DEVICE_STATE_TUNING_OFF)
    }

    @Test
    fun `invalid device code throws`() {
        assertFailsWith<IllegalArgumentException> { 14.codeToDeviceResult() }
        assertFailsWith<IllegalArgumentException> { 0.codeToDeviceResult() }
    }

    @Test
    fun `DeviceModuleState enumerates all five values`() {
        assertThat(DeviceModuleState.entries).hasSize(5)
    }

    // ---------------------------------------------------------------------------------------------
    // BleState
    // ---------------------------------------------------------------------------------------------

    private fun state(
        enabled: DeviceModuleState = DeviceModuleState.DEVICE_STATE_ON,
        bonded: BondingState = BondingState.BOND_BONDED,
        discovered: ServiceDiscoverState = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED,
        connected: PeripheralConnectionState = PeripheralConnectionState.CONN_STATE_CONNECTED,
        notification: NotificationState = NotificationState.NOTIFICATION_ENABLED
    ) = BleState(
        isEnabled = enabled,
        isBonded = bonded,
        isServiceDiscovered = discovered,
        isConnected = connected,
        isNotificationEnabled = notification
    )

    @Test
    fun `isAvailable is true unless the module is off`() {
        assertThat(state(enabled = DeviceModuleState.DEVICE_STATE_ON).isAvailable()).isTrue()
        assertThat(state(enabled = DeviceModuleState.DEVICE_NONE).isAvailable()).isTrue()
        assertThat(state(enabled = DeviceModuleState.DEVICE_STATE_TURNING_ON).isAvailable()).isTrue()
        assertThat(state(enabled = DeviceModuleState.DEVICE_STATE_OFF).isAvailable()).isFalse()
    }

    @Test
    fun `BleState supports value semantics`() {
        assertThat(state()).isEqualTo(state())
        assertThat(state().copy(isBonded = BondingState.BOND_NONE)).isNotEqualTo(state())
    }
}
