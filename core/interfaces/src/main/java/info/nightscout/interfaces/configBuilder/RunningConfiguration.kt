package info.nightscout.interfaces.configBuilder

import info.nightscout.sdk.remotemodel.RemoteDeviceStatus
import org.json.JSONObject

interface RunningConfiguration {

    // called in AAPS mode only
    fun configuration(): JSONObject

    // called in NSClient mode only
    fun apply(configuration: RemoteDeviceStatus.Configuration)
}