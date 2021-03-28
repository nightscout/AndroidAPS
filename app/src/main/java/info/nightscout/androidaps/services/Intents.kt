package info.nightscout.androidaps.services

@Suppress("unused")
interface Intents {

    companion object {

        // AAPS -> Xdrip
        const val ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT"
        const val ACTION_CHANGED_TREATMENT = "info.nightscout.client.CHANGED_TREATMENT"
        const val ACTION_REMOVED_TREATMENT = "info.nightscout.client.REMOVED_TREATMENT"
        const val ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE"
        const val ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV"
        const val ACTION_NEW_MBG = "info.nightscout.client.NEW_MBG"
        const val ACTION_NEW_CAL = "info.nightscout.client.NEW_CAL"

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

        // Broadcast status
        const val AAPS_BROADCAST = "info.nightscout.androidaps.status"
    }
}