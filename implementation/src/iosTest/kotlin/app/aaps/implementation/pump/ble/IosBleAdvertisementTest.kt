package app.aaps.implementation.pump.ble

import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.DeviceId
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the simulator can check of the iOS central with no radio: CoreBluetooth's data in the shape of every platform. */
class IosBleAdvertisementTest {

    private val device = DeviceId("12345678-1234-1234-1234-123456789ABC")

    @Test
    fun theAdvertisementOfAPumpIsRead() {
        val data = mapOf<Any?, Any?>(
            CBAdvertisementDataLocalNameKey to "PUMP-2",
            CBAdvertisementDataServiceUUIDsKey to listOf(CBUUID.UUIDWithString("FFF0")),
            CBAdvertisementDataManufacturerDataKey to byteArrayOf(0x79, 0x47, 0x11, 0x22, 0x33).toNSData(),
            CBAdvertisementDataServiceDataKey to mapOf(CBUUID.UUIDWithString("FFF1") to byteArrayOf(7).toNSData())
        )

        val advertisement = advertisementOf(device, "cached name", data, -40)

        assertEquals("PUMP-2", advertisement.name)
        assertEquals(-40, advertisement.rssi)
        assertEquals(listOf(BleUuid.of("fff0")), advertisement.serviceUuids)
        assertContentEquals(byteArrayOf(0x11, 0x22, 0x33), advertisement.manufacturerData[0x4779])
        assertContentEquals(byteArrayOf(7), advertisement.serviceData[BleUuid.of("fff1")])
    }

    @Test
    fun withNoAdvertisedNameThePeripheralNameIsUsed() {
        assertEquals("cached name", advertisementOf(device, "cached name", emptyMap<Any?, Any?>(), -40).name)
    }

    @Test
    fun manufacturerDataShorterThanTheCompanyIdIsIgnored() {
        val data = mapOf<Any?, Any?>(CBAdvertisementDataManufacturerDataKey to byteArrayOf(0x79).toNSData())

        assertTrue(advertisementOf(device, null, data, -40).manufacturerData.isEmpty())
    }

    @Test
    fun aLongUuidKeepsItsValueBothWays() {
        val uuid = BleUuid.of("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

        assertEquals(uuid, uuid.toCBUUID().toBleUuid())
        assertEquals(BleUuid.of("2902"), CBUUID.UUIDWithString("2902").toBleUuid())
    }

    @Test
    fun onlyAnIdentifierIsAnIosDeviceId() {
        assertTrue(isPeripheralIdentifier(device.value))
        assertFalse(isPeripheralIdentifier("00:11:22:AA:BB:CC"))
    }
}
