package app.aaps.implementation.pump.ble

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
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleException
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.ConnectOptions
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration as JavaDuration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Android part of the v2 Bluetooth layer, on Robolectric's Bluetooth: how the GATT callbacks become the ends of the
 * link's operations. The rules of the link itself (one operation at a time, time limits) are tested on `QueuedBleLink`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidBleCentralTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val adapter: BluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val remote: BluetoothDevice get() = adapter.getRemoteDevice(ADDRESS)
    /**
     * Reads no bond state, so a test sees what the broadcasts alone do. Robolectric's createBond() makes the device
     * BONDED at once, where a real system says BONDING, so a read would end every bond wait at its first look.
     */
    private val central = AndroidBleCentral(context, AAPSLoggerTest()).apply { bondReadInterval = 1.hours }

    /** Reads the bond state as in the app. */
    private val readingCentral = AndroidBleCentral(context, AAPSLoggerTest())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val service = BleUuid.of("fff0")
    private val notify = CharacteristicId(service, BleUuid.of("fff1"))
    private val write = CharacteristicId(service, BleUuid.of("fff2"))
    private val read = CharacteristicId(service, BleUuid.of("fff3"))

    @Before
    fun setUp() {
        shadowOf(adapter).setEnabled(true)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, true)
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        shadowOf(remote).setName("PUMP-1")
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---- Connect ------------------------------------------------------------------------------------------------

    @Test
    fun aConnectGivesALinkWithTheServicesOfTheDevice() {
        val (link, _) = connected()

        assertThat(link.state.value).isEqualTo(LinkState.Up)
        assertThat(link.services.characteristics[notify]).containsExactly(CharacteristicProperty.NOTIFY)
        assertThat(link.services.characteristics[write]).containsExactly(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE)
        assertThat(link.services.characteristics[read]).containsExactly(CharacteristicProperty.READ)
        assertThat(link.maxWriteLength(WriteType.WITHOUT_RESPONSE)).isEqualTo(20)
    }

    @Test
    fun aDeviceThatDoesNotAnswerTimesOutAndItsGattIsClosed() = runBlocking {
        val result = central.connect(DEVICE, ConnectOptions(timeout = 300.milliseconds))

        assertThat(failureOf(result).kind).isEqualTo(BleFailure.Kind.TIMEOUT)
        assertThat(shadowOf(shadowOf(remote).bluetoothGatts.single()).isClosed).isTrue()
    }

    /** Status 133, the usual Android failure. A GATT left open after it blocks the next connects. */
    @Test
    fun aConnectThatFailsGivesTheStatusAndClosesItsGatt() = runBlocking {
        val result = scope.async { central.connect(DEVICE) }
        val gatt = awaitGatt()

        shadowOf(gatt).gattCallback.onConnectionStateChange(gatt, 133, BluetoothProfile.STATE_DISCONNECTED)

        val failure = failureOf(result.await())
        assertThat(failure.kind).isEqualTo(BleFailure.Kind.REFUSED)
        assertThat(failure.platformStatus).isEqualTo(133)
        assertThat(shadowOf(gatt).isClosed).isTrue()
    }

    @Test
    fun aBadAddressIsNotSupported() = runBlocking {
        assertThat(failureOf(central.connect(DeviceId("not an address"))).kind).isEqualTo(BleFailure.Kind.NOT_SUPPORTED)
    }

    // ---- The link -----------------------------------------------------------------------------------------------

    @Test
    fun aSubscriptionIsConfirmedAndTheDataArrives() = runBlocking {
        val (link, gatt) = connected()

        assertThat(link.subscribe(notify, timeout = 1.seconds)).isEqualTo(BleResult.Ok(Unit))
        shadowOf(gatt).gattCallback.onCharacteristicChanged(gatt, characteristic(gatt, notify), byteArrayOf(1, 2, 3))

        val incoming = withTimeout(1.seconds) { link.incoming.first() }
        assertThat(incoming.characteristic).isEqualTo(notify)
        assertThat(incoming.data).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun aWriteReachesTheDevice() = runBlocking {
        val (link, gatt) = connected()

        assertThat(link.write(write, byteArrayOf(4, 5), WriteType.WITH_RESPONSE, 1.seconds)).isEqualTo(BleResult.Ok(Unit))
        assertThat(shadowOf(gatt).latestWrittenBytes).isEqualTo(byteArrayOf(4, 5))
    }

    @Test
    fun aReadReturnsTheValue() = runBlocking {
        val (link, gatt) = connected()
        val result = scope.async { link.read(read, 2.seconds) }
        awaitStarted()

        shadowOf(gatt).gattCallback.onCharacteristicRead(gatt, characteristic(gatt, read), byteArrayOf(9, 8), BluetoothGatt.GATT_SUCCESS)

        assertThat((result.await() as BleResult.Ok).value).isEqualTo(byteArrayOf(9, 8))
    }

    @Test
    fun theDeviceGoingAwayEndsTheLinkAndClosesTheGatt() = runBlocking {
        val (link, gatt) = connected()

        shadowOf(gatt).gattCallback.onConnectionStateChange(gatt, 8, BluetoothProfile.STATE_DISCONNECTED)

        val state = link.state.value as LinkState.Lost
        assertThat(state.failure.platformStatus).isEqualTo(8)
        assertThat(shadowOf(gatt).isClosed).isTrue()
        assertThat(failureOf(link.write(write, byteArrayOf(1), WriteType.WITHOUT_RESPONSE)).kind).isEqualTo(BleFailure.Kind.NO_LINK)
    }

    @Test
    fun closeClosesTheGatt() = runBlocking {
        val (link, gatt) = connected()

        link.close()

        assertThat(link.state.value).isEqualTo(LinkState.Closed)
        assertThat(shadowOf(gatt).isClosed).isTrue()
    }

    // ---- Scan ---------------------------------------------------------------------------------------------------

    @Test
    fun aScanFindsTheDeviceAndStopsWhenTheCollectorStops() = runBlocking {
        val found = CopyOnWriteArrayList<Advertisement>()
        val collector = scope.launch { central.scan(ScanFilter(namePrefix = "PUMP")).collect { found += it } }
        val scanner = shadowOf(adapter.bluetoothLeScanner)
        await("the scan started") { scanner.scanCallbacks.isNotEmpty() }

        @Suppress("DEPRECATION")
        scanner.scanCallbacks.single().onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, ScanResult(remote, null, -60, 0L))

        await("the device found") { found.isNotEmpty() }
        assertThat(found.single().device).isEqualTo(DEVICE)
        assertThat(found.single().name).isEqualTo("PUMP-1")
        assertThat(found.single().rssi).isEqualTo(-60)
        collector.cancel()
        await("the scan stopped") { scanner.scanCallbacks.isEmpty() }
    }

    /** Medtrum finds its patch by the serial in its manufacturer data; Omnipod its pod by the service UUIDs. */
    @Test
    fun theAdvertisementCarriesTheServicesAndTheManufacturerData() = runBlocking {
        val found = CopyOnWriteArrayList<Advertisement>()
        val collector = scope.launch { central.scan(ScanFilter(manufacturerId = MANUFACTURER)).collect { found += it } }
        val scanner = shadowOf(adapter.bluetoothLeScanner)
        await("the scan started") { scanner.scanCallbacks.isNotEmpty() }
        val record = byteArrayOf(
            3, 0x03, 0xf0.toByte(), 0xff.toByte(),                        // complete 16-bit service UUIDs: fff0
            6, 0xff.toByte(), 0x79, 0x47, 0x11, 0x22, 0x33,               // manufacturer 0x4779, data 11 22 33
            7, 0x09, 'P'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), '-'.code.toByte(), '2'.code.toByte()
        )

        @Suppress("DEPRECATION")
        scanner.scanCallbacks.single().onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, ScanResult(remote, parse(record), -40, 0L))

        await("the device found") { found.isNotEmpty() }
        val advertisement = found.single()
        assertThat(advertisement.name).isEqualTo("PUMP-2")
        assertThat(advertisement.serviceUuids).containsExactly(service)
        assertThat(advertisement.manufacturerData[MANUFACTURER]).isEqualTo(byteArrayOf(0x11, 0x22, 0x33))
        collector.cancel()
    }

    // ---- The adapter and the permissions -------------------------------------------------------------------------

    @Test
    fun withBluetoothOffNothingStarts() = runBlocking {
        shadowOf(adapter).setEnabled(false)

        assertThat(failureOf(central.connect(DEVICE)).kind).isEqualTo(BleFailure.Kind.BLUETOOTH_OFF)
        try {
            central.scan(ScanFilter()).first()
            fail("a scan with Bluetooth off")
        } catch (e: BleException) {
            assertThat(e.failure.kind).isEqualTo(BleFailure.Kind.BLUETOOTH_OFF)
        }
        assertThat(central.adapterState.value).isEqualTo(AdapterState.OFF)
        assertThat(shadowOf(remote).bluetoothGatts).isEmpty()
    }

    @Test
    fun withoutThePermissionNothingIsTouched() = runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        assertThat(failureOf(central.connect(DEVICE)).kind).isEqualTo(BleFailure.Kind.NOT_AUTHORIZED)
        assertThat(central.adapterState.value).isEqualTo(AdapterState.UNAUTHORIZED)
        assertThat(shadowOf(remote).bluetoothGatts).isEmpty()
    }

    @Test
    fun withoutTheScanPermissionNoScanStarts() = runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_SCAN)

        try {
            central.scan(ScanFilter()).first()
            fail("a scan with no permission")
        } catch (e: BleException) {
            assertThat(e.failure.kind).isEqualTo(BleFailure.Kind.NOT_AUTHORIZED)
        }
        assertThat(shadowOf(adapter.bluetoothLeScanner).scanCallbacks).isEmpty()
    }

    // ---- Bonding ------------------------------------------------------------------------------------------------

    /** BONDING is not bonded: a pump that needs an encrypted link must get no command before the system says BONDED. */
    @Test
    fun aBondIsOkOnlyWhenTheSystemReportsBonded() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        shadowOf(remote).setCreatedBond(true)
        val result = scope.async { central.bonding!!.createBond(DEVICE, 5.seconds) }
        awaitBondReceiver()

        bondBroadcast(BluetoothDevice.BOND_BONDING)
        bondBroadcast(BluetoothDevice.BOND_BONDING)
        assertThat(result.isActive).isTrue()
        bondBroadcast(BluetoothDevice.BOND_BONDED)

        assertThat(result.await()).isEqualTo(BleResult.Ok(Unit))
    }

    @Test
    fun aBondThatEndsInNoneFails() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        shadowOf(remote).setCreatedBond(true)
        val result = scope.async { central.bonding!!.createBond(DEVICE, 5.seconds) }
        awaitBondReceiver()

        bondBroadcast(BluetoothDevice.BOND_BONDING)
        bondBroadcast(BluetoothDevice.BOND_NONE)

        assertThat(failureOf(result.await()).kind).isEqualTo(BleFailure.Kind.REFUSED)
    }

    /** Carelevo accepts the bond only between the connect and the discovery. */
    @Test
    fun aConnectThatBondsFirstWaitsForBondedBeforeTheDiscovery() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        shadowOf(remote).setCreatedBond(true)
        val result = scope.async { central.connect(DEVICE, ConnectOptions(timeout = 5.seconds, androidBondBeforeDiscovery = true)) }
        val gatt = awaitGatt()
        shadowOf(gatt).addDiscoverableService(BluetoothGattService(UUID.fromString(service.value), BluetoothGattService.SERVICE_TYPE_PRIMARY))
        shadowOf(remote).simulateGattConnectionChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        awaitBondReceiver()

        bondBroadcast(BluetoothDevice.BOND_BONDING)
        assertThat(result.isActive).isTrue()
        assertThat(gatt.services).isEmpty()
        bondBroadcast(BluetoothDevice.BOND_BONDED)

        assertThat(withTimeout(5.seconds) { result.await() } is BleResult.Ok).isTrue()
        assertThat(gatt.services).isNotEmpty()
    }

    /**
     * Seen on a real phone: the system had the bond 3 s before its broadcast reached the app, and the patch had dropped
     * the link 2.4 s after the bond. The discovery must start from the system's own state, not wait for the broadcast.
     */
    @Test
    fun aConnectThatBondsFirstStartsTheDiscoveryWhenTheSystemIsBondedBeforeItsBroadcast() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        shadowOf(remote).setCreatedBond(true) // the system has the bond at once; no broadcast is sent
        val result = scope.async { readingCentral.connect(DEVICE, ConnectOptions(timeout = 5.seconds, androidBondBeforeDiscovery = true)) }
        val gatt = awaitGatt()
        shadowOf(gatt).addDiscoverableService(BluetoothGattService(UUID.fromString(service.value), BluetoothGattService.SERVICE_TYPE_PRIMARY))
        shadowOf(remote).simulateGattConnectionChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)

        assertThat(withTimeout(2.seconds) { result.await() } is BleResult.Ok).isTrue()
        assertThat(gatt.services).isNotEmpty()
    }

    /** The same wait with the broadcasts alone never ends: the read is what ends it. */
    @Test
    fun aBondReadFromTheSystemIsOkWithoutItsBroadcast() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        shadowOf(remote).setCreatedBond(true) // the system has the bond at once; no broadcast is sent

        assertThat(withTimeoutOrNull(500.milliseconds) { central.bonding!!.createBond(DEVICE, 5.seconds) }).isNull()

        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        assertThat(withTimeout(2.seconds) { readingCentral.bonding!!.createBond(DEVICE, 5.seconds) }).isEqualTo(BleResult.Ok(Unit))
    }

    @Test
    fun aConnectWhoseBondFailsFailsAndClosesItsGatt() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        shadowOf(remote).setCreatedBond(true)
        val result = scope.async { central.connect(DEVICE, ConnectOptions(timeout = 5.seconds, androidBondBeforeDiscovery = true)) }
        val gatt = awaitGatt()
        shadowOf(remote).simulateGattConnectionChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        awaitBondReceiver()

        bondBroadcast(BluetoothDevice.BOND_BONDING)
        bondBroadcast(BluetoothDevice.BOND_NONE)

        assertThat(failureOf(withTimeout(5.seconds) { result.await() }).kind).isEqualTo(BleFailure.Kind.REFUSED)
        assertThat(shadowOf(gatt).isClosed).isTrue()
    }

    @Test
    fun aDeviceBondedBeforeIsOkAtOnce() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_BONDED)

        assertThat(central.bonding!!.createBond(DEVICE, 1.seconds)).isEqualTo(BleResult.Ok(Unit))
    }

    /**
     * The Bluetooth process sends with its own uid, not the system uid, so on a real phone a not-exported receiver never
     * hears BONDED and the pairing waits until its time limit. Robolectric delivers either way: only the flag shows it.
     */
    @Test
    fun theBondReceiverIsExportedSoTheBluetoothProcessCanReachIt() = runBlocking {
        shadowOf(remote).setBondState(BluetoothDevice.BOND_NONE)
        shadowOf(remote).setCreatedBond(true)
        val result = scope.async { central.bonding!!.createBond(DEVICE, 5.seconds) }
        awaitBondReceiver()

        assertExported(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        result.cancel()
    }

    /** The same rule for Bluetooth turned off or on. */
    @Test
    fun theAdapterReceiverIsExportedSoTheBluetoothProcessCanReachIt() {
        central.adapterState

        assertExported(BluetoothAdapter.ACTION_STATE_CHANGED)
    }

    // ---- Helpers ------------------------------------------------------------------------------------------------

    /** Connected and discovered, with the service of a pump. */
    private fun connected(): Pair<BleLink, BluetoothGatt> = runBlocking {
        val result = scope.async { central.connect(DEVICE, ConnectOptions(timeout = 5.seconds)) }
        val gatt = awaitGatt()
        val pumpService = BluetoothGattService(UUID.fromString(service.value), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val notifying = BluetoothGattCharacteristic(UUID.fromString(notify.characteristic.value), BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        notifying.addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        pumpService.addCharacteristic(notifying)
        pumpService.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID.fromString(write.characteristic.value),
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        pumpService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(read.characteristic.value), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        shadowOf(gatt).addDiscoverableService(pumpService)
        shadowOf(gatt).allowCharacteristicNotification(notifying)
        shadowOf(remote).simulateGattConnectionChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        val link = (withTimeout(5.seconds) { result.await() } as? BleResult.Ok)?.value ?: throw AssertionError("no link: ${result.await()}")
        link to gatt
    }

    private fun awaitGatt(): BluetoothGatt {
        await("a GATT") { shadowOf(remote).bluetoothGatts.isNotEmpty() }
        return shadowOf(remote).bluetoothGatts.last()
    }

    /** Robolectric shows no started read, so give the operation a moment to reach the stack. */
    private fun awaitStarted() = Thread.sleep(100)

    private fun characteristic(gatt: BluetoothGatt, id: CharacteristicId): BluetoothGattCharacteristic =
        gatt.getService(UUID.fromString(id.service.value)).getCharacteristic(UUID.fromString(id.characteristic.value))

    /** A real system reports the bond only after createBond(), which comes after the receiver is registered. */
    private fun awaitBondReceiver() =
        await("the bond receiver") { shadowOf(context).registeredReceivers.any { it.intentFilter.hasAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) } }

    private fun assertExported(action: String) {
        val receivers = shadowOf(context).registeredReceivers.filter { it.intentFilter.hasAction(action) }
        assertThat(receivers).isNotEmpty()
        receivers.forEach { assertThat(it.flags and Context.RECEIVER_EXPORTED).isEqualTo(Context.RECEIVER_EXPORTED) }
    }

    private fun bondBroadcast(state: Int) {
        context.sendBroadcast(
            Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, remote)
                .putExtra(BluetoothDevice.EXTRA_BOND_STATE, state)
        )
        shadowOf(Looper.getMainLooper()).idleFor(JavaDuration.ofMillis(50))
        Thread.sleep(50)
    }

    /** Builds a scan record from raw advertising bytes, as the system does. Hidden in the SDK, so by reflection. */
    private fun parse(bytes: ByteArray): ScanRecord =
        ScanRecord::class.java.getMethod("parseFromBytes", ByteArray::class.java).invoke(null, bytes) as ScanRecord

    private fun failureOf(result: BleResult<*>): BleFailure = (result as? BleResult.Failed)?.failure ?: throw AssertionError("not a failure: $result")

    private fun await(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > end) fail("Timeout waiting for: $what")
            shadowOf(Looper.getMainLooper()).idleFor(JavaDuration.ofMillis(10))
            Thread.sleep(5)
        }
    }

    private companion object {

        const val ADDRESS = "00:11:22:AA:BB:CC"
        val DEVICE = DeviceId(ADDRESS)
        const val MANUFACTURER = 0x4779
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
