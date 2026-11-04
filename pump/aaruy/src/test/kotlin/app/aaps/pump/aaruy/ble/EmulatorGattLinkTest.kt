package app.aaps.pump.aaruy.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import com.aaruy.mylibrary.AABleUtil
import com.aaruy.mylibrary.open.interfaces.MyBleListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import no.nordicsemi.android.ble.EmulatorGattLink
import org.junit.After
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
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** The vendor AAR on the BLE library with [EmulatorGattLink] under it: no radio and no `BluetoothGatt`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmulatorGattLinkTest {

    private val written = CopyOnWriteArrayList<ByteArray>()
    private val log = CopyOnWriteArrayList<String>()
    private lateinit var util: AABleUtil

    private val transport = object : BleTransport {
        var activeListener: BleTransportListener? = null
        override val adapter = object : BleAdapter {
            override fun enable() {}
            override fun getDeviceName(address: String): String? = null
            override fun isDeviceBonded(address: String) = true
            override fun createBond(address: String) = true
            override fun removeBond(address: String) {}
        }
        override val scanner = object : BleScanner {
            override val scannedDevices: SharedFlow<ScannedDevice> = MutableSharedFlow()
            override fun startScan() {}
            override fun stopScan() {}
        }
        override val gatt = object : BleGatt {
            override fun connect(address: String): Boolean {
                activeListener?.onConnectionStateChanged(true)
                return true
            }

            override fun disconnect() {
                activeListener?.onConnectionStateChanged(false)
            }

            override fun close() {}
            override fun discoverServices() {
                activeListener?.onServicesDiscovered(true)
            }

            override fun findCharacteristics() = true
            override fun enableNotifications() {
                activeListener?.onDescriptorWritten()
            }

            override fun writeCharacteristic(data: ByteArray) {
                written.add(data)
                activeListener?.onCharacteristicWritten()
            }
        }
        override val pairingState: StateFlow<PairingState> = MutableStateFlow(PairingState())
        override fun updatePairingState(state: PairingState) {}
        override fun setListener(listener: BleTransportListener?) {
            activeListener = listener
        }
    }

    @After
    fun deactivate() {
        EmulatorGattLink.activate(null)
    }

    private fun drain() {
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            Thread.sleep(25)
        }
    }

    private fun drainUntil(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what. sent=${sentIds()} log=$log", System.currentTimeMillis() < end)
            drain()
        }
    }

    private fun cipher(mode: Int): Cipher {
        val key = util.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(util) as ByteArray
        return Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES")) }
    }

    private fun sentIds(): List<Int> = written.map {
        val plain = cipher(Cipher.DECRYPT_MODE).doFinal(it.copyOfRange(3, it.size))
        ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).getShort(3).toInt()
    }

    private fun frame(paramId: Int, payload: ByteArray): ByteArray {
        val plain = ByteBuffer.allocate(7 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((payload.size + 5).toShort()).put(1).putShort(paramId.toShort()).putShort(1).put(payload).array()
        val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        return ByteBuffer.allocate(encrypted.size + 3).order(ByteOrder.LITTLE_ENDIAN).put(0x55).putShort(encrypted.size.toShort()).put(encrypted).array()
    }

    @Test
    fun aarConnectsAndBolusesWithoutRadio() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        // Bluetooth stays off: the emulator path must not need it.
        shadowOf(adapter).setEnabled(false)
        val device = adapter.getRemoteDevice("00:11:22:33:44:55")
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        EmulatorGattLink.activate(transport)

        util = AABleUtil(context)
        util.setOnBleListener(
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(MyBleListener::class.java)) { _, method, args ->
                log.add(method.name + "(" + args.orEmpty().joinToString() + ")")
                Unit
            } as MyBleListener
        )
        util.bindDevice(device)
        drainUntil("connected and report device sent") { sentIds().contains(17) && log.any { it.startsWith("onBleStatusChanged(CONNECTED") } }
        assertTrue("no real GATT may be opened", shadowOf(device).bluetoothGatts.isEmpty())

        fun pump(paramId: Int, payload: ByteArray) {
            transport.activeListener?.onCharacteristicChanged(frame(paramId, payload))
            drain()
        }

        pump(17, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).put(6).put(1).putShort(50).array())
        drainUntil("time sync sent") { sentIds().contains(5) }
        pump(0, ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).putInt(0).put(1).putShort(25).putShort(2000).putShort(500).put(0).putShort(10000.toShort()).put(1).array())
        pump(6, ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putShort(150).put(1).array())

        util.setupAndStartBolus(1.5, 0.0, 0, true)
        drainUntil("bolus start sent") { sentIds().contains(9) }

        assertEquals(listOf(17, 5, 4, 9), sentIds())
        assertTrue("status: $log", log.any { it.startsWith("onReceiveCurrentStatus") })
        assertTrue("basic param: $log", log.any { it.startsWith("onReceiveBasicParamSetting") })
    }
}
