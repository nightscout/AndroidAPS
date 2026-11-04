package app.aaps.pump.aaruy.emulator

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
import com.aaruy.mylibrary.open.data.BasalInfo
import com.aaruy.mylibrary.open.data.BasalItem
import com.aaruy.mylibrary.open.data.BasalItems
import com.aaruy.mylibrary.open.data.BasicParam
import com.aaruy.mylibrary.open.interfaces.MyBleListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The unmodified vendor AAR against the emulator. Robolectric's `BluetoothGatt` carries the frames between them. If the
 * emulator's frames were wrong, the AAR's own parsers would show it here. It does not prove the firmware behaves the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AarOnEmulatorTest {

    private val log = CopyOnWriteArrayList<String>()
    private lateinit var util: AABleUtil
    private lateinit var emulator: AaruyPumpEmulator
    private lateinit var gatt: BluetoothGatt
    private lateinit var library: BluetoothGattCallback

    private val notify = BluetoothGattCharacteristic(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"), BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ).apply {
        addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ))
    }
    private val write = BluetoothGattCharacteristic(
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    private fun pumpSends(message: ByteArray) = library.onCharacteristicChanged(gatt, notify, message)

    private fun drain() {
        if (::library.isInitialized) emulator.tick().forEach { pumpSends(it) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        Thread.sleep(20)
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what. status=${emulator.runningStatus} log=${log.takeLast(12)}", System.currentTimeMillis() < end)
            drain()
        }
    }

    private fun logged(prefix: String) = log.any { it.startsWith(prefix) }

    @Before
    fun connect() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val device = adapter.getRemoteDevice(AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)

        util = AABleUtil(context)
        emulator = AaruyPumpEmulator(keyProvider = {
            util.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(util) as ByteArray
        })
        util.setOnBleListener(
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(MyBleListener::class.java)) { _, method, args ->
                log.add(method.name + "(" + args.orEmpty().joinToString() + ")")
                Unit
            } as MyBleListener
        )
        util.bindDevice(device)
        val end = System.currentTimeMillis() + 20_000
        while (shadowOf(device).bluetoothGatts.isEmpty()) {
            assertTrue("no connectGatt", System.currentTimeMillis() < end)
            drain()
        }

        gatt = shadowOf(device).bluetoothGatts.single()
        val shadowGatt = shadowOf(gatt)
        library = shadowGatt.gattCallback
        val real = library
        shadowGatt.addDiscoverableService(
            BluetoothGattService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                addCharacteristic(notify)
                addCharacteristic(write)
            }
        )
        shadowGatt.addDiscoverableService(BluetoothGattService(UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), BluetoothGattService.SERVICE_TYPE_PRIMARY))
        shadowGatt.allowCharacteristicNotification(notify)
        shadowGatt.setGattCallback(object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) = real.onConnectionStateChange(g, status, state)
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = real.onServicesDiscovered(g, status)
            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = real.onMtuChanged(g, mtu, status)

            @Deprecated("Android calls this form on old versions")
            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = real.onDescriptorWrite(g, descriptor, status)

            @Deprecated("Android calls this form on old versions")
            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                val answers = emulator.handle(shadowGatt.latestWrittenBytes)
                real.onCharacteristicWrite(g, characteristic, status)
                answers.forEach { pumpSends(it) }
            }
        })
        real.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)

        waitFor("connected") { logged("onBleStatusChanged(CONNECTED") }
        util.setupBasicParamInfo(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        waitFor("basic param") { logged("onReceiveBasicParamSetting") }
        util.getCurStatus()
        waitFor("status") { logged("onReceiveCurrentStatus") && logged("onReceiveRealData") }
    }

    @Test
    fun connectGivesStatusAndParamsTheAarUnderstands() {
        assertTrue(log.toString(), log.any { it.contains("maxBolus=10.0") && it.contains("bolusStep=2") })
        assertTrue(log.toString(), log.any { it.contains("runningStatus=1") && it.contains("basalStatus=BasalStatus(") })
    }

    @Test
    fun bolusDeliversStepByStepAndEnds() {
        log.clear()
        util.setupAndStartBolus(0.15, 0.0, 0, false)
        waitFor("bolus running") { log.any { it.contains("runningStatus=3") } }
        waitFor("bolus done") { log.any { it.contains("runningStatus=3") && it.contains("state=2") } }
        waitFor("back on basal") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        assertTrue("delivered 0.15: $log", log.any { it.contains("curBolusNow=150.0") })
        assertEquals("no sequence errors", 0, emulator.rejectedFrames)
    }

    @Test
    fun cancelledBolusPausesThenContinues() {
        util.setupAndStartBolus(5.0, 0.0, 0, false)
        waitFor("bolus running") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BOLUS }
        waitFor("status seen by AAR") { log.any { it.contains("runningStatus=3") } }
        util.cancelBolus()
        waitFor("basal again") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        waitFor("status seen") { log.any { it.contains("runningStatus=1") } }
    }

    @Test
    fun bolusAboveMaxIsRefusedByTheAar() {
        log.clear()
        util.setupAndStartBolus(11.0, 0.0, 0, false)
        waitFor("refused") { logged("onBolusValueTooHigh") }
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus)
    }

    @Test
    fun tempBasalStartsAndCancels() {
        log.clear()
        util.setClosedMode(1)
        waitFor("closed mode") { emulator.closedMode == 1 }
        util.startTempBasal(1.5, 30)
        waitFor("temp running") { log.any { it.contains("runningStatus=2") && it.contains("curValue=1.5") } }
        util.cancelTempBasal()
        waitFor("temp ended") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    @Test
    fun basalProfileIsEchoed() {
        log.clear()
        util.setupBasalInfo(BasalInfo(BasalItems(12.3, 2, listOf(BasalItem(0, 0.5), BasalItem(24, 0.275)))))
        waitFor("echo") { logged("onReceiveBasalSetting") }
        assertTrue(log.toString(), log.any { it.contains("BasalItem(startTime=24, basalSpeed=0.275)") && it.contains("injectPerDay=12.3") })
    }

    @Test
    fun historyAfterBolusHasTheBolus() {
        util.setupAndStartBolus(0.1, 0.0, 0, false)
        waitFor("bolus done") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL && log.any { it.contains("state=2") } }
        log.clear()
        util.getHistoryLogData(0)
        waitFor("history") { logged("onReceiveHistoryLog") }
        assertTrue(log.toString(), log.any { it.contains("type=2") && it.contains("data=100") })
        util.getHistoryDailyData(0)
        waitFor("daily") { logged("onReceiveHistoryDaily") }
    }

    @Test
    fun replaceThenStartBasalGoesThroughRestore() {
        log.clear()
        util.replaceDevice()
        waitFor("rewinding") { log.any { it.contains("runningStatus=5") } }
        waitFor("stopped") { log.any { it.contains("runningStatus=8") } }
        assertTrue("rewind event: $log", logged("onReceiveErrorData(17, 0"))
        util.startBasal()
        waitFor("back to not running, then basal by the AAR's own flow") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
    }

    @Test
    fun sequenceErrorRecoversThroughSerialRead() {
        val manager = util.javaClass.getDeclaredField("myBleManager").apply { isAccessible = true }.get(util)
        manager.javaClass.getField("g").set(manager, 500.toShort())
        log.clear()
        util.getCurStatus()
        waitFor("error seen") { logged("onReceiveErrorData(28, 128") }
        assertTrue(emulator.rejectedFrames > 0)
        waitFor("recovered") {
            log.clear()
            util.getCurStatus()
            drain()
            drain()
            drain()
            logged("onReceiveCurrentStatus")
        }
    }

    @Test
    fun alarmReachesTheAar() {
        log.clear()
        pumpSends(emulator.raiseAlarm(0x8105))
        waitFor("alarm") { logged("onReceiveErrorData(5, 129") }
    }

    @Test
    fun lowReservoirRaisesAlarmOnce() {
        log.clear()
        emulator.reservoir = 19.0
        waitFor("low reservoir") { logged("onReceiveErrorData(3, 129") }
        Thread.sleep(600)
        drain()
        assertEquals(log.toString(), 1, log.count { it.startsWith("onReceiveErrorData(3, 129") })
    }

    @Test
    fun linkLossIsReported() {
        log.clear()
        library.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        waitFor("reported") { logged("onBleStatusChanged(DISCONNECTED") }
        assertFalse(logged("onBleStatusChanged(CONNECTED"))
    }
}
