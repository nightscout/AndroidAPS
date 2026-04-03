package app.aaps.receivers

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.utils.extensions.safeGetParcelableExtra
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BTReceiverTest : TestBaseWithProfile() {

    // The System Under Test
    private lateinit var btReceiver: BTReceiver

    // Mocks for dependencies
    @Mock lateinit var intent: Intent
    @Mock lateinit var bluetoothDevice: BluetoothDevice
    @Mock lateinit var mockedRxBus: RxBus

    private val eventCaptor = argumentCaptor<EventBTChange>()

    private val deviceName = "TestPump"
    private val deviceAddress = "00:11:22:33:44:55"

    @BeforeEach
    fun setUpMocks() {
        btReceiver = BTReceiver().also {
            it.rxBus = mockedRxBus
        }

        // Common setup for the mock BluetoothDevice
        whenever(bluetoothDevice.name).thenReturn(deviceName)
        whenever(bluetoothDevice.address).thenReturn(deviceAddress)
    }

    @Test
    fun `processIntent sends CONNECT event on ACTION_ACL_CONNECTED when permission is granted`() {
        // Arrange
        whenever(context.checkPermission(Manifest.permission.BLUETOOTH_CONNECT, android.os.Process.myPid(), android.os.Process.myUid()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
        whenever(intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java))
            .thenReturn(bluetoothDevice)
        whenever(intent.action).thenReturn(BluetoothDevice.ACTION_ACL_CONNECTED)

        // Act
        btReceiver.processIntent(context, intent)

        // Assert
        verify(mockedRxBus).send(eventCaptor.capture())
        val sentEvent = eventCaptor.singleValue
        assertThat(sentEvent.state).isEqualTo(EventBTChange.Change.CONNECT)
        assertThat(sentEvent.deviceName).isEqualTo(deviceName)
        assertThat(sentEvent.deviceAddress).isEqualTo(deviceAddress)
    }

    @Test
    fun `processIntent sends DISCONNECT event on ACTION_ACL_DISCONNECTED when permission is granted`() {
        // Arrange
        whenever(context.checkPermission(Manifest.permission.BLUETOOTH_CONNECT, android.os.Process.myPid(), android.os.Process.myUid()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
        whenever(intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java))
            .thenReturn(bluetoothDevice)
        whenever(intent.action).thenReturn(BluetoothDevice.ACTION_ACL_DISCONNECTED)

        // Act
        btReceiver.processIntent(context, intent)

        // Assert
        verify(mockedRxBus).send(eventCaptor.capture())
        val sentEvent = eventCaptor.singleValue
        assertThat(sentEvent.state).isEqualTo(EventBTChange.Change.DISCONNECT)
        assertThat(sentEvent.deviceName).isEqualTo(deviceName)
        assertThat(sentEvent.deviceAddress).isEqualTo(deviceAddress)
    }

    @Test
    fun `processIntent does nothing if permission is NOT granted`() {
        // Arrange
        whenever(context.checkPermission(Manifest.permission.BLUETOOTH_CONNECT, android.os.Process.myPid(), android.os.Process.myUid()))
            .thenReturn(PackageManager.PERMISSION_DENIED)
        whenever(intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java))
            .thenReturn(bluetoothDevice)
        whenever(intent.action).thenReturn(BluetoothDevice.ACTION_ACL_CONNECTED)

        // Act
        btReceiver.processIntent(context, intent)

        // Assert
        verify(mockedRxBus, never()).send(any())
    }

    @Test
    fun `processIntent does nothing if device is missing from intent`() {
        // Arrange
        whenever(intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java))
            .thenReturn(null)

        // Act
        btReceiver.processIntent(context, intent)

        // Assert
        verify(mockedRxBus, never()).send(any())
    }

    @Test
    fun `processIntent does nothing for an irrelevant action`() {
        // Arrange
        whenever(context.checkPermission(Manifest.permission.BLUETOOTH_CONNECT, android.os.Process.myPid(), android.os.Process.myUid()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)
        whenever(intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java))
            .thenReturn(bluetoothDevice)
        whenever(intent.action).thenReturn("some.other.irrelevant.ACTION")

        // Act
        btReceiver.processIntent(context, intent)

        // Assert
        verify(mockedRxBus, never()).send(any())
    }
}
