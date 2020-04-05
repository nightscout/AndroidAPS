package info.nightscout.androidaps.services;

public interface Intents {
    // NSClient -> App
    String ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT";
    String ACTION_CHANGED_TREATMENT = "info.nightscout.client.CHANGED_TREATMENT";
    String ACTION_REMOVED_TREATMENT = "info.nightscout.client.REMOVED_TREATMENT";
    String ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE";
    String ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV";
    String ACTION_NEW_MBG = "info.nightscout.client.NEW_MBG";
    String ACTION_NEW_CAL = "info.nightscout.client.NEW_CAL";

    // xDrip -> App
    String ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate";
    String EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate";
    String EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope";
    String EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName";
    String EXTRA_SENSOR_BATTERY = "com.eveningoutpost.dexdrip.Extras.SensorBattery";
    String EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time";
    String EXTRA_RAW = "com.eveningoutpost.dexdrip.Extras.Raw";
    String XDRIP_DATA_SOURCE_DESCRIPTION = "com.eveningoutpost.dexdrip.Extras.SourceDesc";


    String ACTION_NEW_BG_ESTIMATE_NO_DATA = "com.eveningoutpost.dexdrip.BgEstimateNoData";

    String NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";

    String ACTION_REMOTE_CALIBRATION = "com.eveningoutpost.dexdrip.NewCalibration";

    String GLIMP_BG = "it.ct.glicemia.ACTION_GLUCOSE_MEASURED";

    String DEXCOM_BG = "com.dexcom.cgm.EXTERNAL_BROADCAST";
    String EVERSENSE_BG = "com.senseonics.AndroidAPSEventSubscriber.BROADCAST";

    String POCTECH_BG = "com.china.poctech.data";
    String TOMATO_BG = "com.fanqies.tomatofn.BgEstimate";

    // Broadcast status
    String AAPS_BROADCAST = "info.nightscout.androidaps.status";
}
