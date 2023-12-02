package app.aaps.core.nssdk.interfaces

import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import org.json.JSONObject

interface RunningConfiguration {

    // called in AAPS mode only
    fun configuration(): JSONObject

    // called in NSClient mode only
    fun apply(configuration: NSDeviceStatus.Configuration)
}