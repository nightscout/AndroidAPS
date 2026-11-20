package app.aaps.plugins.dexcomoneplus.oem

/**
 * Who made the phone this is running on, as the platform reports it.
 *
 * Only [DeviceProfileRegistry] needs it, to pick the BLE timings a given OEM's stack needs. The
 * names are matched case insensitively, so a platform may return them in any case.
 */
internal expect fun onePlusDeviceManufacturer(): String

/**
 * The phone's model name, as the platform reports it. Logged with the chosen profile so a field
 * report says which phone it came from; nothing is decided on it today.
 */
internal expect fun onePlusDeviceModel(): String
