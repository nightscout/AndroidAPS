package app.aaps.pump.apex.misc

import app.aaps.core.keys.Preferences
import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.keys.ApexStringKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApexDeviceInfoImpl @Inject constructor(
    val preferences: Preferences
): ApexDeviceInfo {
    override var serialNumber: String
        get() = preferences.get(ApexStringKey.SerialNumber)
        set(s) = preferences.put(ApexStringKey.SerialNumber, s)
}