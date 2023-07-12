package info.nightscout.androidaps.receivers

@Suppress("unused")
interface Intents {

    companion object {

        // AAPS -> Xdrip
        const val ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT"
        const val ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE"
        const val ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV"

        // AAPS -> xDrip 640G mode
        const val XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR"

        // xDrip -> AAPS
        const val ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"
        const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        const val EXTRA_SENSOR_BATTERY = "com.eveningoutpost.dexdrip.Extras.SensorBattery"
        const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"
        const val EXTRA_RAW = "com.eveningoutpost.dexdrip.Extras.Raw"
        const val XDRIP_DATA_SOURCE_DESCRIPTION = "com.eveningoutpost.dexdrip.Extras.SourceDesc"
        const val ACTION_NEW_BG_ESTIMATE_NO_DATA = "com.eveningoutpost.dexdrip.BgEstimateNoData"
        const val NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR"
        const val ACTION_REMOTE_CALIBRATION = "com.eveningoutpost.dexdrip.NewCalibration"
        const val GLIMP_BG = "it.ct.glicemia.ACTION_GLUCOSE_MEASURED"
        const val DEXCOM_BG = "com.dexcom.cgm.EXTERNAL_BROADCAST"
        const val EVERSENSE_BG = "com.senseonics.AndroidAPSEventSubscriber.BROADCAST"
        const val POCTECH_BG = "com.china.poctech.data"
        const val TOMATO_BG = "com.fanqies.tomatofn.BgEstimate"

        // Aidex -> AAPS
        var AIDEX_NEW_BG_ESTIMATE = "com.microtechmd.cgms.aidex.action.BgEstimate"
        var AIDEX_BG_TYPE = "com.microtechmd.cgms.aidex.BgType"
        var AIDEX_BG_VALUE = "com.microtechmd.cgms.aidex.BgValue"
        var AIDEX_BG_SLOPE_NAME = "com.microtechmd.cgms.aidex.BgSlopeName"
        var AIDEX_TIMESTAMP = "com.microtechmd.cgms.aidex.Time" // epoch in ms
        var AIDEX_TRANSMITTER_SN = "com.microtechmd.cgms.aidex.TransmitterSerialNumber"
        var AIDEX_SENSOR_ID = "com.microtechmd.cgms.aidex.SensorId"

        // Patched SI App -> AAPS
        const val SIAPP_BG = "cn.diyaps.sharing.SI_APP"

        // Patched Sino App -> AAPS
        const val SINOAPP_BG = "cn.diyaps.sharing.SINO_APP"

        // Broadcast status
        const val AAPS_BROADCAST = "info.nightscout.androidaps.status"
    }
}