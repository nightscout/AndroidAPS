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
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.ToastUtils;

public class WatchUpdaterService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    public static final String ACTION_RESEND = WatchUpdaterService.class.getName().concat(".Resend");
    public static final String ACTION_OPEN_SETTINGS = WatchUpdaterService.class.getName().concat(".OpenSettings");
    public static final String ACTION_SEND_STATUS = WatchUpdaterService.class.getName().concat(".SendStatus");

    private GoogleApiClient googleApiClient;
    public static final String WEARABLE_DATA_PATH = "/nightscout_watch_data";
    public static final String WEARABLE_RESEND_PATH = "/nightscout_watch_data_resend";
    private static final String OPEN_SETTINGS_PATH = "/openwearsettings";
    private static final String NEW_STATUS_PATH = "/sendstatustowear";


    boolean wear_integration = false;
    boolean pebble_integration = false;
    SharedPreferences mPrefs;
    SharedPreferences.OnSharedPreferenceChangeListener mPreferencesListener;

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
        mPreferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                setSettings();
            }
        };
        mPrefs.registerOnSharedPreferenceChangeListener(mPreferencesListener);
    }

    public void setSettings() {
        //TODO Adrian: check if wear plugin is active or better: Never call from Plugin if not enabled!
        wear_integration = true; //mPrefs.getBoolean("wear_sync", false);
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
                    sendStatus(intent.getStringExtra("externalStatusString"));
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
            /**bgView.setText(lastBG.valueToUnitsToString(profile.getUnits()));
            DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();
            if (glucoseStatus != null)
                deltaView.setText("Δ " + NSProfile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units);
            BgReading.units = profile.getUnits();**/

            DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();

            if(googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) { googleApiConnect(); }
            if (wear_integration) {
                new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient).execute(dataMapSingleBG(lastBG, glucoseStatus));
            }
        }

        /**
        BgReading bg = BgReading.last();
        if (bg != null) {
            if(googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) { googleApiConnect(); }
            if (wear_integration) {
                new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient).execute(dataMap(bg, mPrefs, new BgGraphBuilder(getApplicationContext())));
            }
        }*/
        ToastUtils.showToastInUiThread(this, "sendData()");
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
        dataMap.putString("slopeArrow", slopeArrow(glucoseStatus.delta));
        dataMap.putDouble("timestamp", lastBG.getTimeIndex()); //TODO: change that to long (was like that in NW)
        dataMap.putString("delta", NSProfile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, profile.getUnits()));
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
        /**if(googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) { googleApiConnect(); }
        long startTime = new Date().getTime() - (60000 * 60 * 24);
        BgReading last_bg = BgReading.last();
        List<BgReading> graph_bgs = BgReading.latestForGraph(60, startTime);
        BgGraphBuilder bgGraphBuilder = new BgGraphBuilder(getApplicationContext());
        if (!graph_bgs.isEmpty()) {
            DataMap entries = dataMap(last_bg, mPrefs, bgGraphBuilder);
            final ArrayList<DataMap> dataMaps = new ArrayList<>(graph_bgs.size());
            for (BgReading bg : graph_bgs) {
                dataMaps.add(dataMap(bg, mPrefs, bgGraphBuilder));
            }
            entries.putDataMapArrayList("entries", dataMaps);

            new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient).execute(entries);
        }*/
        ToastUtils.showToastInUiThread(this, "resendData()");
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

    private void sendStatus(String status) {
        if (googleApiClient.isConnected()) {
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

    /**private DataMap dataMap(BgReading bg, SharedPreferences sPrefs, BgGraphBuilder bgGraphBuilder) {
        Double highMark = Double.parseDouble(sPrefs.getString("highValue", "170"));
        Double lowMark = Double.parseDouble(sPrefs.getString("lowValue", "70"));
        DataMap dataMap = new DataMap();

        int battery = BgSendQueue.getBatteryLevel(getApplicationContext());

        dataMap.putString("sgvString", bgGraphBuilder.unitized_string(bg.calculated_value));
        dataMap.putString("slopeArrow", bg.slopeArrow());
        dataMap.putDouble("timestamp", bg.timestamp); //TODO: change that to long (was like that in NW)
        dataMap.putString("delta", bgGraphBuilder.unitizedDeltaString(true, true));
        dataMap.putString("battery", "" + battery);
        dataMap.putLong("sgvLevel", sgvLevel(bg.calculated_value, sPrefs, bgGraphBuilder));
        dataMap.putInt("batteryLevel", (battery>=30)?1:0);
        dataMap.putDouble("sgvDouble", bg.calculated_value);
        dataMap.putDouble("high", inMgdl(highMark, sPrefs));
        dataMap.putDouble("low", inMgdl(lowMark, sPrefs));
        //TODO: Add raw again
        //dataMap.putString("rawString", threeRaw((prefs.getString("units", "mgdl").equals("mgdl"))));
        return dataMap;
    }


    // TODO: Integrate these helper methods into BGGraphBuilder.
    // TODO: clean them up  (no "if(boolean){return true; else return false;").
    // TODO: Make the needed methods in BgGraphBuilder static.

    public long sgvLevel(double sgv_double, SharedPreferences prefs, BgGraphBuilder bgGB) {
        Double highMark = Double.parseDouble(prefs.getString("highValue", "170"));
        Double lowMark = Double.parseDouble(prefs.getString("lowValue", "70"));
        if(bgGB.unitized(sgv_double) >= highMark) {
            return 1;
        } else if (bgGB.unitized(sgv_double) >= lowMark) {
            return 0;
        } else {
            return -1;
        }
    }

    public double inMgdl(double value, SharedPreferences sPrefs) {
        if (!doMgdl(sPrefs)) {
            return value * Constants.MMOLL_TO_MGDL;
        } else {
            return value;
        }

    }

    public boolean doMgdl(SharedPreferences sPrefs) {
        String unit = sPrefs.getString("units", "mgdl");
        if (unit.compareTo("mgdl") == 0) {
            return true;
        } else {
            return false;
        }
    }

    */



    @Override
    public void onDestroy() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        if (mPrefs != null && mPreferencesListener != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(mPreferencesListener);
        }
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
