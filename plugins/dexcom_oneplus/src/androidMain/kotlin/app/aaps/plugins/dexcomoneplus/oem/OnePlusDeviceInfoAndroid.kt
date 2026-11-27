package app.aaps.plugins.dexcomoneplus.oem

import android.os.Build

/**
 * `Build.MANUFACTURER` and `Build.MODEL` are declared non-null by the framework, and are null on
 * the unit test classpath, where every `android.os.Build` field is a stub. The empty string is
 * read by [DeviceProfileRegistry] as "no OEM I know", which is the generic profile - the same
 * answer an unknown phone gets, and the right one when the phone will not say who made it.
 */
internal actual fun onePlusDeviceManufacturer(): String = Build.MANUFACTURER ?: ""

internal actual fun onePlusDeviceModel(): String = Build.MODEL ?: ""
