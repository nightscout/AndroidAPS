package app.aaps.core.interfaces.receivers

@Suppress("unused")
interface Intents {

    companion object {

        // AAPS -> AAPS
        const val AAPS_CLIENT_WEAR_DATA = "app.aaps.aapsclient.weardata"

        // AAPS -> Xdrip
        const val ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT"
        const val ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE"
        const val ACTION_NEW_DEVICE_STATUS = "info.nightscout.client.NEW_DEVICESTATUS"
        const val ACTION_NEW_FOOD = "info.nightscout.client.NEW_FOOD"
        const val ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV"

        const val EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline"
        const val ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline"
        const val RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE"

        // AAPS -> xDrip 640G mode
        const val XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR"

        // xDrip -> AAPS
        const val ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"
        const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        const val EXTRA_SENSOR_BATTERY = "com.eveningoutpost.dexdrip.Extras.SensorBattery"
        const val EXTRA_SENSOR_STARTED_AT = "com.eveningoutpost.dexdrip.Extras.SensorStartedAt"
        const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"
        const val EXTRA_RAW = "com.eveningoutpost.dexdrip.Extras.Raw"
        const val XDRIP_DATA_SOURCE = "com.eveningoutpost.dexdrip.Extras.SourceInfo"
        const val XDRIP_DATA_SOURCE_DESCRIPTION = "com.eveningoutpost.dexdrip.Extras.SourceDesc"
        const val ACTION_NEW_BG_ESTIMATE_NO_DATA = "com.eveningoutpost.dexdrip.BgEstimateNoData"
        const val NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR"
        const val ACTION_REMOTE_CALIBRATION = "com.eveningoutpost.dexdrip.NewCalibration"
        const val GLIMP_BG = "it.ct.glicemia.ACTION_GLUCOSE_MEASURED"
        const val DEXCOM_BG = "com.dexcom.cgm.EXTERNAL_BROADCAST"
        const val DEXCOM_G7_BG = "com.dexcom.g7.EXTERNAL_BROADCAST"
        const val POCTECH_BG = "com.china.poctech.data"
        const val TOMATO_BG = "com.fanqies.tomatofn.BgEstimate"

        // Broadcast status
        const val AAPS_BROADCAST = "info.nightscout.androidaps.status"
        // Patched Ottai App -> AAPS (International)
        const val OTTAI_APP = "info.nightscout.androidaps.action.OTTAI_APP"
        // Patched Ottai App -> AAPS (China)
        const val OTTAI_APP_CN = "cn.diyaps.sharing.OT_APP"

        // Patched SI App -> AAPS
        const val SI_APP = "cn.diyaps.sharing.SI_APP"

        // Patched Sino App -> AAPS
        const val SINO_APP = "cn.diyaps.sharing.SINO_APP"
        // Patched Syai Tag App -> AAPS
        const val SYAI_APP = "info.nightscout.androidaps.action.SYAI_TAG_APP"
    }
}
