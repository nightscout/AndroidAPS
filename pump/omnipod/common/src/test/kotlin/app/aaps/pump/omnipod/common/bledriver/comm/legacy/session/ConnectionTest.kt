package app.aaps.pump.omnipod.common.bledriver.comm.legacy.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager.BluetoothConnectionState
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The Bluetooth link of the Dash driver, [Connection], on mocked Android Bluetooth classes: what it opens and closes,
 * and what it writes to the pod state, which is where the driver and the command queue read whether the link is up.
 *
 * The pump emulator takes the place of this class, so without this test its code would run for the first time on a
 * real pod. The shared transport contract test of the other pumps does not fit: the Omnipod radio code is older than
 * the shared `BleTransport` and does not use it. This test follows `O5ConnectionTest`, which does the same for the
 * Omnipod 5 link.
 *
 * The waits of [BleCommCallbacks] do not care which thread ends them. So the mocked GATT calls answer by calling the
 * callback at once, and no wait blocks.
 */
class ConnectionTest {

    private val context = mock<Context>()
    private val podState = mock<OmnipodDashPodStateManager>()
    private val podDevice = mock<BluetoothDevice>()
    private val bluetoothManager = mock<BluetoothManager>()
    private val gatt = mock<BluetoothGatt>()

    private fun newConnection(bluetoothServiceAvailable: Boolean = true): Connection {
        whenever(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(if (bluetoothServiceAvailable) bluetoothManager else null)
        return Connection(podDevice, AAPSLoggerTest(), mock<Config>(), context, podState)
    }

    private fun gattState(state: Int) {
        whenever(bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT)).thenReturn(state)
    }

    /** A connect that opens the GATT and then gets no answer from the pod. */
    private fun connectionWithAGattThatDidNotConnect(): Connection {
        whenever(podDevice.connectGatt(eq(context), eq(false), any(), eq(BluetoothDevice.TRANSPORT_LE))).thenReturn(gatt)
        gattState(BluetoothProfile.STATE_DISCONNECTED)
        val connection = newConnection()
        assertThrows(FailedToConnectException::class.java) { connection.connect(ConnectionWaitCondition(timeoutMs = 10L)) }
        return connection
    }

    @Test
    fun `the link is up when Android says the GATT is connected`() {
        gattState(BluetoothProfile.STATE_CONNECTED)

        assertThat(newConnection().connectionState()).isInstanceOf(Connected::class.java)
    }

    @Test
    fun `the link is down when Android says the GATT is not connected`() {
        gattState(BluetoothProfile.STATE_DISCONNECTED)

        assertThat(newConnection().connectionState()).isInstanceOf(NotConnected::class.java)
    }

    @Test
    fun `the link is down when the phone has no Bluetooth service`() {
        assertThat(newConnection(bluetoothServiceAvailable = false).connectionState()).isInstanceOf(NotConnected::class.java)
    }

    @Test
    fun `a connect the pod does not answer is counted and leaves the pod state disconnected`() {
        whenever(podState.connectionAttempts).thenReturn(4)

        connectionWithAGattThatDidNotConnect()

        inOrder(podState) {
            verify(podState).connectionAttempts = 5
            verify(podState).bluetoothConnectionState = BluetoothConnectionState.CONNECTING
            verify(podState).bluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
        }
        verify(podState, never()).bluetoothConnectionState = BluetoothConnectionState.CONNECTED
    }

    @Test
    fun `a disconnect with no GATT touches nothing of Android`() {
        newConnection().disconnect(closeGatt = true)
        newConnection().disconnect(closeGatt = false)

        verify(gatt, never()).disconnect()
        verify(gatt, never()).close()
    }

    @Test
    fun `a disconnect keeps the GATT for the next connect`() {
        val connection = connectionWithAGattThatDidNotConnect()

        connection.disconnect(closeGatt = false)

        verify(gatt).disconnect()
        verify(gatt, never()).close()
        verify(podState, times(2)).bluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
    }

    /** Android has few GATT clients: one that is closed must not be closed again, and must be forgotten. */
    @Test
    fun `a disconnect that closes the GATT closes it once`() {
        val connection = connectionWithAGattThatDidNotConnect()

        connection.disconnect(closeGatt = true)
        connection.disconnect(closeGatt = true)

        verify(gatt, times(1)).close()
    }

    @Test
    fun `a second connect uses the GATT that is still open`() {
        val connection = connectionWithAGattThatDidNotConnect()

        assertThrows(FailedToConnectException::class.java) { connection.connect(ConnectionWaitCondition(timeoutMs = 10L)) }

        verify(podDevice, times(1)).connectGatt(eq(context), eq(false), any(), eq(BluetoothDevice.TRANSPORT_LE))
    }

    /**
     * The whole way a connect goes on a real pod: the GATT connects, the service with its two characteristics is
     * found, and the hello is written to the command characteristic. The step after that waits for the pod, which a
     * mock does not answer, so the connect ends there.
     */
    @Test
    fun `a connect the pod answers finds the service and says hello`() {
        val service = mock<BluetoothGattService>()
        val cmdChar = mock<BluetoothGattCharacteristic>()
        val dataChar = mock<BluetoothGattCharacteristic>()
        var callbacks: BleCommCallbacks? = null
        var written: ByteArray? = null

        gattState(BluetoothProfile.STATE_CONNECTED)
        whenever(podDevice.connectGatt(eq(context), eq(false), any(), eq(BluetoothDevice.TRANSPORT_LE))).thenAnswer {
            callbacks = it.getArgument<BleCommCallbacks>(2)
            callbacks!!.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            gatt
        }
        whenever(gatt.discoverServices()).thenAnswer {
            callbacks!!.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
            true
        }
        whenever(gatt.getService(any())).thenReturn(service)
        whenever(service.getCharacteristic(CharacteristicType.CMD.uuid)).thenReturn(cmdChar)
        whenever(service.getCharacteristic(CharacteristicType.DATA.uuid)).thenReturn(dataChar)
        whenever(cmdChar.uuid).thenReturn(CharacteristicType.CMD.uuid)
        whenever(cmdChar.setValue(any<ByteArray>())).thenAnswer {
            written = it.getArgument(0)
            true
        }
        whenever(cmdChar.value).thenAnswer { written }
        whenever(cmdChar.descriptors).thenReturn(listOf(mock<BluetoothGattDescriptor>()))
        whenever(cmdChar.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE)
        whenever(dataChar.descriptors).thenReturn(listOf(mock<BluetoothGattDescriptor>()))
        whenever(dataChar.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE)
        whenever(gatt.writeCharacteristic(cmdChar)).thenAnswer {
            callbacks!!.onCharacteristicWrite(gatt, cmdChar, BluetoothGatt.GATT_SUCCESS)
            true
        }
        whenever(gatt.setCharacteristicNotification(any(), any())).thenReturn(true)
        whenever(gatt.writeDescriptor(any())).thenReturn(true)

        val connection = newConnection()
        runCatching { connection.connect(ConnectionWaitCondition(timeoutMs = 50L)) }

        inOrder(podState, gatt) {
            verify(podState).bluetoothConnectionState = BluetoothConnectionState.CONNECTING
            verify(podState).bluetoothConnectionState = BluetoothConnectionState.CONNECTED
            verify(gatt).discoverServices()
            verify(gatt).writeCharacteristic(cmdChar)
        }
        assertThat(written).isNotNull()
        assertThat(written).isNotEmpty()
        assertThat(connection.msgIO).isNotNull()
    }
}
