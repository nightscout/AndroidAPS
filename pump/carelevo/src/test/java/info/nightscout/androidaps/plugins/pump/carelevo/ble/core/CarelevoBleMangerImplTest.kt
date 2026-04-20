package info.nightscout.androidaps.plugins.pump.carelevo.ble.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import app.aaps.core.interfaces.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleParams
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BondingState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.DeviceModuleState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.FailureState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.NotificationState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralConnectionState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.ServiceDiscoverState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

internal class CarelevoBleMangerImplTest {

    private val context: Context = mock()
    private val btManager: BluetoothManager = mock()
    private val aapsLogger: AAPSLogger = mock()

    private val params = BleParams(
        cccd = UUID.randomUUID(),
        serviceUuid = UUID.randomUUID(),
        txUuid = UUID.randomUUID(),
        rxUUID = UUID.randomUUID()
    )

    @BeforeEach
    fun setUp() {
        CarelevoBleSource._bluetoothState.onNext(defaultBleState())
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(btManager)
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PackageManager.PERMISSION_GRANTED)
    }

    @Test
    fun connectTo_returns_invalid_params_when_mac_is_empty() = runBlocking {
        whenever(btManager.adapter).thenReturn(null)
        val sut = CarelevoBleMangerImpl(context, params, aapsLogger)

        val result = sut.connectTo("")

        assertFailure(result, FailureState.FAILURE_INVALID_PARAMS)
    }

    @Test
    fun readCharacteristic_returns_not_initialized_when_adapter_is_null() {
        whenever(btManager.adapter).thenReturn(null)
        val sut = CarelevoBleMangerImpl(context, params, aapsLogger)

        val result = sut.readCharacteristic(UUID.randomUUID())

        assertFailure(result, FailureState.FAILURE_RESOURCE_NOT_INITIALIZED)
    }

    @Test
    fun stopScan_returns_not_initialized_when_adapter_is_null() {
        whenever(btManager.adapter).thenReturn(null)
        val sut = CarelevoBleMangerImpl(context, params, aapsLogger)

        val result = sut.stopScan()

        assertFailure(result, FailureState.FAILURE_RESOURCE_NOT_INITIALIZED)
    }

    @Test
    fun writeCharacteristic_returns_not_initialized_when_service_not_discovered() {
        val btAdapter: BluetoothAdapter = mock()
        whenever(btManager.adapter).thenReturn(btAdapter)
        whenever(btAdapter.isEnabled).thenReturn(true)
        val sut = CarelevoBleMangerImpl(context, params, aapsLogger)

        CarelevoBleSource._bluetoothState.onNext(
            defaultBleState().copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE)
        )

        val result = sut.writeCharacteristic(UUID.randomUUID(), byteArrayOf(0x01))

        assertFailure(result, FailureState.FAILURE_RESOURCE_NOT_INITIALIZED)
    }

    @Test
    fun writeCharacteristic_returns_bt_not_enabled_when_adapter_is_off_even_if_service_discovered() {
        val btAdapter: BluetoothAdapter = mock()
        whenever(btManager.adapter).thenReturn(btAdapter)
        whenever(btAdapter.isEnabled).thenReturn(false)
        val sut = CarelevoBleMangerImpl(context, params, aapsLogger)

        CarelevoBleSource._bluetoothState.onNext(
            defaultBleState().copy(isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED)
        )

        val result = sut.writeCharacteristic(UUID.randomUUID(), byteArrayOf(0x01))

        assertFailure(result, FailureState.FAILURE_BT_NOT_ENABLED)
    }

    private fun assertFailure(result: CommandResult<Boolean>, expected: FailureState) {
        assertThat(result).isInstanceOf(CommandResult.Failure::class.java)
        val failure = result as CommandResult.Failure
        assertThat(failure.state).isEqualTo(expected)
    }

    private fun defaultBleState() = BleState(
        isEnabled = DeviceModuleState.DEVICE_NONE,
        isBonded = BondingState.BOND_NONE,
        isConnected = PeripheralConnectionState.CONN_STATE_NONE,
        isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
        isNotificationEnabled = NotificationState.NOTIFICATION_NONE
    )
}
