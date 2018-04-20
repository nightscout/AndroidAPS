package info.nightscout.androidaps.Services;

public interface Intents {
    // NSClient -> App
    String ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT";
    String ACTION_CHANGED_TREATMENT = "info.nightscout.client.CHANGED_TREATMENT";
    String ACTION_REMOVED_TREATMENT = "info.nightscout.client.REMOVED_TREATMENT";
    String ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE";
    String ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV";
    String ACTION_NEW_DEVICESTATUS = "info.nightscout.client.NEW_DEVICESTATUS";
    String ACTION_NEW_FOOD = "info.nightscout.client.NEW_FOOD";
    String ACTION_CHANGED_FOOD = "info.nightscout.client.CHANGED_FOOD";
    String ACTION_REMOVED_FOOD = "info.nightscout.client.REMOVED_FOOD";
    String ACTION_NEW_MBG = "info.nightscout.client.NEW_MBG";
    String ACTION_NEW_CAL = "info.nightscout.client.NEW_CAL";
    String ACTION_NEW_STATUS = "info.nightscout.client.NEW_STATUS";
    String ACTION_QUEUE_STATUS = "info.nightscout.client.QUEUE_STATUS";
    String ACTION_ANNOUNCEMENT = "info.nightscout.client.ANNOUNCEMENT";
    String ACTION_ALARM = "info.nightscout.client.ALARM";
    String ACTION_URGENT_ALARM = "info.nightscout.client.URGENT_ALARM";
    String ACTION_CLEAR_ALARM = "info.nightscout.client.CLEAR_ALARM";


    // App -> NSClient
    String ACTION_DATABASE = "info.nightscout.client.DBACCESS";
    String ACTION_RESTART = "info.nightscout.client.RESTART";
    String ACTION_RESEND = "info.nightscout.client.RESEND";
    String ACTION_ACK_ALARM = "info.nightscout.client.ACK_ALARM";

    // xDrip -> App
    String RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_BG_ESTIMATE";

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

    String DEXCOMG5_BG = "com.dexcom.cgm.DATA";
}
