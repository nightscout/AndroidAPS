package app.aaps.implementation.pump.ble

import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.DeviceId
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.create

/*
 * The parts of the iOS central that need no radio, so the simulator tests can run them.
 */

/**
 * What CoreBluetooth found, in the shape every platform uses. iOS gives the manufacturer data as one block that starts
 * with the company id (little endian), as it is on the air; Android splits it already.
 */
internal fun advertisementOf(device: DeviceId, peripheralName: String?, data: Map<Any?, *>, rssi: Int): Advertisement {
    val manufacturer = (data[CBAdvertisementDataManufacturerDataKey] as? NSData)?.toByteArray()
    return Advertisement(
        device = device,
        name = data[CBAdvertisementDataLocalNameKey] as? String ?: peripheralName,
        rssi = rssi,
        serviceUuids = (data[CBAdvertisementDataServiceUUIDsKey] as? List<*>).orEmpty().mapNotNull { (it as? CBUUID)?.toBleUuid() },
        manufacturerData =
            if (manufacturer == null || manufacturer.size < COMPANY_ID_LENGTH) emptyMap()
            else mapOf(((manufacturer[0].toInt() and 0xff) or ((manufacturer[1].toInt() and 0xff) shl 8)) to manufacturer.copyOfRange(COMPANY_ID_LENGTH, manufacturer.size)),
        serviceData = (data[CBAdvertisementDataServiceDataKey] as? Map<*, *>).orEmpty().entries.mapNotNull { (uuid, bytes) ->
            val key = (uuid as? CBUUID)?.toBleUuid() ?: return@mapNotNull null
            val value = (bytes as? NSData)?.toByteArray() ?: return@mapNotNull null
            key to value
        }.toMap()
    )
}

/** CoreBluetooth writes 16-bit UUIDs short, as "FFF0"; [BleUuid.of] expands them. */
internal fun CBUUID.toBleUuid(): BleUuid = BleUuid.of(UUIDString)

internal fun BleUuid.toCBUUID(): CBUUID = CBUUID.UUIDWithString(value)

/** An iOS device id is the peripheral's identifier, an upper case UUID. */
internal fun isPeripheralIdentifier(value: String): Boolean = PERIPHERAL_IDENTIFIER.matches(value)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = bytes ?: return ByteArray(0)
    return if (size == 0) ByteArray(0) else bytes.reinterpret<ByteVar>().readBytes(size)
}

private const val COMPANY_ID_LENGTH = 2
private val PERIPHERAL_IDENTIFIER = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")
