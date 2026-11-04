package app.aaps.pump.aaruy.comm

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Kotlin client on the real transport, down to Android's `BluetoothGatt`. Robolectric plays Android and this test
 * plays the pump. `NordicRealModeTest` runs the vendor AAR through the same steps and expects the same frames.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyBleTransportImplTest {

    private val sent = CopyOnWriteArrayList<AaruyFrame.Message>()
    private val log = CopyOnWriteArrayList<String>()

    private val notify = BluetoothGattCharacteristic(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"), BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ).apply {
        addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ))
    }
    private val write = BluetoothGattCharacteristic(
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    private fun drain() {
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            Thread.sleep(25)
        }
    }

    private fun drainUntil(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what. sent=${sent.map { it.paramId }} log=$log", System.currentTimeMillis() < end)
            drain()
        }
    }

    private fun frame(paramId: Short, payload: ByteArray) = AaruyFrame.buildWrite(paramId, 1, payload)
    private fun logged(prefix: String) = log.any { it.startsWith(prefix) }

    private lateinit var client: AaruyBleClient
    private lateinit var gatt: BluetoothGatt
    private lateinit var android: BluetoothGattCallback

    private fun connect() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val device = adapter.getRemoteDevice("00:11:22:33:44:55")
        shadowOf(device).setName("aaruy_Q12345")
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)

        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val transport = AaruyBleTransportImpl(context, mock(AAPSLogger::class.java))
        client = AaruyBleClient(context, mock(AAPSLogger::class.java), transport, Looper.getMainLooper())
        client.setOnBleListener(
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(AaruyBleListener::class.java)) { _, method, args ->
                log.add(method.name + "(" + args.orEmpty().joinToString() + ")")
                Unit
            } as AaruyBleListener
        )
        client.bindDevice(device)
        // The transport asks the adapter for its own BluetoothDevice object, so the GATT is not on `device`.
        val gattField = AaruyBleTransportImpl::class.java.getDeclaredField("bluetoothGatt").apply { isAccessible = true }
        drainUntil("connectGatt") { gattField.get(transport) != null }

        gatt = gattField.get(transport) as BluetoothGatt
        val shadowGatt = shadowOf(gatt)
        android = shadowGatt.gattCallback
        val real = android
        shadowGatt.addDiscoverableService(
            BluetoothGattService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                addCharacteristic(notify)
                addCharacteristic(write)
            }
        )
        shadowGatt.allowCharacteristicNotification(notify)
        shadowGatt.setGattCallback(object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) = real.onConnectionStateChange(g, status, state)
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = real.onServicesDiscovered(g, status)
            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = real.onMtuChanged(g, mtu, status)

            @Deprecated("Android calls this form on old versions")
            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = real.onDescriptorWrite(g, descriptor, status)

            @Deprecated("Android calls this form on old versions")
            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                sent.add(AaruyFrame.parse(shadowGatt.latestWrittenBytes)!!)
                real.onCharacteristicWrite(g, characteristic, status)
            }
        })

        real.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        drainUntil("connected and report device sent") { sent.any { it.paramId == AaruyParamId.REPORT_DEVICE } && logged("onBleStatusChanged(CONNECTED") }
    }

    private fun pump(paramId: Short, payload: ByteArray) {
        android.onCharacteristicChanged(gatt, notify, frame(paramId, payload))
        drain()
    }

    @Test
    fun connectsAndBolusesWithTheSameFramesAsTheAar() {
        connect()

        pump(17, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).put(6).put(1).putShort(50).array())
        drainUntil("time sync sent") { sent.any { it.paramId == AaruyParamId.SYNC_TIME } }
        pump(0, ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).putInt(0).put(1).putShort(25).putShort(2000).putShort(500).put(0).putShort(10000.toShort()).put(1).array())
        pump(6, ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putShort(150).put(1).array())

        val beforeBolus = sent.size
        client.setupAndStartBolus(1.5, 0.0, 0, true)
        drainUntil("bolus start sent") { sent.any { it.paramId == AaruyParamId.BOLUS_CONTROL } }

        assertEquals(listOf<Short>(17, 5), sent.take(2).map { it.paramId })
        assertTrue(log.toString(), logged("onReceiveBasicParamSetting"))
        assertTrue(log.toString(), logged("onReceiveCurrentStatus"))
        assertEquals(listOf<Short>(4, 9), sent.drop(beforeBolus).map { it.paramId })
        val setting = ByteBuffer.wrap(sent[beforeBolus].payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("1.5 U total on the wire", 1500, setting.getShort(4).toInt() and 0xFFFF)
    }

    @Test
    fun linkLossIsReported() {
        connect()
        log.clear()
        android.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        drainUntil("reported") { logged("onBleStatusChanged(DISCONNECTED") }
    }

    @Test
    fun nothingIsSentBeforeTheSetupIsComplete() {
        val transport = mock(AaruyBleTransport::class.java, RETURNS_DEEP_STUBS)
        val client = AaruyBleClient(RuntimeEnvironment.getApplication(), mock(AAPSLogger::class.java), transport, Looper.getMainLooper())
        client.getCurStatus()
        client.cancelBolus()
        verify(transport.gatt, never()).writeCharacteristic(any<ByteArray?>() ?: ByteArray(0))
    }
}
