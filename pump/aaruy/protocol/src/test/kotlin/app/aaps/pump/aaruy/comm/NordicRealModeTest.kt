package app.aaps.pump.aaruy.comm

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
import com.aaruy.mylibrary.AABleUtil
import com.aaruy.mylibrary.open.interfaces.MyBleListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * The reference for `AaruyBleTransportImplTest`: what the vendor AAR does on the real Bluetooth path.
 *
 * The vendor AAR runs on the published Nordic BLE library, down to Android's `BluetoothGatt`. Robolectric
 * plays the Android side and this test plays the pump. Nothing of the AAR or the library is mocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NordicRealModeTest {

    private class Sent(val paramId: Int, val payload: ByteArray)

    private val sent = CopyOnWriteArrayList<Sent>()
    private val log = mutableListOf<String>()
    private lateinit var util: AABleUtil

    private val serviceUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val writeUuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val notifyUuid = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    private val deviceInfoUuid = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val notify = BluetoothGattCharacteristic(
        notifyUuid,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(BluetoothGattDescriptor(cccdUuid, BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ))
    }
    private val write = BluetoothGattCharacteristic(
        writeUuid,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    private fun drain() {
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            Thread.sleep(25)
        }
    }

    /** Parts of the path run on real threads, so a fixed wait would make this test flaky. */
    private fun drainUntil(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what. sent=${sent.map { it.paramId }} log=$log", System.currentTimeMillis() < end)
            drain()
        }
    }

    private fun logged(prefix: String): Boolean = synchronized(log) { log.any { it.startsWith(prefix) } }

    private fun cipher(mode: Int): Cipher {
        val key = util.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(util) as ByteArray
        return Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES")) }
    }

    private fun decode(frame: ByteArray): Sent {
        val plain = cipher(Cipher.DECRYPT_MODE).doFinal(frame.copyOfRange(3, frame.size))
        val paramId = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).getShort(3).toInt()
        return Sent(paramId, plain.copyOfRange(7, plain.size))
    }

    private fun frame(paramId: Int, payload: ByteArray): ByteArray {
        val plain = ByteBuffer.allocate(7 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((payload.size + 5).toShort()).put(1).putShort(paramId.toShort()).putShort(1).put(payload).array()
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        return ByteBuffer.allocate(encrypted.size + 3).order(ByteOrder.LITTLE_ENDIAN).put(0x55).putShort(encrypted.size.toShort()).put(encrypted).array()
    }

    @Test
    fun aarConnectsAndBolusesThroughRealGattPath() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val device = adapter.getRemoteDevice("00:11:22:33:44:55")
        shadowOf(device).setName("aaruy_Q12345")
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)

        util = AABleUtil(context)
        util.setOnBleListener(
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(MyBleListener::class.java)) { _, method, args ->
                synchronized(log) { log.add(method.name + "(" + args.orEmpty().joinToString() + ")") }
                Unit
            } as MyBleListener
        )

        util.bindDevice(device)
        drain()

        val gatt: BluetoothGatt = shadowOf(device).bluetoothGatts.single()
        val shadowGatt = shadowOf(gatt)
        val library = shadowGatt.gattCallback
        shadowGatt.addDiscoverableService(
            BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                addCharacteristic(notify)
                addCharacteristic(write)
            }
        )
        shadowGatt.addDiscoverableService(BluetoothGattService(deviceInfoUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY))
        shadowGatt.allowCharacteristicNotification(notify)
        shadowGatt.setGattCallback(object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) = library.onConnectionStateChange(g, status, state)
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = library.onServicesDiscovered(g, status)
            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = library.onMtuChanged(g, mtu, status)

            @Deprecated("Android calls this form on old versions")
            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = library.onDescriptorWrite(g, descriptor, status)

            @Deprecated("Android calls this form on old versions")
            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                sent.add(decode(shadowGatt.latestWrittenBytes))
                library.onCharacteristicWrite(g, characteristic, status)
            }
        })

        library.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        drainUntil("connected and report device sent") { sent.any { it.paramId == 17 } && logged("onBleStatusChanged(CONNECTED") }

        fun pump(paramId: Int, payload: ByteArray) {
            library.onCharacteristicChanged(gatt, notify, frame(paramId, payload))
            drain()
        }

        pump(17, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).put(6).put(1).putShort(50).array())
        drainUntil("time sync sent") { sent.any { it.paramId == 5 } }
        pump(0, ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).putInt(0).put(1).putShort(25).putShort(2000).putShort(500).put(0).putShort(10000.toShort()).put(1).array())
        pump(6, ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putShort(150).put(1).array())

        val beforeBolus = sent.size
        util.setupAndStartBolus(1.5, 0.0, 0, true)
        drain()


        assertEquals("report device, then time sync", listOf(17, 5), sent.take(2).map { it.paramId })
        assertTrue("connected: $log", log.any { it.startsWith("onBleStatusChanged(CONNECTED") })
        assertTrue("basic param: $log", log.any { it.startsWith("onReceiveBasicParamSetting") })
        assertTrue("status: $log", log.any { it.startsWith("onReceiveCurrentStatus") })
        assertEquals("bolus setting, then start", listOf(4, 9), sent.drop(beforeBolus).map { it.paramId })
        val setting = ByteBuffer.wrap(sent[beforeBolus].payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("1.5 U total on the wire", 1500, setting.getShort(4).toInt() and 0xFFFF)
    }
}
