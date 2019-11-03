package info.nightscout.androidaps.plugins.general.wear.wearintegration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.ToastUtils;

public class WatchUpdaterService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String ACTION_RESEND = WatchUpdaterService.class.getName().concat(".Resend");
    public static final String ACTION_OPEN_SETTINGS = WatchUpdaterService.class.getName().concat(".OpenSettings");
    public static final String ACTION_SEND_STATUS = WatchUpdaterService.class.getName().concat(".SendStatus");
    public static final String ACTION_SEND_BASALS = WatchUpdaterService.class.getName().concat(".SendBasals");
    public static final String ACTION_SEND_BOLUSPROGRESS = WatchUpdaterService.class.getName().concat(".BolusProgress");
    public static final String ACTION_SEND_ACTIONCONFIRMATIONREQUEST = WatchUpdaterService.class.getName().concat(".ActionConfirmationRequest");
    public static final String ACTION_SEND_CHANGECONFIRMATIONREQUEST = WatchUpdaterService.class.getName().concat(".ChangeConfirmationRequest");
    public static final String ACTION_CANCEL_NOTIFICATION = WatchUpdaterService.class.getName().concat(".CancelNotification");

    private GoogleApiClient googleApiClient;
    public static final String WEARABLE_DATA_PATH = "/nightscout_watch_data";
    public static final String WEARABLE_RESEND_PATH = "/nightscout_watch_data_resend";
    private static final String WEARABLE_CANCELBOLUS_PATH = "/nightscout_watch_cancel_bolus";
    public static final String WEARABLE_CONFIRM_ACTIONSTRING_PATH = "/nightscout_watch_confirmactionstring";
    public static final String WEARABLE_INITIATE_ACTIONSTRING_PATH = "/nightscout_watch_initiateactionstring";

    private static final String OPEN_SETTINGS_PATH = "/openwearsettings";
    private static final String NEW_STATUS_PATH = "/sendstatustowear";
    private static final String NEW_PREFERENCES_PATH = "/sendpreferencestowear";
    public static final String BASAL_DATA_PATH = "/nightscout_watch_basal";
    public static final String BOLUS_PROGRESS_PATH = "/nightscout_watch_bolusprogress";
    public static final String ACTION_CONFIRMATION_REQUEST_PATH = "/nightscout_watch_actionconfirmationrequest";
    public static final String ACTION_CHANGECONFIRMATION_REQUEST_PATH = "/nightscout_watch_changeconfirmationrequest";
    public static final String ACTION_CANCELNOTIFICATION_REQUEST_PATH = "/nightscout_watch_cancelnotificationrequest";


    boolean wear_integration = false;
    SharedPreferences mPrefs;
    private static boolean lastLoopStatus;

    private static Logger log = LoggerFactory.getLogger(WatchUpdaterService.class);

    private Handler handler;

    // Phone
    private static final String CAPABILITY_PHONE_APP = "phone_app_sync_bgs";
    private static final String MESSAGE_PATH_PHONE = "/phone_message_path";
    // Wear
    private static final String CAPABILITY_WEAR_APP = "wear_app_sync_bgs";
    private static final String MESSAGE_PATH_WEAR = "/wear_message_path";
    private String mWearNodeId = null;
    private String localnode = null;
    private String logPrefix = ""; // "WR: "


    @Override
    public void onCreate() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        listenForChangeInSettings();
        setSettings();
        if (wear_integration) {
            googleApiConnect();
        }
        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(this.getClass().getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
    }

    public void listenForChangeInSettings() {
        WearPlugin.registerWatchUpdaterService(this);
    }

    public void setSettings() {
        wear_integration = WearPlugin.getPlugin().isEnabled(PluginType.GENERAL);
        // Log.d(TAG, "WR: wear_integration=" + wear_integration);
        if (wear_integration) {
            googleApiConnect();
        }
    }


    private void googleApiConnect() {
        if (googleApiClient != null && (googleApiClient.isConnected() || googleApiClient.isConnecting())) {
            googleApiClient.disconnect();
        }
        googleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
        Wearable.MessageApi.addListener(googleApiClient, this);
        if (googleApiClient.isConnected()) {
            log.debug(logPrefix + "API client is connected");
        } else {
            // Log.d("WatchUpdater", logPrefix + "API client is not connected and is trying to connect");
            googleApiClient.connect();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        // Log.d(TAG, logPrefix + "onStartCommand: " + action);

        if (wear_integration) {
            handler.post(() -> {
                if (googleApiClient.isConnected()) {
                    if (ACTION_RESEND.equals(action)) {
                        resendData();
                    } else if (ACTION_OPEN_SETTINGS.equals(action)) {
                        sendNotification();
                    } else if (ACTION_SEND_STATUS.equals(action)) {
                        sendStatus();
                    } else if (ACTION_SEND_BASALS.equals(action)) {
                        sendBasals();
                    } else if (ACTION_SEND_BOLUSPROGRESS.equals(action)) {
                        sendBolusProgress(intent.getIntExtra("progresspercent", 0), intent.hasExtra("progressstatus") ? intent.getStringExtra("progressstatus") : "");
                    } else if (ACTION_SEND_ACTIONCONFIRMATIONREQUEST.equals(action)) {
                        String title = intent.getStringExtra("title");
                        String message = intent.getStringExtra("message");
                        String actionstring = intent.getStringExtra("actionstring");
                        sendActionConfirmationRequest(title, message, actionstring);
                    } else if (ACTION_SEND_CHANGECONFIRMATIONREQUEST.equals(action)) {
                        String title = intent.getStringExtra("title");
                        String message = intent.getStringExtra("message");
                        String actionstring = intent.getStringExtra("actionstring");
                        sendChangeConfirmationRequest(title, message, actionstring);
                    } else if (ACTION_CANCEL_NOTIFICATION.equals(action)) {
                        String actionstring = intent.getStringExtra("actionstring");
                        sendCancelNotificationRequest(actionstring);
                    } else {
                        sendData();
                    }
                } else {
                    googleApiClient.connect();
                }
            });
        }

        return START_STICKY;
    }


    private void updateWearSyncBgsCapability(CapabilityInfo capabilityInfo) {
        Log.d("WatchUpdaterService", logPrefix + "CabilityInfo: " + capabilityInfo);
        Set<Node> connectedNodes = capabilityInfo.getNodes();
        mWearNodeId = pickBestNodeId(connectedNodes);
    }


    private String pickBestNodeId(Set<Node> nodes) {
        String bestNodeId = null;
        // Find a nearby node or pick one arbitrarily
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        return bestNodeId;
    }


    @Override
    public void onConnected(Bundle connectionHint) {
        CapabilityApi.CapabilityListener capabilityListener = capabilityInfo -> {
            updateWearSyncBgsCapability(capabilityInfo);
            // Log.d(TAG, logPrefix + "onConnected onCapabilityChanged mWearNodeID:" + mWearNodeId);
            // new CheckWearableConnected().execute();
        };

        Wearable.CapabilityApi.addCapabilityListener(googleApiClient, capabilityListener, CAPABILITY_WEAR_APP);
        sendData();
    }


    @Override
    public void onPeerConnected(com.google.android.gms.wearable.Node peer) {// KS
        super.onPeerConnected(peer);
        String id = peer.getId();
        String name = peer.getDisplayName();
        // Log.d(TAG, logPrefix + "onPeerConnected peer name & ID: " + name + "|" + id);
    }


    @Override
    public void onPeerDisconnected(com.google.android.gms.wearable.Node peer) {// KS
        super.onPeerDisconnected(peer);
        String id = peer.getId();
        String name = peer.getDisplayName();
        // Log.d(TAG, logPrefix + "onPeerDisconnected peer name & ID: " + name + "|" + id);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }


    @Override
    public void onMessageReceived(MessageEvent event) {

        // Log.d(TAG, logPrefix + "onMessageRecieved: " + event);

        if (wear_integration) {
            if (event != null && event.getPath().equals(WEARABLE_RESEND_PATH)) {
                resendData();
            }

            if (event != null && event.getPath().equals(WEARABLE_CANCELBOLUS_PATH)) {
                cancelBolus();
            }

            if (event != null && event.getPath().equals(WEARABLE_INITIATE_ACTIONSTRING_PATH)) {
                String actionstring = new String(event.getData());
                log.debug("Wear: " + actionstring);
                ActionStringHandler.handleInitiate(actionstring);
            }

            if (event != null && event.getPath().equals(WEARABLE_CONFIRM_ACTIONSTRING_PATH)) {
                String actionstring = new String(event.getData());
                log.debug("Wear Confirm: " + actionstring);
                ActionStringHandler.handleConfirmation(actionstring);
            }
        }
    }

    private void cancelBolus() {
        ConfigBuilderPlugin.getPlugin().getActivePump().stopBolusDelivering();
    }

    private void sendData() {

        BgReading lastBG = DatabaseHelper.lastBg();
        // Log.d(TAG, logPrefix + "LastBg=" + lastBG);
        if (lastBG != null) {
            GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

            if (googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
                googleApiConnect();
            }
            if (wear_integration) {

                final DataMap dataMap = dataMapSingleBG(lastBG, glucoseStatus);
                if (dataMap == null) {
                    ToastUtils.showToastInUiThread(this, MainApp.gs(R.string.noprofile));
                    return;
                }

                executeTask(new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient), dataMap);
            }
        }
    }


    private DataMap dataMapSingleBG(BgReading lastBG, GlucoseStatus glucoseStatus) {
        String units = ProfileFunctions.getInstance().getProfileUnits();

        Double lowLine = SafeParse.stringToDouble(mPrefs.getString("low_mark", "0"));
        Double highLine = SafeParse.stringToDouble(mPrefs.getString("high_mark", "0"));

        // convert to mg/dl
        if (!units.equals(Constants.MGDL)) {
            lowLine *= Constants.MMOLL_TO_MGDL;
            highLine *= Constants.MMOLL_TO_MGDL;

        }

        if (lowLine < 1) {
            lowLine = OverviewPlugin.INSTANCE.getBgTargetLow();
        }

        if (highLine < 1) {
            highLine = OverviewPlugin.INSTANCE.getBgTargetHigh();
        }

        long sgvLevel = 0l;
        if (lastBG.value > highLine) {
            sgvLevel = 1;
        } else if (lastBG.value < lowLine) {
            sgvLevel = -1;
        }

        DataMap dataMap = new DataMap();
        dataMap.putString("sgvString", lastBG.valueToUnitsToString(units));
        dataMap.putString("glucoseUnits", units);
        dataMap.putLong("timestamp", lastBG.date);
        if (glucoseStatus == null) {
            dataMap.putString("slopeArrow", "");
            dataMap.putString("delta", "--");
            dataMap.putString("avgDelta", "--");
        } else {
            dataMap.putString("slopeArrow", slopeArrow(glucoseStatus.delta));
            dataMap.putString("delta", deltastring(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units));
            dataMap.putString("avgDelta", deltastring(glucoseStatus.avgdelta, glucoseStatus.avgdelta * Constants.MGDL_TO_MMOLL, units));
        }
        dataMap.putLong("sgvLevel", sgvLevel);
        dataMap.putDouble("sgvDouble", lastBG.value);
        dataMap.putDouble("high", highLine);
        dataMap.putDouble("low", lowLine);
        return dataMap;
    }

    private String deltastring(double deltaMGDL, double deltaMMOL, String units) {
        String deltastring = "";
        if (deltaMGDL >= 0) {
            deltastring += "+";
        } else {
            deltastring += "-";
        }

        boolean detailed = SP.getBoolean("wear_detailed_delta", false);
        if (units.equals(Constants.MGDL)) {
            if (detailed) {
                deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMGDL));
            } else {
                deltastring += DecimalFormatter.to0Decimal(Math.abs(deltaMGDL));
            }
        } else {
            if (detailed) {
                deltastring += DecimalFormatter.to2Decimal(Math.abs(deltaMMOL));
            } else {
                deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMMOL));
            }
        }
        return deltastring;
    }

    private String slopeArrow(double delta) {
        if (delta <= (-3.5 * 5)) {
            return "\u21ca";
        } else if (delta <= (-2 * 5)) {
            return "\u2193";
        } else if (delta <= (-1 * 5)) {
            return "\u2198";
        } else if (delta <= (1 * 5)) {
            return "\u2192";
        } else if (delta <= (2 * 5)) {
            return "\u2197";
        } else if (delta <= (3.5 * 5)) {
            return "\u2191";
        } else {
            return "\u21c8";
        }
    }


    private void resendData() {
        if (googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
            googleApiConnect();
        }
        long startTime = System.currentTimeMillis() - (long) (60000 * 60 * 5.5);
        BgReading last_bg = DatabaseHelper.lastBg();

        if (last_bg == null) return;

        List<BgReading> graph_bgs = MainApp.getDbHelper().getBgreadingsDataFromTime(startTime, true);
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData(true);

        if (!graph_bgs.isEmpty()) {
            DataMap entries = dataMapSingleBG(last_bg, glucoseStatus);
            if (entries == null) {
                ToastUtils.showToastInUiThread(this, MainApp.gs(R.string.noprofile));
                return;
            }
            final ArrayList<DataMap> dataMaps = new ArrayList<>(graph_bgs.size());
            for (BgReading bg : graph_bgs) {
                DataMap dataMap = dataMapSingleBG(bg, glucoseStatus);
                if (dataMap != null) {
                    dataMaps.add(dataMap);
                }
            }
            entries.putDataMapArrayList("entries", dataMaps);
            executeTask(new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient), entries);
        }
        sendPreferences();
        sendBasals();
        sendStatus();
    }

    private void sendBasals() {
        if (googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
            googleApiConnect();
        }

        long now = System.currentTimeMillis();
        final long startTimeWindow = now - (long) (60000 * 60 * 5.5);


        ArrayList<DataMap> basals = new ArrayList<>();
        ArrayList<DataMap> temps = new ArrayList<>();
        ArrayList<DataMap> boluses = new ArrayList<>();
        ArrayList<DataMap> predictions = new ArrayList<>();


        Profile profile = ProfileFunctions.getInstance().getProfile();

        if (profile == null) {
            return;
        }

        long beginBasalSegmentTime = startTimeWindow;
        long runningTime = startTimeWindow;

        double beginBasalValue = profile.getBasal(beginBasalSegmentTime);
        double endBasalValue = beginBasalValue;

        TemporaryBasal tb1 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(runningTime);
        TemporaryBasal tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(runningTime);
        double tb_before = beginBasalValue;
        double tb_amount = beginBasalValue;
        long tb_start = runningTime;

        if (tb1 != null) {
            tb_before = beginBasalValue;
            Profile profileTB = ProfileFunctions.getInstance().getProfile(runningTime);
            if (profileTB != null) {
                tb_amount = tb1.tempBasalConvertedToAbsolute(runningTime, profileTB);
                tb_start = runningTime;
            }
        }


        for (; runningTime < now; runningTime += 5 * 60 * 1000) {
            Profile profileTB = ProfileFunctions.getInstance().getProfile(runningTime);
            if (profileTB == null)
                return;
            //basal rate
            endBasalValue = profile.getBasal(runningTime);
            if (endBasalValue != beginBasalValue) {
                //push the segment we recently left
                basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));

                //begin new Basal segment
                beginBasalSegmentTime = runningTime;
                beginBasalValue = endBasalValue;
            }

            //temps
            tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(runningTime);

            if (tb1 == null && tb2 == null) {
                //no temp stays no temp

            } else if (tb1 != null && tb2 == null) {
                //temp is over -> push it
                temps.add(tempDatamap(tb_start, tb_before, runningTime, endBasalValue, tb_amount));
                tb1 = null;

            } else if (tb1 == null && tb2 != null) {
                //temp begins
                tb1 = tb2;
                tb_start = runningTime;
                tb_before = endBasalValue;
                tb_amount = tb1.tempBasalConvertedToAbsolute(runningTime, profileTB);

            } else if (tb1 != null && tb2 != null) {
                double currentAmount = tb2.tempBasalConvertedToAbsolute(runningTime, profileTB);
                if (currentAmount != tb_amount) {
                    temps.add(tempDatamap(tb_start, tb_before, runningTime, currentAmount, tb_amount));
                    tb_start = runningTime;
                    tb_before = tb_amount;
                    tb_amount = currentAmount;
                    tb1 = tb2;
                }
            }
        }
        if (beginBasalSegmentTime != runningTime) {
            //push the remaining segment
            basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));
        }
        if (tb1 != null) {
            tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now); //use "now" to express current situation
            if (tb2 == null) {
                //express the cancelled temp by painting it down one minute early
                temps.add(tempDatamap(tb_start, tb_before, now - 1 * 60 * 1000, endBasalValue, tb_amount));
            } else {
                //express currently running temp by painting it a bit into the future
                Profile profileNow = ProfileFunctions.getInstance().getProfile(now);
                double currentAmount = tb2.tempBasalConvertedToAbsolute(now, profileNow);
                if (currentAmount != tb_amount) {
                    temps.add(tempDatamap(tb_start, tb_before, now, tb_amount, tb_amount));
                    temps.add(tempDatamap(now, tb_amount, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
                } else {
                    temps.add(tempDatamap(tb_start, tb_before, runningTime + 5 * 60 * 1000, tb_amount, tb_amount));
                }
            }
        } else {
            tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now); //use "now" to express current situation
            if (tb2 != null) {
                //onset at the end
                Profile profileTB = ProfileFunctions.getInstance().getProfile(runningTime);
                double currentAmount = tb2.tempBasalConvertedToAbsolute(runningTime, profileTB);
                temps.add(tempDatamap(now - 1 * 60 * 1000, endBasalValue, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
            }
        }

        List<Treatment> treatments = TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();
        for (Treatment treatment : treatments) {
            if (treatment.date > startTimeWindow) {
                boluses.add(treatmentMap(treatment.date, treatment.insulin, treatment.carbs, treatment.isSMB, treatment.isValid));
            }

        }

        final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
        if (SP.getBoolean("wear_predictions", true) && finalLastRun != null && finalLastRun.request.hasPredictions && finalLastRun.constraintsProcessed != null) {
            List<BgReading> predArray = finalLastRun.constraintsProcessed.getPredictions();

            if (!predArray.isEmpty()) {
                for (BgReading bg : predArray) {
                    if (bg.value < 40) continue;
                    predictions.add(predictionMap(bg.date, bg.value, bg.getPredectionColor()));
                }
            }
        }


        DataMap dm = new DataMap();
        dm.putDataMapArrayList("basals", basals);
        dm.putDataMapArrayList("temps", temps);
        dm.putDataMapArrayList("boluses", boluses);
        dm.putDataMapArrayList("predictions", predictions);

        executeTask(new SendToDataLayerThread(BASAL_DATA_PATH, googleApiClient), dm);
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

    private DataMap treatmentMap(long date, double bolus, double carbs, boolean isSMB, boolean isValid) {
        DataMap dm = new DataMap();
        dm.putLong("date", date);
        dm.putDouble("bolus", bolus);
        dm.putDouble("carbs", carbs);
        dm.putBoolean("isSMB", isSMB);
        dm.putBoolean("isValid", isValid);
        return dm;
    }

    private DataMap predictionMap(long timestamp, double sgv, int color) {
        DataMap dm = new DataMap();
        dm.putLong("timestamp", timestamp);
        dm.putDouble("sgv", sgv);
        dm.putInt("color", color);
        return dm;
    }


    private void sendNotification() {
        if (googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(OPEN_SETTINGS_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("openSettings", "openSettings");
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendNotification", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("OpenSettings", "No connection to wearable available!");
        }
    }

    private void sendBolusProgress(int progresspercent, String status) {
        if (googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(BOLUS_PROGRESS_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("bolusProgress", "bolusProgress");
            dataMapRequest.getDataMap().putString("progressstatus", status);
            dataMapRequest.getDataMap().putInt("progresspercent", progresspercent);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendBolusProgress", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("BolusProgress", "No connection to wearable available!");
        }
    }

    private void sendActionConfirmationRequest(String title, String message, String actionstring) {
        if (googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(ACTION_CONFIRMATION_REQUEST_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("actionConfirmationRequest", "actionConfirmationRequest");
            dataMapRequest.getDataMap().putString("title", title);
            dataMapRequest.getDataMap().putString("message", message);
            dataMapRequest.getDataMap().putString("actionstring", actionstring);

            log.debug("Requesting confirmation from wear: " + actionstring);

            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendActionConfirmationRequest", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("confirmationRequest", "No connection to wearable available!");
        }
    }

    private void sendChangeConfirmationRequest(String title, String message, String actionstring) {
        if (googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(ACTION_CHANGECONFIRMATION_REQUEST_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("changeConfirmationRequest", "changeConfirmationRequest");
            dataMapRequest.getDataMap().putString("title", title);
            dataMapRequest.getDataMap().putString("message", message);
            dataMapRequest.getDataMap().putString("actionstring", actionstring);

            log.debug("Requesting confirmation from wear: " + actionstring);

            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendChangeConfirmationRequest", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("changeConfirmRequest", "No connection to wearable available!");
        }
    }

    private void sendCancelNotificationRequest(String actionstring) {
        if (googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(ACTION_CANCELNOTIFICATION_REQUEST_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("cancelNotificationRequest", "cancelNotificationRequest");
            dataMapRequest.getDataMap().putString("actionstring", actionstring);

            log.debug("Canceling notification on wear: " + actionstring);

            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendCancelNotificationRequest", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("cancelNotificationReq", "No connection to wearable available!");
        }
    }

    private void sendStatus() {

        if (googleApiClient.isConnected()) {
            Profile profile = ProfileFunctions.getInstance().getProfile();
            String status = MainApp.gs(R.string.noprofile);
            String iobSum, iobDetail, cobString, currentBasal, bgiString;
            iobSum = iobDetail = cobString = currentBasal = bgiString = "";
            if (profile != null) {
                TreatmentsInterface treatmentsInterface = TreatmentsPlugin.getPlugin();
                treatmentsInterface.updateTotalIOBTreatments();
                IobTotal bolusIob = treatmentsInterface.getLastCalculationTreatments().round();
                treatmentsInterface.updateTotalIOBTempBasals();
                IobTotal basalIob = treatmentsInterface.getLastCalculationTempBasals().round();

                iobSum = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob);
                iobDetail = "(" + DecimalFormatter.to2Decimal(bolusIob.iob) + "|" + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
                cobString = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "WatcherUpdaterService").generateCOBString();
                currentBasal = generateBasalString(treatmentsInterface);

                //bgi


                double bgi = -(bolusIob.activity + basalIob.activity) * 5 * profile.getIsf();
                bgiString = "" + ((bgi >= 0) ? "+" : "") + DecimalFormatter.to1Decimal(bgi);

                status = generateStatusString(profile, currentBasal, iobSum, iobDetail, bgiString);
            }


            //batteries
            int phoneBattery = getBatteryLevel(getApplicationContext());
            String rigBattery = NSDeviceStatus.getInstance().getUploaderStatus().trim();


            long openApsStatus = -1;
            //OpenAPS status
            if (Config.APS) {
                //we are AndroidAPS
                openApsStatus = LoopPlugin.lastRun != null && LoopPlugin.lastRun.lastEnact != null && LoopPlugin.lastRun.lastEnact.getTime() != 0 ? LoopPlugin.lastRun.lastEnact.getTime() : -1;
            } else {
                //NSClient or remote
                openApsStatus = NSDeviceStatus.getOpenApsTimestamp();
            }

            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NEW_STATUS_PATH);
            //unique content
            dataMapRequest.getDataMap().putString("externalStatusString", status);
            dataMapRequest.getDataMap().putString("iobSum", iobSum);
            dataMapRequest.getDataMap().putString("iobDetail", iobDetail);
            dataMapRequest.getDataMap().putBoolean("detailedIob", mPrefs.getBoolean("wear_detailediob", false));
            dataMapRequest.getDataMap().putString("cob", cobString);
            dataMapRequest.getDataMap().putString("currentBasal", currentBasal);
            dataMapRequest.getDataMap().putString("battery", "" + phoneBattery);
            dataMapRequest.getDataMap().putString("rigBattery", rigBattery);
            dataMapRequest.getDataMap().putLong("openApsStatus", openApsStatus);
            dataMapRequest.getDataMap().putString("bgi", bgiString);
            dataMapRequest.getDataMap().putBoolean("showBgi", mPrefs.getBoolean("wear_showbgi", false));
            dataMapRequest.getDataMap().putInt("batteryLevel", (phoneBattery >= 30) ? 1 : 0);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendStatus", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("SendStatus", "No connection to wearable available!");
        }
    }

    private void sendPreferences() {
        if (googleApiClient.isConnected()) {

            boolean wearcontrol = SP.getBoolean("wearcontrol", false);

            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NEW_PREFERENCES_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putBoolean("wearcontrol", wearcontrol);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendPreferences", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("SendStatus", "No connection to wearable available!");
        }
    }


    private void debugData(String source, Object data) {
        // Log.d(TAG, "WR: " + source + " " + data);
    }


    private void executeTask(AsyncTask task, DataMap... parameters) {
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Object[])parameters);
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        // task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        // } else {
        // task.execute();
        // }
    }


    @NonNull
    private String generateStatusString(Profile profile, String currentBasal, String iobSum, String iobDetail, String bgiString) {

        String status = "";

        if (profile == null) {
            status = MainApp.gs(R.string.noprofile);
            return status;
        }

        LoopPlugin activeloop = LoopPlugin.getPlugin();

        if (!activeloop.isEnabled(PluginType.LOOP)) {
            status += MainApp.gs(R.string.disabledloop) + "\n";
            lastLoopStatus = false;
        } else {
            lastLoopStatus = true;
        }

        String iobString = "";
        if (mPrefs.getBoolean("wear_detailediob", false)) {
            iobString = iobSum + " " + iobDetail;
        } else {
            iobString = iobSum + "U";
        }

        status += currentBasal + " " + iobString;

        //add BGI if shown, otherwise return
        if (mPrefs.getBoolean("wear_showbgi", false)) {
            status += " " + bgiString;
        }

        return status;
    }

    @NonNull
    private String generateBasalString(TreatmentsInterface treatmentsInterface) {

        String basalStringResult;

        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null)
            return "";

        TemporaryBasal activeTemp = treatmentsInterface.getTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            basalStringResult = activeTemp.toStringShort();
        } else {
            if (SP.getBoolean(R.string.key_danar_visualizeextendedaspercentage, false)) {
                basalStringResult = "100%";
            } else {
                basalStringResult = DecimalFormatter.to2Decimal(profile.getBasal()) + "U/h";
            }
        }
        return basalStringResult;
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

    public static boolean shouldReportLoopStatus(boolean enabled) {
        return (lastLoopStatus != enabled);
    }

    public static int getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level == -1 || scale == -1) {
                return 50;
            }
            return (int) (((float) level / (float) scale) * 100.0f);
        }
        return 50;
    }
}
