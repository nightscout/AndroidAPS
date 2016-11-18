package info.nightscout.androidaps.plugins.Wear.wearintegration;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.plugins.Wear.WearPlugin;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DecimalFormatter;

public class WatchUpdaterService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    public static final String ACTION_RESEND = WatchUpdaterService.class.getName().concat(".Resend");
    public static final String ACTION_OPEN_SETTINGS = WatchUpdaterService.class.getName().concat(".OpenSettings");
    public static final String ACTION_SEND_STATUS = WatchUpdaterService.class.getName().concat(".SendStatus");
    public static final String ACTION_SEND_BASALS = WatchUpdaterService.class.getName().concat(".SendBasals");


    private GoogleApiClient googleApiClient;
    public static final String WEARABLE_DATA_PATH = "/nightscout_watch_data";
    public static final String WEARABLE_RESEND_PATH = "/nightscout_watch_data_resend";
    private static final String OPEN_SETTINGS_PATH = "/openwearsettings";
    private static final String NEW_STATUS_PATH = "/sendstatustowear";
    public static final String BASAL_DATA_PATH = "/nightscout_watch_basal";


    boolean wear_integration = false;
    SharedPreferences mPrefs;

    @Override
    public void onCreate() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        listenForChangeInSettings();
        setSettings();
        if (wear_integration) {
            googleApiConnect();
        }
    }

    public void listenForChangeInSettings() {
        WearPlugin.registerWatchUpdaterService(this);
    }

    public void setSettings() {
        wear_integration = WearPlugin.isEnabled();
        if (wear_integration) {
            googleApiConnect();
        }
    }

    public void googleApiConnect() {
        if(googleApiClient != null && (googleApiClient.isConnected() || googleApiClient.isConnecting())) { googleApiClient.disconnect(); }
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        Wearable.MessageApi.addListener(googleApiClient, this);
        if (googleApiClient.isConnected()) {
            Log.d("WatchUpdater", "API client is connected");
        } else {
            googleApiClient.connect();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        double timestamp = 0;
        if (intent != null) {
            timestamp = intent.getDoubleExtra("timestamp", 0);
        }

        String action = null;
        if (intent != null) {
            action = intent.getAction();
        }

        if (wear_integration) {
            if (googleApiClient.isConnected()) {
                if (ACTION_RESEND.equals(action)) {
                    resendData();
                } else if (ACTION_OPEN_SETTINGS.equals(action)) {
                    sendNotification();
                } else if (ACTION_SEND_STATUS.equals(action)) {
                    sendStatus();
                } else if (ACTION_SEND_BASALS.equals(action)) {
                    sendBasals();
                } else {
                    sendData();
                }
            } else {
                googleApiClient.connect();
            }
        }

        return START_STICKY;
    }


    @Override
    public void onConnected(Bundle connectionHint) {
        sendData();
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (wear_integration) {
            if (event != null && event.getPath().equals(WEARABLE_RESEND_PATH))
                resendData();
        }
    }

    public void sendData() {

        BgReading lastBG = MainApp.getDbHelper().lastBg();
        if (lastBG != null) {
            DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();

            if(googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) { googleApiConnect(); }
            if (wear_integration) {
                new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient).execute(dataMapSingleBG(lastBG, glucoseStatus));
            }
        }
    }

    private DataMap dataMapSingleBG(BgReading lastBG, DatabaseHelper.GlucoseStatus glucoseStatus) {
        Double highMark = 170d; //in mg/dl TODO: dynamically read this?
        Double lowMark = 70d;

        long sgvLevel = 0l;
        if (lastBG.value > highMark) {
            sgvLevel = 1;
        } else if (lastBG.value < lowMark) {
            sgvLevel = -1;
        }
        DataMap dataMap = new DataMap();

        int battery = getBatteryLevel(getApplicationContext());
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        dataMap.putString("sgvString", lastBG.valueToUnitsToString(profile.getUnits()));
        dataMap.putDouble("timestamp", lastBG.getTimeIndex()); //TODO: change that to long (was like that in NW)
        if(glucoseStatus == null) {
            dataMap.putString("slopeArrow", "NONE" );
            dataMap.putString("delta", "");
        } else {
            dataMap.putString("slopeArrow", slopeArrow(glucoseStatus.delta));
            dataMap.putString("delta", deltastring(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, profile.getUnits()));
        }
        dataMap.putString("battery", "" + battery);
        dataMap.putLong("sgvLevel", sgvLevel);
        dataMap.putInt("batteryLevel", (battery>=30)?1:0);
        dataMap.putDouble("sgvDouble", lastBG.value);
        dataMap.putDouble("high", highMark);
        dataMap.putDouble("low", lowMark);
        //TODO Adrian use for status string?
        //dataMap.putString("rawString", threeRaw((prefs.getString("units", "mgdl").equals("mgdl"))));
        return dataMap;
    }

    private String deltastring(double deltaMGDL, double deltaMMOL, String units) {
        String deltastring = "";
        if (deltaMGDL >=0){
            deltastring += "+";
        } else{
            deltastring += "-";

        }
        if (units.equals(Constants.MGDL)){
            deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMGDL));
        }
        else {
            deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMMOL));
        }
        return deltastring;
    }

    private String slopeArrow(double delta) {
        String arrow = "NONE";
        if (delta <= (-3.5*5)) {
            arrow = "DoubleDown";
        } else if (delta <= (-2*5)) {
            arrow = "SingleDown";
        } else if (delta <= (-1*5)) {
            arrow = "FortyFiveDown";
        } else if (delta <= (1*5)) {
            arrow = "Flat";
        } else if (delta <= (2*5)) {
            arrow = "FortyFiveUp";
        } else if (delta <= (3.5*5)) {
            arrow = "SingleUp";
        } else if (delta <= (40*5)) {
            arrow = "DoubleUp";
        }
        return arrow;
    }


    private void resendData() {
        if(googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) { googleApiConnect(); }
        long startTime = System.currentTimeMillis() - (long)(60000 * 60 * 5.5);
        BgReading last_bg = MainApp.getDbHelper().lastBg();

        if (last_bg == null) return;

        List<BgReading> graph_bgs =  MainApp.getDbHelper().getDataFromTime(startTime);
        DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();

        if (!graph_bgs.isEmpty()) {
            DataMap entries = dataMapSingleBG(last_bg, glucoseStatus);
            final ArrayList<DataMap> dataMaps = new ArrayList<>(graph_bgs.size());
            for (BgReading bg : graph_bgs) {
                dataMaps.add(dataMapSingleBG(bg, glucoseStatus));
            }
            entries.putDataMapArrayList("entries", dataMaps);
            new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient).execute(entries);
        }
        sendBasals();
        sendStatus();
    }

    private void sendBasals() {
        if(googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) { googleApiConnect(); }

        long now = System.currentTimeMillis();
        long startTimeWindow = now - (long)(60000 * 60 * 5.5);



        ArrayList<DataMap> basals = new ArrayList<>();

        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();


        long beginBasalSegmentTime = startTimeWindow;
        long endBasalSegmentTime = startTimeWindow;
        double beginValue = profile.getBasal(NSProfile.secondsFromMidnight(new Date(beginBasalSegmentTime)));
        double endValue = profile.getBasal(NSProfile.secondsFromMidnight(new Date(endBasalSegmentTime)));


        for(;endBasalSegmentTime<now;endBasalSegmentTime+= 5*60*1000){
            endValue = profile.getBasal(NSProfile.secondsFromMidnight(new Date(endBasalSegmentTime)));
            if(endValue != beginValue){
                //push the segment we recently left
                basals.add(basalMap(beginBasalSegmentTime, endBasalSegmentTime, beginValue));

                //begin new Basal segment
                beginBasalSegmentTime = endBasalSegmentTime;
                beginValue = endValue;
            }
        }
        if(beginBasalSegmentTime != endBasalSegmentTime){
            //push the remaining segment
            basals.add(basalMap(beginBasalSegmentTime, endBasalSegmentTime, beginValue));
        }






        //TODO: Adrian: replace fake data
        long from = startTimeWindow;
        long to = (now + from)/2;
        double amount = 0.5;
        //basals.add(basalMap(from, to, amount));

        //from = to;
        //to = now;
        //amount = 0.8;
        //basals.add(basalMap(from, to, amount));



        ArrayList<DataMap> temps = new ArrayList<>();
        from = (long)(startTimeWindow + (1/8d)*(now - startTimeWindow));
        double fromBasal = 0.5;
        to = (long)(startTimeWindow + (2/8d)*(now - startTimeWindow));
        double toBasal = 0.5;
        amount = 3;
        temps.add(tempDatamap(from, fromBasal, to, toBasal, amount));


        from = (long)(startTimeWindow + (6/8d)*(now - startTimeWindow));
        fromBasal = 0.8;
        to = (long)(startTimeWindow + (7/8d)*(now - startTimeWindow));
        toBasal = 0.8;
        amount = 0;
        temps.add(tempDatamap(from, fromBasal, to, toBasal, amount));




        DataMap dm = new DataMap();
        dm.putDataMapArrayList("basals", basals);
        dm.putDataMapArrayList("temps", temps);

        new SendToDataLayerThread(BASAL_DATA_PATH, googleApiClient).execute(dm);
    }

    private DataMap tempDatamap(long startTime, double startBasal, long to, double toBasal, double amount) {
        DataMap dm = new DataMap();
        dm.putLong("starttime", startTime);
        dm.putDouble("startBasal", startBasal);
        dm.putLong("endtime", to);
        dm.putDouble("endbasal", toBasal);
        dm.putDouble("amount", amount);
        return dm;
    }

    private DataMap basalMap(long startTime, long endTime, double amount) {
        DataMap dm = new DataMap();
        dm.putLong("starttime", startTime);
        dm.putLong("endtime", endTime);
        dm.putDouble("amount", amount);
        return dm;
    }


    private void sendNotification() {
        if (googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(OPEN_SETTINGS_PATH);
            //unique content
            dataMapRequest.getDataMap().putDouble("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("openSettings", "openSettings");
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("OpenSettings", "No connection to wearable available!");
        }
    }

    private void sendStatus() {
        if (googleApiClient.isConnected()) {

            String status = "";

            //TODO Adrian: Setting if short or medium string.

            boolean shortString = true;

            //Temp basal
            PumpInterface pump = MainApp.getConfigBuilder();

            if (pump.isTempBasalInProgress()) {
                TempBasal activeTemp = pump.getTempBasal();
                if (shortString) {
                    status += activeTemp.toStringShort();
                } else {
                    status += activeTemp.toStringMedium();
                }
            }

            //IOB
            MainApp.getConfigBuilder().getActiveTreatments().updateTotalIOB();
            IobTotal bolusIob = MainApp.getConfigBuilder().getActiveTreatments().getLastCalculation().round();
            if (bolusIob == null) bolusIob = new IobTotal();
            MainApp.getConfigBuilder().getActiveTempBasals().updateTotalIOB();
            IobTotal basalIob = MainApp.getConfigBuilder().getActiveTempBasals().getLastCalculation().round();
            if (basalIob == null) basalIob = new IobTotal();
            status += (shortString?"":(getString(R.string.treatments_iob_label_string) + " ")) + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "|"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";

            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NEW_STATUS_PATH);
            //unique content
            dataMapRequest.getDataMap().putDouble("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("externalStatusString", status);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("SendStatus", "No connection to wearable available!");
        }
    }

    @Override
    public void onDestroy() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        WearPlugin.unRegisterWatchUpdaterService();
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }

    public static int getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) {
            return 50;
        }
        return (int)(((float)level / (float)scale) * 100.0f);
    }
}
