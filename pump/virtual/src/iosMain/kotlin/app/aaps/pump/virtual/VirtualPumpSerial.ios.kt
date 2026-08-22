package app.aaps.pump.virtual

import platform.UIKit.UIDevice

/**
 * `identifierForVendor` is the closest counterpart to the Firebase installation id used on Android:
 * stable for as long as the app stays installed, and scoped to this vendor rather than the device.
 * It is null only while the device is locked before first unlock, which cannot happen at the point a
 * pump reports its serial.
 */
internal actual fun virtualPumpSerialNumber(): String =
    UIDevice.currentDevice.identifierForVendor?.UUIDString ?: ""
