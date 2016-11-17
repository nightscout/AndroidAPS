package info.nightscout.androidaps.Services;

public interface Intents {
    // NSClient -> App
    String ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT";
    String ACTION_CHANGED_TREATMENT = "info.nightscout.client.CHANGED_TREATMENT";
    String ACTION_REMOVED_TREATMENT = "info.nightscout.client.REMOVED_TREATMENT";
    String ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE";
    String ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV";
    String ACTION_NEW_DEVICESTATUS = "info.nightscout.client.NEW_DEVICESTATUS";
    String ACTION_NEW_MBG = "info.nightscout.client.NEW_MBG";
    String ACTION_NEW_CAL = "info.nightscout.client.NEW_CAL";
    String ACTION_NEW_STATUS = "info.nightscout.client.NEW_STATUS";


    // App -> NSClient
    String ACTION_DATABASE = "info.nightscout.client.DBACCESS";
    String ACTION_RESTART = "info.nightscout.client.RESTART";

    // xDrip -> App
    String RECEIVER_PERMISSION = "info.nightscout.androidaps.permissions.RECEIVE_BG_ESTIMATE";

    String ACTION_NEW_BG_ESTIMATE = "info.nightscout.androidaps.BgEstimate";
    String EXTRA_BG_ESTIMATE = "info.nightscout.androidaps.Extras.BgEstimate";
    String EXTRA_BG_SLOPE = "info.nightscout.androidaps.Extras.BgSlope";
    String EXTRA_BG_SLOPE_NAME = "info.nightscout.androidaps.Extras.BgSlopeName";
    String EXTRA_SENSOR_BATTERY = "info.nightscout.androidaps.Extras.SensorBattery";
    String EXTRA_TIMESTAMP = "info.nightscout.androidaps.Extras.Time";
    String EXTRA_RAW = "info.nightscout.androidaps.Extras.Raw";

    String ACTION_NEW_BG_ESTIMATE_NO_DATA = "info.nightscout.androidaps.BgEstimateNoData";
}
