package app.aaps.pump.carelevo.ble.data

import android.bluetooth.BluetoothDevice
import app.aaps.pump.carelevo.ble.data.BondingState.Companion.codeToBondingResult
import app.aaps.pump.carelevo.ble.data.DeviceModuleState.Companion.codeToDeviceResult
import app.aaps.pump.carelevo.ble.data.NotificationState.Companion.codeToNotificationResult
import app.aaps.pump.carelevo.ble.data.PeripheralConnectionState.Companion.codeToConnectionResult
import app.aaps.pump.carelevo.ble.data.ServiceDiscoverState.Companion.codeToDiscoverResult
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Pure-logic coverage for the Carelevo BLE data layer:
 *  - CarelevoBleEnums.kt   (enum code lookups)
 *  - CarelevoBleModels.kt  (BleState + its many predicate extensions)
 *  - CarelevoBleParams.kt  (BleParams / ConfigParams)
 *  - CarelevoBleResults.kt (CharacterResult custom equals/hashCode, sealed result hierarchies)
 */
internal class CarelevoBleDataTest {

    // ---------------------------------------------------------------------------------------------
    // BondingState.codeToBondingResult
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `bonding code lookups map to the right state`() {
        assertThat((-1).codeToBondingResult()).isEqualTo(BondingState.BOND_NONE)
        assertThat(10.codeToBondingResult()).isEqualTo(BondingState.BOND_NONE)
        assertThat(11.codeToBondingResult()).isEqualTo(BondingState.BOND_BONDING)
        assertThat(12.codeToBondingResult()).isEqualTo(BondingState.BOND_BONDED)
    }

    @Test
    fun `invalid bonding code throws`() {
        assertFailsWith<IllegalArgumentException> { 99.codeToBondingResult() }
        assertFailsWith<IllegalArgumentException> { 0.codeToBondingResult() }
    }

    @Test
    fun `BondingState enumerates all four values`() {
        assertThat(BondingState.entries).containsExactly(
            BondingState.BOND_NONE, BondingState.BOND_BONDING, BondingState.BOND_BONDED, BondingState.BOND_ERROR
        ).inOrder()
        assertThat(BondingState.valueOf("BOND_ERROR")).isEqualTo(BondingState.BOND_ERROR)
    }

    // ---------------------------------------------------------------------------------------------
    // PeripheralConnectionState.codeToConnectionResult
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `connection code lookups map to the right state`() {
        assertThat((-1).codeToConnectionResult()).isEqualTo(PeripheralConnectionState.CONN_STATE_NONE)
        assertThat(0.codeToConnectionResult()).isEqualTo(PeripheralConnectionState.CONN_STATE_DISCONNECTED)
        assertThat(1.codeToConnectionResult()).isEqualTo(PeripheralConnectionState.CONN_STATE_CONNECTING)
        assertThat(2.codeToConnectionResult()).isEqualTo(PeripheralConnectionState.CONN_STATE_CONNECTED)
        assertThat(3.codeToConnectionResult()).isEqualTo(PeripheralConnectionState.CONN_STATE_DISCONNECTING)
    }

    @Test
    fun `invalid connection code throws`() {
        assertFailsWith<IllegalArgumentException> { 4.codeToConnectionResult() }
        assertFailsWith<IllegalArgumentException> { (-2).codeToConnectionResult() }
    }

    @Test
    fun `PeripheralConnectionState enumerates all five values`() {
        assertThat(PeripheralConnectionState.entries).hasSize(5)
    }

    // ---------------------------------------------------------------------------------------------
    // ServiceDiscoverState.codeToDiscoverResult
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `discover code lookups map to the right state`() {
        assertThat((-1).codeToDiscoverResult()).isEqualTo(ServiceDiscoverState.DISCOVER_STATE_CLEARED)
        assertThat(0.codeToDiscoverResult()).isEqualTo(ServiceDiscoverState.DISCOVER_STATE_DISCOVERED)
    }

    @Test
    fun `any other discover code falls through to FAILED`() {
        // The when has no throwing else; every code other than -1 / 0 is treated as a failed discovery.
        assertThat(1.codeToDiscoverResult()).isEqualTo(ServiceDiscoverState.DISCOVER_STATE_FAILED)
        assertThat(2.codeToDiscoverResult()).isEqualTo(ServiceDiscoverState.DISCOVER_STATE_FAILED)
        assertThat(133.codeToDiscoverResult()).isEqualTo(ServiceDiscoverState.DISCOVER_STATE_FAILED)
        assertThat((-99).codeToDiscoverResult()).isEqualTo(ServiceDiscoverState.DISCOVER_STATE_FAILED)
    }

    @Test
    fun `ServiceDiscoverState enumerates all four values`() {
        assertThat(ServiceDiscoverState.entries).hasSize(4)
    }

    // ---------------------------------------------------------------------------------------------
    // DeviceModuleState.codeToDeviceResult
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
    // NotificationState.codeToNotificationResult
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `notification code lookups map to the right state`() {
        assertThat((-1).codeToNotificationResult()).isEqualTo(NotificationState.NOTIFICATION_NONE)
        assertThat(0.codeToNotificationResult()).isEqualTo(NotificationState.NOTIFICATION_DISABLED)
        assertThat(1.codeToNotificationResult()).isEqualTo(NotificationState.NOTIFICATION_ENABLED)
    }

    @Test
    fun `invalid notification code throws`() {
        assertFailsWith<IllegalArgumentException> { 2.codeToNotificationResult() }
        assertFailsWith<IllegalArgumentException> { (-5).codeToNotificationResult() }
    }

    @Test
    fun `NotificationState enumerates all three values`() {
        assertThat(NotificationState.entries).hasSize(3)
    }

    // ---------------------------------------------------------------------------------------------
    // FailureState
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `FailureState enumerates every failure cause`() {
        assertThat(FailureState.entries).containsExactly(
            FailureState.FAILURE_INVALID_PARAMS,
            FailureState.FAILURE_RESOURCE_NOT_INITIALIZED,
            FailureState.FAILURE_PERMISSION_NOT_GRANTED,
            FailureState.FAILURE_BT_NOT_ENABLED,
            FailureState.FAILURE_COMMAND_NOT_EXECUTABLE
        ).inOrder()
    }

    // ---------------------------------------------------------------------------------------------
    // BleParams / ConfigParams
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `BleParams carries its four uuids and supports value semantics`() {
        val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val service = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val tx = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val rx = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        val params = BleParams(cccd, service, tx, rx)
        assertThat(params.cccd).isEqualTo(cccd)
        assertThat(params.serviceUuid).isEqualTo(service)
        assertThat(params.txUuid).isEqualTo(tx)
        assertThat(params.rxUUID).isEqualTo(rx)

        assertThat(params).isEqualTo(BleParams(cccd, service, tx, rx))
        assertThat(params.copy(txUuid = rx)).isNotEqualTo(params)
    }

    @Test
    fun `ConfigParams defaults to foreground and can be overridden`() {
        assertThat(ConfigParams().isForeground).isTrue()
        assertThat(ConfigParams(isForeground = false).isForeground).isFalse()
        assertThat(ConfigParams()).isEqualTo(ConfigParams(true))
        assertThat(ConfigParams().copy(isForeground = false)).isEqualTo(ConfigParams(false))
    }

    // ---------------------------------------------------------------------------------------------
    // CharacterResult (custom equals / hashCode)
    // ---------------------------------------------------------------------------------------------

    private val charUuid: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    @Test
    fun `CharacterResult equals is reflexive`() {
        val r = CharacterResult(charUuid, byteArrayOf(1, 2, 3), 0)
        // exercises the `this === other` short-circuit branch
        assertThat(r.equals(r)).isTrue()
    }

    @Test
    fun `CharacterResult is not equal to null or a different type`() {
        val r = CharacterResult(charUuid, byteArrayOf(1), 0)
        assertThat(r.equals(null)).isFalse()
        assertThat(r.equals("not a result")).isFalse()
    }

    @Test
    fun `CharacterResult equal when uuid, value bytes and status match`() {
        val a = CharacterResult(charUuid, byteArrayOf(1, 2, 3), 7)
        val b = CharacterResult(charUuid, byteArrayOf(1, 2, 3), 7)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `CharacterResult differs when uuid differs`() {
        val a = CharacterResult(charUuid, byteArrayOf(1), 0)
        val b = CharacterResult(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"), byteArrayOf(1), 0)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `CharacterResult differs when value bytes differ`() {
        val a = CharacterResult(charUuid, byteArrayOf(1, 2, 3), 0)
        val b = CharacterResult(charUuid, byteArrayOf(9, 9, 9), 0)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `CharacterResult with non-null value differs from one with null value`() {
        val withValue = CharacterResult(charUuid, byteArrayOf(1), 0)
        val nullValue = CharacterResult(charUuid, null, 0)
        assertThat(withValue).isNotEqualTo(nullValue)
        // symmetric: null value side vs non-null side hits the `else if (other.value != null)` branch
        assertThat(nullValue).isNotEqualTo(withValue)
    }

    @Test
    fun `CharacterResult with both values null compares by uuid and status`() {
        val a = CharacterResult(charUuid, null, 5)
        val b = CharacterResult(charUuid, null, 5)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())

        assertThat(a).isNotEqualTo(CharacterResult(charUuid, null, 6))
    }

    @Test
    fun `CharacterResult differs when status differs`() {
        val a = CharacterResult(charUuid, byteArrayOf(1), 0)
        val b = CharacterResult(charUuid, byteArrayOf(1), 1)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `CharacterResult all-null defaults hash to zero`() {
        val empty = CharacterResult()
        assertThat(empty.uuidCharacteristic).isNull()
        assertThat(empty.value).isNull()
        assertThat(empty.codeStatus).isNull()
        assertThat(empty.hashCode()).isEqualTo(0)
        assertThat(empty).isEqualTo(CharacterResult())
    }

    // ---------------------------------------------------------------------------------------------
    // PeripheralScanResult (sealed)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `PeripheralScanResult subtypes expose their device list through the base value`() {
        val devices = listOf(ScannedDevice(mock<BluetoothDevice>(), rssi = -55))
        val init: PeripheralScanResult = PeripheralScanResult.Init(devices)
        val success: PeripheralScanResult = PeripheralScanResult.Success(devices)
        val failed: PeripheralScanResult = PeripheralScanResult.Failed(emptyList())

        assertThat(init.value).isEqualTo(devices)
        assertThat(success.value).isEqualTo(devices)
        assertThat(failed.value).isEmpty()

        assertThat(init).isInstanceOf(PeripheralScanResult.Init::class.java)
        assertThat(success).isInstanceOf(PeripheralScanResult.Success::class.java)
        assertThat(failed).isInstanceOf(PeripheralScanResult.Failed::class.java)
    }

    @Test
    fun `PeripheralScanResult data classes compare by value`() {
        assertThat(PeripheralScanResult.Success(emptyList())).isEqualTo(PeripheralScanResult.Success(emptyList()))
        assertThat(PeripheralScanResult.Init(emptyList())).isNotEqualTo(PeripheralScanResult.Success(emptyList()))
    }

    // ---------------------------------------------------------------------------------------------
    // CommandResult (sealed)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `CommandResult Pending and Success carry their payload`() {
        val pending: CommandResult<String> = CommandResult.Pending("waiting")
        val success: CommandResult<String> = CommandResult.Success("done")
        assertThat((pending as CommandResult.Pending).data).isEqualTo("waiting")
        assertThat((success as CommandResult.Success).data).isEqualTo("done")
        assertThat(pending).isEqualTo(CommandResult.Pending("waiting"))
    }

    @Test
    fun `CommandResult Failure carries state and message`() {
        val failure = CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth off")
        assertThat(failure.state).isEqualTo(FailureState.FAILURE_BT_NOT_ENABLED)
        assertThat(failure.message).isEqualTo("bluetooth off")
        assertThat(failure).isEqualTo(CommandResult.Failure(FailureState.FAILURE_BT_NOT_ENABLED, "bluetooth off"))
    }

    @Test
    fun `CommandResult Error wraps the throwable`() {
        val boom = IllegalStateException("boom")
        val error = CommandResult.Error(boom)
        assertThat(error.e).isSameInstanceAs(boom)
    }

    // ---------------------------------------------------------------------------------------------
    // ScannedDevice
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `ScannedDevice holds the device and rssi and is mutable`() {
        val device = mock<BluetoothDevice>()
        val scanned = ScannedDevice(device, rssi = -70)
        assertThat(scanned.device).isSameInstanceAs(device)
        assertThat(scanned.rssi).isEqualTo(-70)

        scanned.rssi = -40
        assertThat(scanned.rssi).isEqualTo(-40)
        assertThat(scanned.copy(rssi = -10).rssi).isEqualTo(-10)
    }

    // ---------------------------------------------------------------------------------------------
    // BleState predicate extensions
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

    /** The canonical fully-connected state (all extensions that require "fully up" are true here). */
    private val connectedState = state()

    @Test
    fun `isAvailable is true unless the module is off`() {
        assertThat(state(enabled = DeviceModuleState.DEVICE_STATE_ON).isAvailable()).isTrue()
        assertThat(state(enabled = DeviceModuleState.DEVICE_NONE).isAvailable()).isTrue()
        assertThat(state(enabled = DeviceModuleState.DEVICE_STATE_TURNING_ON).isAvailable()).isTrue()
        assertThat(state(enabled = DeviceModuleState.DEVICE_STATE_OFF).isAvailable()).isFalse()
    }

    @Test
    fun `isPeripheralConnected tracks only the connection field`() {
        assertThat(state(connected = PeripheralConnectionState.CONN_STATE_CONNECTED).isPeripheralConnected()).isTrue()
        assertThat(state(connected = PeripheralConnectionState.CONN_STATE_DISCONNECTED).isPeripheralConnected()).isFalse()
    }

    @Test
    fun `isConnected requires the full happy path`() {
        assertThat(connectedState.isConnected()).isTrue()
        // flip each of the five fields once
        assertThat(connectedState.copy(isEnabled = DeviceModuleState.DEVICE_STATE_OFF).isConnected()).isFalse()
        assertThat(connectedState.copy(isBonded = BondingState.BOND_NONE).isConnected()).isFalse()
        assertThat(connectedState.copy(isConnected = PeripheralConnectionState.CONN_STATE_DISCONNECTED).isConnected()).isFalse()
        assertThat(connectedState.copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE).isConnected()).isFalse()
        assertThat(connectedState.copy(isNotificationEnabled = NotificationState.NOTIFICATION_DISABLED).isConnected()).isFalse()
    }

    @Test
    fun `shouldBeNotificationEnabled matches the fully-connected state`() {
        assertThat(connectedState.shouldBeNotificationEnabled()).isTrue()
        assertThat(connectedState.copy(isNotificationEnabled = NotificationState.NOTIFICATION_DISABLED).shouldBeNotificationEnabled()).isFalse()
    }

    @Test
    fun `shouldBeConnected requires connection but not yet discovery or notifications`() {
        val s = state(
            discovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
            connected = PeripheralConnectionState.CONN_STATE_CONNECTED,
            notification = NotificationState.NOTIFICATION_DISABLED
        )
        assertThat(s.shouldBeConnected()).isTrue()
        // already discovered => no longer "should be connected"
        assertThat(s.copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED).shouldBeConnected()).isFalse()
        // notifications already enabled => false
        assertThat(s.copy(isNotificationEnabled = NotificationState.NOTIFICATION_ENABLED).shouldBeConnected()).isFalse()
        // not connected => false
        assertThat(s.copy(isConnected = PeripheralConnectionState.CONN_STATE_DISCONNECTED).shouldBeConnected()).isFalse()
    }

    @Test
    fun `shouldBeDiscovered requires discovery done but notifications not yet enabled`() {
        val s = state(
            discovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED,
            notification = NotificationState.NOTIFICATION_DISABLED
        )
        assertThat(s.shouldBeDiscovered()).isTrue()
        assertThat(s.copy(isNotificationEnabled = NotificationState.NOTIFICATION_ENABLED).shouldBeDiscovered()).isFalse()
        assertThat(s.copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE).shouldBeDiscovered()).isFalse()
        assertThat(s.copy(isBonded = BondingState.BOND_NONE).shouldBeDiscovered()).isFalse()
    }

    @Test
    fun `isDiscoverCleared matches the cleared teardown state`() {
        val s = state(
            bonded = BondingState.BOND_BONDED,
            connected = PeripheralConnectionState.CONN_STATE_DISCONNECTED,
            discovered = ServiceDiscoverState.DISCOVER_STATE_CLEARED,
            notification = NotificationState.NOTIFICATION_DISABLED
        )
        assertThat(s.isDiscoverCleared()).isTrue()
        assertThat(s.copy(isConnected = PeripheralConnectionState.CONN_STATE_CONNECTED).isDiscoverCleared()).isFalse()
        assertThat(s.copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED).isDiscoverCleared()).isFalse()
    }

    @Test
    fun `isReInitialized and isAbnormalFailed share the re-init signature`() {
        val s = state(
            bonded = BondingState.BOND_NONE,
            connected = PeripheralConnectionState.CONN_STATE_DISCONNECTED,
            discovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED,
            notification = NotificationState.NOTIFICATION_DISABLED
        )
        assertThat(s.isReInitialized()).isTrue()
        assertThat(s.isAbnormalFailed()).isTrue()
        // bonded again => neither holds
        assertThat(s.copy(isBonded = BondingState.BOND_BONDED).isReInitialized()).isFalse()
        assertThat(s.copy(isBonded = BondingState.BOND_BONDED).isAbnormalFailed()).isFalse()
    }

    @Test
    fun `isAbnormalBondingFailed requires an unbonded but still-connected link`() {
        val s = state(
            bonded = BondingState.BOND_NONE,
            connected = PeripheralConnectionState.CONN_STATE_CONNECTED,
            discovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED,
            notification = NotificationState.NOTIFICATION_DISABLED
        )
        assertThat(s.isAbnormalBondingFailed()).isTrue()
        assertThat(s.copy(isConnected = PeripheralConnectionState.CONN_STATE_DISCONNECTED).isAbnormalBondingFailed()).isFalse()
        assertThat(s.copy(isBonded = BondingState.BOND_BONDED).isAbnormalBondingFailed()).isFalse()
    }

    @Test
    fun `isFailed requires unbonded, connected, no discovery`() {
        val s = state(
            bonded = BondingState.BOND_NONE,
            connected = PeripheralConnectionState.CONN_STATE_CONNECTED,
            discovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
            notification = NotificationState.NOTIFICATION_DISABLED
        )
        assertThat(s.isFailed()).isTrue()
        assertThat(s.copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED).isFailed()).isFalse()
        assertThat(s.copy(isNotificationEnabled = NotificationState.NOTIFICATION_ENABLED).isFailed()).isFalse()
    }

    @Test
    fun `isPairingFailed requires a fully torn-down link`() {
        val s = state(
            bonded = BondingState.BOND_NONE,
            connected = PeripheralConnectionState.CONN_STATE_DISCONNECTED,
            discovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
            notification = NotificationState.NOTIFICATION_NONE
        )
        assertThat(s.isPairingFailed()).isTrue()
        assertThat(s.copy(isNotificationEnabled = NotificationState.NOTIFICATION_DISABLED).isPairingFailed()).isFalse()
        assertThat(s.copy(isConnected = PeripheralConnectionState.CONN_STATE_CONNECTED).isPairingFailed()).isFalse()
        assertThat(s.copy(isEnabled = DeviceModuleState.DEVICE_STATE_OFF).isPairingFailed()).isFalse()
    }

    @Test
    fun `BleState supports value semantics`() {
        assertThat(connectedState).isEqualTo(state())
        assertThat(connectedState.copy(isBonded = BondingState.BOND_NONE)).isNotEqualTo(connectedState)
    }
}
