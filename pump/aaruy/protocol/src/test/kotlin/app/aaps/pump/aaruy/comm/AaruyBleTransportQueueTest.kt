package app.aaps.pump.aaruy.comm

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** Android refuses a GATT operation while another is pending. Here `BluetoothGatt` is a mock, so the test decides when an operation ends. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("DEPRECATION")
class AaruyBleTransportQueueTest {

    private val first = byteArrayOf(1)
    private val second = byteArrayOf(2)
    private val noResponse = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

    private val gatt = mock(BluetoothGatt::class.java)
    private val write = BluetoothGattCharacteristic(UUID.randomUUID(), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE)
    private val firmware = BluetoothGattCharacteristic(UUID.randomUUID(), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
    private val listener = mock(BleTransportListener::class.java)
    private val extras = mock(AaruyTransportExtras::class.java)
    private lateinit var transport: AaruyBleTransportImpl
    private lateinit var callback: BluetoothGattCallback

    private fun field(name: String) = AaruyBleTransportImpl::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Before
    fun setUp() {
        transport = AaruyBleTransportImpl(RuntimeEnvironment.getApplication(), mock(AAPSLogger::class.java))
        transport.setListener(listener)
        transport.setExtrasListener(extras)
        field("bluetoothGatt").set(transport, gatt)
        field("writeCharacteristic").set(transport, write)
        field("firmwareCharacteristic").set(transport, firmware)
        callback = field("gattCallback").get(transport) as BluetoothGattCallback
    }

    @Test
    fun theSecondWriteWaitsForTheFirst() {
        transport.gatt.writeCharacteristic(first)
        transport.gatt.writeCharacteristic(second)
        verify(gatt).writeCharacteristic(write, first, noResponse)
        verify(gatt, never()).writeCharacteristic(write, second, noResponse)

        callback.onCharacteristicWrite(gatt, write, BluetoothGatt.GATT_SUCCESS)
        verify(gatt).writeCharacteristic(write, second, noResponse)
        verify(listener).onCharacteristicWritten()
    }

    @Test
    fun aWriteAndAReadTakeTurns() {
        `when`(gatt.readCharacteristic(firmware)).thenReturn(true)
        transport.gatt.writeCharacteristic(first)
        transport.readFirmwareVersion()
        verify(gatt, never()).readCharacteristic(firmware)

        callback.onCharacteristicWrite(gatt, write, BluetoothGatt.GATT_SUCCESS)
        verify(gatt).readCharacteristic(firmware)

        transport.gatt.writeCharacteristic(second)
        verify(gatt, never()).writeCharacteristic(write, second, noResponse)
        callback.onCharacteristicRead(gatt, firmware, "1.0".toByteArray(), BluetoothGatt.GATT_SUCCESS)
        verify(gatt).writeCharacteristic(write, second, noResponse)
    }

    @Test
    fun aWriteThatCannotStartIsReportedAndTheNextOneRuns() {
        `when`(gatt.writeCharacteristic(write, first, noResponse)).thenReturn(BUSY)
        transport.gatt.writeCharacteristic(first)
        verify(extras).onWriteFailed()

        transport.gatt.writeCharacteristic(second)
        verify(gatt).writeCharacteristic(write, second, noResponse)
    }

    @Test
    fun aWriteThatFailsIsReportedAndTheNextOneRuns() {
        transport.gatt.writeCharacteristic(first)
        transport.gatt.writeCharacteristic(second)
        callback.onCharacteristicWrite(gatt, write, GATT_ERROR)

        verify(extras).onWriteFailed()
        verify(listener, never()).onCharacteristicWritten()
        verify(gatt).writeCharacteristic(write, second, noResponse)
    }

    @Test
    fun linkLossFailsAllWritesAndDoesNotBlockLaterOnes() {
        transport.gatt.writeCharacteristic(first)
        transport.gatt.writeCharacteristic(first)
        transport.gatt.writeCharacteristic(first)
        callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)

        verify(extras, times(3)).onWriteFailed()
        verify(listener).onConnectionStateChanged(false)
        verify(gatt, times(1)).writeCharacteristic(write, first, noResponse)

        transport.gatt.writeCharacteristic(second)
        verify(gatt).writeCharacteristic(write, second, noResponse)
    }

    @Test
    fun aFailedStatusIsNotConnected() {
        callback.onConnectionStateChange(gatt, GATT_ERROR, BluetoothProfile.STATE_CONNECTED)
        verify(listener).onConnectionStateChanged(false)
        verify(listener, never()).onConnectionStateChanged(true)
    }

    @Test
    fun closeFailsPendingWrites() {
        transport.gatt.writeCharacteristic(first)
        transport.gatt.close()
        verify(gatt).close()
        verify(extras).onWriteFailed()
    }

    private companion object {

        const val BUSY = 201
        const val GATT_ERROR = 133
    }
}
