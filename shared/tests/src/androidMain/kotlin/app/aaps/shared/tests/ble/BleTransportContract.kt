package app.aaps.shared.tests.ble

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What every Bluetooth transport of a pump must do, run on its real Android code against Robolectric's Bluetooth.
 *
 * The emulator tests replace the transport, so without this its code runs first on a real pump. A pump's test only says
 * how to build its transport and which service and characteristics the pump has. What all transports do the same way is
 * tested here, once for all. Robolectric fires the GATT callbacks at once, on the calling thread; a transport that writes
 * on a thread of its own is waited for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
abstract class BleTransportContract {

    /**
     * The pump's service and characteristics. Notify and write can be one characteristic.
     * @param writeProperties what the write characteristic allows, as on the real pump
     */
    data class GattProfile(
        val service: UUID,
        val notify: UUID,
        val write: UUID,
        val writeProperties: Int = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    )

    protected abstract val profile: GattProfile

    /** A name the pump advertises, which the transport's scan keeps. */
    protected abstract val advertisedName: String

    protected abstract fun createTransport(context: Context, adapter: BluetoothAdapter): BleTransport

    /**
     * False for a transport that tells its link and data through a callback of its own, not [BleTransportListener], and
     * finds its characteristics by itself. The tests of that path are then skipped, the rest still runs.
     */
    protected open val reportsThroughListener: Boolean = true

    protected val context: Application = RuntimeEnvironment.getApplication()
    protected val adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    protected val device: BluetoothDevice get() = adapter.getRemoteDevice(ADDRESS)
    protected val events = Recorder()
    protected lateinit var transport: BleTransport
    private val collectors = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUpBluetooth() {
        shadowOf(adapter).setEnabled(true)
        shadowOf(device).setName(advertisedName)
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        transport = createTransport(context, adapter)
        transport.setListener(events)
    }

    @After
    fun closeTransport() {
        collectors.cancel()
        runCatching { transport.scanner.stopScan() }
        runCatching { transport.gatt.close() }
    }

    // ---- Permissions and the adapter --------------------------------------------------------------

    @Test
    fun withoutTheConnectPermissionNothingIsTouched() {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        assertFalse("connect", transport.gatt.connect(ADDRESS))
        assertTrue("no GATT", shadowOf(device).bluetoothGatts.isEmpty())
        assertNull("name", transport.adapter.getDeviceName(ADDRESS))
        assertFalse("bonded", transport.adapter.isDeviceBonded(ADDRESS))
        assertFalse("bond", transport.adapter.createBond(ADDRESS))
    }

    @Test
    fun theAdapterTellsNameAndBond() {
        assertEquals(advertisedName, transport.adapter.getDeviceName(ADDRESS))
        assertTrue(transport.adapter.isDeviceBonded(ADDRESS))
        shadowOf(device).setBondState(BluetoothDevice.BOND_NONE)
        assertFalse(transport.adapter.isDeviceBonded(ADDRESS))
    }

    @Test
    fun thePairingStateStartsIdleAndCanBeSet() {
        assertEquals(PairingStep.IDLE, transport.pairingState.value.step)
        transport.updatePairingState(PairingState(PairingStep.CONNECTING))
        assertEquals(PairingStep.CONNECTING, transport.pairingState.value.step)
    }

    // ---- The GATT ---------------------------------------------------------------------------------

    @Test
    fun aConnectOpensOneGattWithTheTransportsCallback() {
        assertTrue(transport.gatt.connect(ADDRESS))
        val gatts = shadowOf(device).bluetoothGatts
        assertEquals("GATT clients", 1, gatts.size)
        assertNotNull(shadowOf(gatts.single()).gattCallback)
    }

    /** Android has few GATT clients. One that is left open ends in status 133 and no connection at all. */
    @Test
    fun aSecondConnectLeavesAtMostOneGattOpen() {
        transport.gatt.connect(ADDRESS)
        transport.gatt.connect(ADDRESS)
        val open = shadowOf(device).bluetoothGatts.count { !shadowOf(it).isClosed }
        assertTrue("open GATT clients: $open", open <= 1)
    }

    @Test
    fun theLinkIsReportedUpAndDown() {
        assumeTrue(reportsThroughListener)
        val gatt = connected()
        shadowOf(gatt).gattCallback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(listOf(true, false), events.links)
    }

    @Test
    fun discoveryFindsTheCharacteristics() {
        assumeTrue(reportsThroughListener)
        val gatt = connected()
        assertFalse("before the discovery", transport.gatt.findCharacteristics())
        addPumpService(gatt)

        transport.gatt.discoverServices()

        await("services reported") { events.discoveries.isNotEmpty() }
        assertEquals(listOf(true), events.discoveries)
        assertTrue("after the discovery", transport.gatt.findCharacteristics())
    }

    @Test
    fun notificationsAreEnabled() {
        assumeTrue(reportsThroughListener)
        discovered()

        transport.gatt.enableNotifications()

        await("the notification descriptor written") { events.descriptorsWritten > 0 }
    }

    @Test
    fun aNotificationReachesTheListener() {
        assumeTrue(reportsThroughListener)
        val gatt = discovered()
        val notify = gatt.getService(profile.service).getCharacteristic(profile.notify)
        val bytes = byteArrayOf(1, 2, 3, 4)

        @Suppress("DEPRECATION")
        notify.value = bytes
        shadowOf(gatt).gattCallback.onCharacteristicChanged(gatt, notify, bytes)

        await("the data reported") { events.received.any { it.contentEquals(bytes) } }
    }

    @Test
    fun aWriteGoesToTheWriteCharacteristic() {
        assumeTrue(reportsThroughListener)
        val gatt = discovered()
        val bytes = byteArrayOf(5, 6, 7, 8)

        transport.gatt.writeCharacteristic(bytes)

        await("the bytes on the radio") { shadowOf(gatt).latestWrittenBytes?.contentEquals(bytes) == true }
        await("the write reported") { events.written > 0 }
    }

    @Test
    fun closeEndsTheGattAndLaterCallsDoNothing() {
        assertTrue(transport.gatt.connect(ADDRESS))
        val gatt = shadowOf(device).bluetoothGatts.single()

        transport.gatt.close()

        assertTrue("closed", shadowOf(gatt).isClosed)
        transport.gatt.discoverServices()
        transport.gatt.writeCharacteristic(byteArrayOf(9))
        transport.gatt.disconnect()
        idle()
        assertNull("nothing written to a closed GATT", shadowOf(gatt).latestWrittenBytes)
    }

    // ---- The scan ---------------------------------------------------------------------------------

    @Test
    fun withoutTheScanPermissionNoScanStarts() {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_SCAN)

        runCatching { transport.scanner.startScan() }

        assertTrue(shadowOf(adapter.bluetoothLeScanner).scanCallbacks.isEmpty())
    }

    @Test
    fun aScanFindsThePumpAndStops() {
        val found = CopyOnWriteArrayList<ScannedDevice>()
        collectors.launch { transport.scanner.scannedDevices.collect { found += it } }
        transport.scanner.startScan()
        val scanner = shadowOf(adapter.bluetoothLeScanner)
        assertEquals("scans", 1, scanner.scanCallbacks.size)

        scanner.scanCallbacks.single().onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult())

        await("the pump found") { found.any { it.address == ADDRESS } }
        transport.scanner.stopScan()
        assertTrue("stopped", scanner.scanCallbacks.isEmpty())
    }

    /** The scan result of the pump. A transport that reads more of it than the name overrides this. */
    @Suppress("DEPRECATION")
    protected open fun scanResult(): ScanResult = ScanResult(device, null, RSSI, 0L)

    // ---- Helpers ----------------------------------------------------------------------------------

    /** A connect that the pump answered. */
    protected fun connected(): BluetoothGatt {
        assertTrue("connect", transport.gatt.connect(ADDRESS))
        val gatt = shadowOf(device).bluetoothGatts.last()
        shadowOf(gatt).gattCallback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        await("the link reported") { events.links.contains(true) }
        return gatt
    }

    /** Connected, with the pump's service found. */
    protected fun discovered(): BluetoothGatt {
        val gatt = connected()
        addPumpService(gatt)
        transport.gatt.discoverServices()
        await("the characteristics found") { transport.gatt.findCharacteristics() }
        return gatt
    }

    private fun addPumpService(gatt: BluetoothGatt) {
        val service = BluetoothGattService(profile.service, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val sameCharacteristic = profile.notify == profile.write
        val notify = BluetoothGattCharacteristic(
            profile.notify,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ or (if (sameCharacteristic) profile.writeProperties else 0),
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        notify.addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        service.addCharacteristic(notify)
        if (!sameCharacteristic)
            service.addCharacteristic(BluetoothGattCharacteristic(profile.write, profile.writeProperties, BluetoothGattCharacteristic.PERMISSION_WRITE))
        shadowOf(gatt).addDiscoverableService(service)
        shadowOf(gatt).allowCharacteristicNotification(notify)
    }

    protected fun await(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + AWAIT_MS
        while (!condition()) {
            if (System.currentTimeMillis() > end) fail("Timeout waiting for: $what. Events: $events")
            idle()
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        Thread.sleep(10)
    }

    /** What the transport told its listener, in order. */
    protected class Recorder : BleTransportListener {

        val links = CopyOnWriteArrayList<Boolean>()
        val discoveries = CopyOnWriteArrayList<Boolean>()
        val received = CopyOnWriteArrayList<ByteArray>()
        @Volatile var descriptorsWritten = 0
        @Volatile var written = 0

        override fun onConnectionStateChanged(connected: Boolean) {
            links += connected
        }

        override fun onServicesDiscovered(success: Boolean) {
            discoveries += success
        }

        override fun onDescriptorWritten() {
            descriptorsWritten++
        }

        override fun onCharacteristicChanged(data: ByteArray) {
            received += data
        }

        override fun onCharacteristicWritten() {
            written++
        }

        override fun toString(): String =
            "links=$links discoveries=$discoveries descriptorsWritten=$descriptorsWritten written=$written received=${received.size}"
    }

    protected companion object {

        const val ADDRESS = "00:11:22:AA:BB:CC"
        const val RSSI = -50
        const val AWAIT_MS = 5_000L

        /** The client characteristic configuration descriptor, the same on every pump. */
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
