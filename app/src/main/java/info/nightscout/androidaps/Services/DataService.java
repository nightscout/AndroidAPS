package info.nightscout.androidaps.Services;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;

import com.j256.ormlite.dao.Dao;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.History.DanaRNSHistorySync;
import info.nightscout.androidaps.plugins.InsulinFastacting.InsulinFastactingFragment;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventNewSMS;
import info.nightscout.androidaps.plugins.SourceGlimp.SourceGlimpPlugin;
import info.nightscout.androidaps.plugins.SourceMM640g.SourceMM640gPlugin;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientPlugin;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripPlugin;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.utils.SP;


public class DataService extends IntentService {
    private static Logger log = LoggerFactory.getLogger(DataService.class);

    boolean xDripEnabled = false;
    boolean nsClientEnabled = true;
    boolean mm640gEnabled = false;
    boolean glimpEnabled = false;

    public DataService() {
        super("DataService");
        registerBus();
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (Config.logFunctionCalls)
            log.debug("onHandleIntent " + intent);

        if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceXdripPlugin.class)) {
            xDripEnabled = true;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceNSClientPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = true;
            mm640gEnabled = false;
            glimpEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceMM640gPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = true;
            glimpEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceGlimpPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = true;
        }

        boolean isNSProfile = ConfigBuilderPlugin.getActiveProfile().getClass().equals(NSProfilePlugin.class);

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);

        if (intent != null) {
            final String action = intent.getAction();
            if (Intents.ACTION_NEW_BG_ESTIMATE.equals(action)) {
                if (xDripEnabled) {
                    handleNewDataFromXDrip(intent);
                }
            } else if (Intents.NS_EMULATOR.equals(action)) {
                if (mm640gEnabled) {
                    handleNewDataFromMM640g(intent);
                }
            } else if (Intents.GLIMP_BG.equals(action)) {
                if (glimpEnabled) {
                    handleNewDataFromGlimp(intent);
                }
            } else if (Intents.ACTION_NEW_SGV.equals(action)) {
                // always handle SGV if NS-Client is the source
                if (nsClientEnabled) {
                    handleNewDataFromNSClient(intent);
                }
                // Objectives 0
                ObjectivesPlugin.bgIsAvailableInNS = true;
                ObjectivesPlugin.saveProgress();
            } else if (isNSProfile && Intents.ACTION_NEW_PROFILE.equals(action) || Intents.ACTION_NEW_DEVICESTATUS.equals(action)) {
                // always handle Profile if NSProfile is enabled without looking at nsUploadOnly
                handleNewDataFromNSClient(intent);
            } else if (!nsUploadOnly &&
                    (Intents.ACTION_NEW_TREATMENT.equals(action) ||
                            Intents.ACTION_CHANGED_TREATMENT.equals(action) ||
                            Intents.ACTION_REMOVED_TREATMENT.equals(action) ||
                            Intents.ACTION_NEW_STATUS.equals(action) ||
                            Intents.ACTION_NEW_DEVICESTATUS.equals(action) ||
                            Intents.ACTION_NEW_CAL.equals(action) ||
                            Intents.ACTION_NEW_MBG.equals(action))
                    ) {
                handleNewDataFromNSClient(intent);
            } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
                handleNewSMS(intent);
            }
        }
        if (Config.logFunctionCalls)
            log.debug("onHandleIntent exit " + intent);
        DataReceiver.completeWakefulIntent(intent);
    }

/*
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (Config.logFunctionCalls)
            log.debug("onStartCommand");

        return START_STICKY;
    }
*/

    @Override
    public void onDestroy() {
        super.onDestroy();
        MainApp.bus().unregister(this);
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void handleNewDataFromXDrip(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        BgReading bgReading = new BgReading();

        bgReading.value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE);
        bgReading.direction = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME);
        bgReading.date = bundle.getLong(Intents.EXTRA_TIMESTAMP);
        bgReading.raw = bundle.getDouble(Intents.EXTRA_RAW);

        if (bgReading.date < new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) {
            if (Config.logIncommingBG)
                log.debug("Ignoring old XDRIPREC BG " + bgReading.toString());
            return;
        }

        if (Config.logIncommingBG)
            log.debug("XDRIPREC BG " + bgReading.toString());

        MainApp.getDbHelper().createIfNotExists(bgReading);
    }

    private void handleNewDataFromGlimp(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        BgReading bgReading = new BgReading();

        bgReading.value = bundle.getDouble("mySGV");
        bgReading.direction = bundle.getString("myTrend");
        bgReading.date = bundle.getLong("myTimestamp");
        bgReading.raw = 0;

        if (Config.logIncommingBG)
            log.debug(bundle.toString());
            log.debug("GLIMP BG " + bgReading.toString());

        MainApp.getDbHelper().createIfNotExists(bgReading);
    }

    private void handleNewDataFromMM640g(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        final String collection = bundle.getString("collection");
        if (collection == null) return;

        if (collection.equals("entries")) {
            final String data = bundle.getString("data");

            if ((data != null) && (data.length() > 0)) {
                try {
                    final JSONArray json_array = new JSONArray(data);
                    for (int i = 0; i < json_array.length(); i++) {
                        final JSONObject json_object = json_array.getJSONObject(i);
                        final String type = json_object.getString("type");
                        switch (type) {
                            case "sgv":
                                BgReading bgReading = new BgReading();

                                bgReading.value = json_object.getDouble("sgv");
                                bgReading.direction = json_object.getString("direction");
                                bgReading.date = json_object.getLong("date");
                                bgReading.raw = json_object.getDouble("sgv");

                                if (bgReading.date < new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) {
                                    if (Config.logIncommingBG)
                                        log.debug("Ignoring old MM640g BG " + bgReading.toString());
                                    return;
                                }

                                if (Config.logIncommingBG)
                                    log.debug("MM640g BG " + bgReading.toString());

                                MainApp.getDbHelper().createIfNotExists(bgReading);
                                break;
                            default:
                                log.debug("Unknown entries type: " + type);
                        }
                    }
                } catch (JSONException e) {
                    log.error("Got JSON exception: " + e);
                }
            }
        }
    }

    private void handleNewDataFromNSClient(Intent intent) {
        Bundle bundles = intent.getExtras();
        if (bundles == null) return;
        if (Config.logIncommingData)
            log.debug("Got intent: " + intent.getAction());


        if (intent.getAction().equals(Intents.ACTION_NEW_STATUS)) {
            if (Config.logIncommingData)
                log.debug("Received status: " + bundles);
            if (bundles.containsKey("nsclientversioncode")) {
                ConfigBuilderPlugin.nightscoutVersionCode = bundles.getInt("nightscoutversioncode"); // for ver 1.2.3 contains 10203
                ConfigBuilderPlugin.nightscoutVersionName = bundles.getString("nightscoutversionname");
                ConfigBuilderPlugin.nsClientVersionCode = bundles.getInt("nsclientversioncode"); // for ver 1.17 contains 117
                ConfigBuilderPlugin.nsClientVersionName = bundles.getString("nsclientversionname");
                log.debug("Got versions: NSClient: " + ConfigBuilderPlugin.nsClientVersionName + " Nightscout: " + ConfigBuilderPlugin.nightscoutVersionName);
                if (ConfigBuilderPlugin.nsClientVersionCode < 121) {
                    Notification notification = new Notification(Notification.OLD_NSCLIENT, MainApp.sResources.getString(R.string.unsupportedclientver), Notification.URGENT);
                    MainApp.bus().post(new EventNewNotification(notification));
                } else {
                    MainApp.bus().post(new EventDismissNotification(Notification.OLD_NSCLIENT));
                }
            } else {
                Notification notification = new Notification(Notification.OLD_NSCLIENT, MainApp.sResources.getString(R.string.unsupportedclientver), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }
            if (bundles.containsKey("status")) {
                try {
                    JSONObject statusJson = new JSONObject(bundles.getString("status"));
                    if (statusJson.has("settings")) {
                        JSONObject settings = statusJson.getJSONObject("settings");
                        if (settings.has("thresholds")) {
                            JSONObject thresholds = settings.getJSONObject("thresholds");
                            if (thresholds.has("bgTargetTop")) {
                                OverviewPlugin.bgTargetHigh = thresholds.getDouble("bgTargetTop");
                            }
                            if (thresholds.has("bgTargetBottom")) {
                                OverviewPlugin.bgTargetLow = thresholds.getDouble("bgTargetBottom");
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        if (intent.getAction().equals(Intents.ACTION_NEW_DEVICESTATUS)) {
            try {
                if (bundles.containsKey("devicestatus")) {
                    String devicestatusesstring = bundles.getString("devicestatus");
                    JSONObject devicestatusJson = new JSONObject(bundles.getString("devicestatus"));
                    if (devicestatusJson.has("pump")) {
                        // Objectives 0
                        ObjectivesPlugin.pumpStatusIsAvailableInNS = true;
                        ObjectivesPlugin.saveProgress();
                    }
                }
                if (bundles.containsKey("devicestatuses")) {
                    String devicestatusesstring = bundles.getString("devicestatuses");
                    JSONArray jsonArray = new JSONArray(devicestatusesstring);
                    if (jsonArray.length() > 0) {
                        JSONObject devicestatusJson = jsonArray.getJSONObject(0);
                        if (devicestatusJson.has("pump")) {
                            // Objectives 0
                            ObjectivesPlugin.pumpStatusIsAvailableInNS = true;
                            ObjectivesPlugin.saveProgress();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Handle profile
        if (intent.getAction().equals(Intents.ACTION_NEW_PROFILE)) {
            try {
                String activeProfile = bundles.getString("activeprofile");
                String profile = bundles.getString("profile");
                NSProfile nsProfile = new NSProfile(new JSONObject(profile), activeProfile);
                MainApp.bus().post(new EventNewBasalProfile(nsProfile, "NSClient"));

                PumpInterface pump = MainApp.getConfigBuilder();
                if (pump != null) {
                    SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    if (SP.getBoolean("syncprofiletopump", false)) {
                        if (pump.setNewBasalProfile(nsProfile) == PumpInterface.SUCCESS) {
                            SmsCommunicatorPlugin smsCommunicatorPlugin = (SmsCommunicatorPlugin) MainApp.getSpecificPlugin(SmsCommunicatorPlugin.class);
                            if (smsCommunicatorPlugin != null && smsCommunicatorPlugin.isEnabled(PluginBase.GENERAL)) {
                                smsCommunicatorPlugin.sendNotificationToAllNumbers(MainApp.sResources.getString(R.string.profile_set_ok));
                            }
                        }
                    }
                } else {
                    log.error("No active pump selected");
                }
                if (Config.logIncommingData)
                    log.debug("Received profile: " + activeProfile + " " + profile);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (intent.getAction().equals(Intents.ACTION_NEW_TREATMENT)) {
            try {
                if (bundles.containsKey("treatment")) {
                    String trstring = bundles.getString("treatment");
                    handleAddedTreatment(trstring);
                }
                if (bundles.containsKey("treatments")) {
                    String trstring = bundles.getString("treatments");
                    JSONArray jsonArray = new JSONArray(trstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject trJson = jsonArray.getJSONObject(i);
                        String trstr = trJson.toString();
                        handleAddedTreatment(trstr);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        if (intent.getAction().equals(Intents.ACTION_CHANGED_TREATMENT)) {
            try {
                if (bundles.containsKey("treatment")) {
                    String trstring = bundles.getString("treatment");
                    handleChangedTreatment(trstring);
                }
                if (bundles.containsKey("treatments")) {
                    String trstring = bundles.getString("treatments");
                    JSONArray jsonArray = new JSONArray(trstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject trJson = jsonArray.getJSONObject(i);
                        String trstr = trJson.toString();
                        handleChangedTreatment(trstr);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (intent.getAction().equals(Intents.ACTION_REMOVED_TREATMENT)) {
            try {
                if (bundles.containsKey("treatment")) {
                    String trstring = bundles.getString("treatment");
                    JSONObject trJson = new JSONObject(trstring);
                    String _id = trJson.getString("_id");
                    MainApp.getDbHelper().deleteTreatmentById(_id);
                    MainApp.getDbHelper().deleteTempTargetById(trJson.getString("_id"));
                    MainApp.getDbHelper().deleteTempBasalById(trJson.getString("_id"));
                    MainApp.getDbHelper().deleteExtendedBolusById(trJson.getString("_id"));
                    MainApp.getDbHelper().deleteCareportalEventById(trJson.getString("_id"));
                }

                if (bundles.containsKey("treatments")) {
                    String trstring = bundles.getString("treatments");
                    JSONArray jsonArray = new JSONArray(trstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject trJson = jsonArray.getJSONObject(i);
                        String _id = trJson.getString("_id");
                        MainApp.getDbHelper().deleteTreatmentById(_id);
                        MainApp.getDbHelper().deleteTempTargetById(trJson.getString("_id"));
                        MainApp.getDbHelper().deleteTempBasalById(trJson.getString("_id"));
                        MainApp.getDbHelper().deleteExtendedBolusById(trJson.getString("_id"));
                        MainApp.getDbHelper().deleteCareportalEventById(trJson.getString("_id"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_SGV)) {
            try {
                if (bundles.containsKey("sgv")) {
                    String sgvstring = bundles.getString("sgv");
                    JSONObject sgvJson = new JSONObject(sgvstring);
                    NSSgv nsSgv = new NSSgv(sgvJson);
                    BgReading bgReading = new BgReading(nsSgv);
                    if (bgReading.date < new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000l) {
                        if (Config.logIncommingData)
                            log.debug("Ignoring old BG: " + bgReading.toString());
                        return;
                    }
                    MainApp.getDbHelper().createIfNotExists(bgReading);
                    if (Config.logIncommingData)
                        log.debug("ADD: Stored new BG: " + bgReading.toString());
                }

                if (bundles.containsKey("sgvs")) {
                    String sgvstring = bundles.getString("sgvs");
                    JSONArray jsonArray = new JSONArray(sgvstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject sgvJson = jsonArray.getJSONObject(i);
                        NSSgv nsSgv = new NSSgv(sgvJson);
                        BgReading bgReading = new BgReading(nsSgv);
                        if (bgReading.date < new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000l) {
                            if (Config.logIncommingData)
                                log.debug("Ignoring old BG: " + bgReading.toString());
                        } else {
                            MainApp.getDbHelper().createIfNotExists(bgReading);
                            if (Config.logIncommingData)
                                log.debug("ADD: Stored new BG: " + bgReading.toString());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_MBG)) {
            log.error("Not implemented yet"); // TODO implemeng MBGS
        }
    }

    private void handleAddedTreatment(String trstring) throws JSONException, SQLException {
        JSONObject trJson = new JSONObject(trstring);
        handleDanaRHistoryRecords(trJson); // update record _id in history
        handleAddChangeTempTargetRecord(trJson);
        handleAddChangeTempBasalRecord(trJson);
        handleAddChangeExtendedBolusRecord(trJson);
        handleAddChangeCareportalEventRecord(trJson);
        if (!trJson.has("insulin") && !trJson.has("carbs")) {
            if (Config.logIncommingData)
                log.debug("Ignoring non insulin/carbs record: " + trstring);
            return;
        }

        Treatment stored = null;
        String _id = trJson.getString("_id");

        if (trJson.has("date")) {
            if (Config.logIncommingData)
                log.debug("ADD: date found: " + trstring);
            stored = MainApp.getDbHelper().findTreatmentByTimeIndex(trJson.getLong("date"));
        } else {
            stored = MainApp.getDbHelper().findTreatmentById(_id);
        }

        if (stored != null) {
            if (Config.logIncommingData)
                log.debug("ADD: Existing treatment: " + trstring);
            if (trJson.has("date")) {
                stored._id = _id;
                int updated = MainApp.getDbHelper().update(stored);
                if (Config.logIncommingData)
                    log.debug("Records updated: " + updated);
            }
        } else {
            if (Config.logIncommingData)
                log.debug("ADD: New treatment: " + trstring);
            InsulinInterface insulinInterface = MainApp.getConfigBuilder().getActiveInsulin();
            if (insulinInterface == null) insulinInterface = InsulinFastactingFragment.getPlugin();
            Treatment treatment = new Treatment(insulinInterface);
            treatment._id = _id;
            treatment.carbs = trJson.has("carbs") ? trJson.getDouble("carbs") : 0;
            treatment.insulin = trJson.has("insulin") ? trJson.getDouble("insulin") : 0d;
            treatment.date = trJson.getLong("mills");
            if (trJson.has("eventType")) {
                treatment.mealBolus = true;
                if (trJson.get("eventType").equals("Correction Bolus"))
                    treatment.mealBolus = false;
                double carbs = treatment.carbs;
                if (trJson.has("boluscalc")) {
                    JSONObject boluscalc = trJson.getJSONObject("boluscalc");
                    if (boluscalc.has("carbs")) {
                        carbs = Math.max(boluscalc.getDouble("carbs"), carbs);
                    }
                }
                if (carbs <= 0)
                    treatment.mealBolus = false;
            }
            MainApp.getDbHelper().createOrUpdate(treatment);
            if (Config.logIncommingData)
                log.debug("ADD: Stored treatment: " + treatment.log());
        }
    }

    private void handleChangedTreatment(String trstring) throws JSONException, SQLException {
        JSONObject trJson = new JSONObject(trstring);
        handleDanaRHistoryRecords(trJson); // update record _id in history
        handleAddChangeTempTargetRecord(trJson);
        handleAddChangeTempBasalRecord(trJson);
        handleAddChangeExtendedBolusRecord(trJson);
        handleAddChangeCareportalEventRecord(trJson);
        if (!trJson.has("insulin") && !trJson.has("carbs")) {
            if (Config.logIncommingData)
                log.debug("CHANGE: Uninterested treatment: " + trstring);
            return;
        }
        String _id = trJson.getString("_id");

        Treatment stored;

        if (trJson.has("date")) {
            if (Config.logIncommingData)
                log.debug("ADD: date found: " + trstring);
            stored = MainApp.getDbHelper().findTreatmentByTimeIndex(trJson.getLong("date"));
        } else {
            stored = MainApp.getDbHelper().findTreatmentById(_id);
        }

        if (stored != null) {
            if (Config.logIncommingData)
                log.debug("CHANGE: Removing old: " + trstring);
            MainApp.getDbHelper().deleteTreatmentById(_id);
        }

        if (Config.logIncommingData)
            log.debug("CHANGE: Adding new treatment: " + trstring);
        InsulinInterface insulinInterface = MainApp.getConfigBuilder().getActiveInsulin();
        if (insulinInterface == null) insulinInterface = InsulinFastactingFragment.getPlugin();
        Treatment treatment = new Treatment(insulinInterface);
        treatment._id = _id;
        treatment.carbs = trJson.has("carbs") ? trJson.getDouble("carbs") : 0;
        treatment.insulin = trJson.has("insulin") ? trJson.getDouble("insulin") : 0d;
        //treatment.created_at = DateUtil.fromISODateString(trJson.getString("created_at"));
        treatment.date = trJson.getLong("mills");
        if (trJson.has("eventType")) {
            treatment.mealBolus = true;
            if (trJson.get("eventType").equals("Correction Bolus"))
                treatment.mealBolus = false;
            double carbs = treatment.carbs;
            if (trJson.has("boluscalc")) {
                JSONObject boluscalc = trJson.getJSONObject("boluscalc");
                if (boluscalc.has("carbs")) {
                    carbs = Math.max(boluscalc.getDouble("carbs"), carbs);
                }
            }
            if (carbs <= 0)
                treatment.mealBolus = false;
        }
        Dao.CreateOrUpdateStatus status = MainApp.getDbHelper().createOrUpdate(treatment);
        if (Config.logIncommingData)
            log.debug("Records updated: " + status.getNumLinesChanged());
        if (Config.logIncommingData)
            log.debug("CHANGE: Stored treatment: " + treatment.log());
    }

    public void handleDanaRHistoryRecords(JSONObject trJson) {
        if (trJson.has(DanaRNSHistorySync.DANARSIGNATURE)) {
            MainApp.getDbHelper().updateDanaRHistoryRecordId(trJson);
        }
    }

    public void handleAddChangeTempTargetRecord(JSONObject trJson) throws JSONException, SQLException {
        if (trJson.has("eventType") && trJson.getString("eventType").equals(CareportalEvent.TEMPORARYTARGET)) {
            if (Config.logIncommingData)
                log.debug("Processing TempTarget record: " + trJson.toString());
            MainApp.getDbHelper().createTemptargetFromJsonIfNotExists(trJson);
        }
    }

    public void handleAddChangeTempBasalRecord(JSONObject trJson) throws JSONException, SQLException {
        if (trJson.has("eventType") && trJson.getString("eventType").equals(CareportalEvent.TEMPBASAL)) {
            if (Config.logIncommingData)
                log.debug("Processing TempBasal record: " + trJson.toString());
            MainApp.getDbHelper().createTempBasalFromJsonIfNotExists(trJson);
        }
    }

    public void handleAddChangeExtendedBolusRecord(JSONObject trJson) throws JSONException, SQLException {
        if (trJson.has("eventType") && trJson.getString("eventType").equals(CareportalEvent.COMBOBOLUS)) {
            if (Config.logIncommingData)
                log.debug("Processing Extended Bolus record: " + trJson.toString());
            MainApp.getDbHelper().createExtendedBolusFromJsonIfNotExists(trJson);
        }
    }

    public void handleAddChangeCareportalEventRecord(JSONObject trJson) throws JSONException, SQLException {
        if (trJson.has("eventType") && (
                trJson.getString("eventType").equals(CareportalEvent.SITECHANGE) ||
                trJson.getString("eventType").equals(CareportalEvent.INSULINCHANGE) ||
                trJson.getString("eventType").equals(CareportalEvent.SENSORCHANGE)
        )) {
            if (Config.logIncommingData)
                log.debug("Processing CareportalEvent record: " + trJson.toString());
            MainApp.getDbHelper().createCareportalEventFromJsonIfNotExists(trJson);
        }
    }

    private void handleNewSMS(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;
        MainApp.bus().post(new EventNewSMS(bundle));
    }

}
