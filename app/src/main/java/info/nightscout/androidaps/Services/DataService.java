package info.nightscout.androidaps.Services;

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Telephony;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventNsFood;
import info.nightscout.androidaps.events.EventNsTreatment;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSMbg;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.ProfileNS.events.EventNSProfileUpdateGUI;
import info.nightscout.androidaps.plugins.PumpDanaR.activities.DanaRNSHistorySync;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventNewSMS;
import info.nightscout.androidaps.plugins.Source.SourceDexcomG5Plugin;
import info.nightscout.androidaps.plugins.Source.SourceGlimpPlugin;
import info.nightscout.androidaps.plugins.Source.SourceMM640gPlugin;
import info.nightscout.androidaps.plugins.Source.SourceNSClientPlugin;
import info.nightscout.androidaps.plugins.Source.SourcePoctechPlugin;
import info.nightscout.androidaps.plugins.Source.SourceXdripPlugin;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.utils.BundleLogger;
import info.nightscout.utils.JsonHelper;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;


public class DataService extends IntentService {
    private static Logger log = LoggerFactory.getLogger(DataService.class);

    boolean xDripEnabled = false;
    boolean nsClientEnabled = true;
    boolean mm640gEnabled = false;
    boolean glimpEnabled = false;
    boolean dexcomG5Enabled = false;
    boolean poctechEnabled = false;

    public DataService() {
        super("DataService");
        MainApp.subscribe(this);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (intent == null)
            return;
        if (Config.logIncommingData)
            log.debug("Got intent: " + intent.getAction());
        if (Config.logFunctionCalls)
            log.debug("onHandleIntent " + BundleLogger.log(intent.getExtras()));
        if (ConfigBuilderPlugin.getActiveBgSource() == null) {
            xDripEnabled = true;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = false;
            dexcomG5Enabled = false;
            poctechEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceXdripPlugin.class)) {
            xDripEnabled = true;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = false;
            dexcomG5Enabled = false;
            poctechEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceNSClientPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = true;
            mm640gEnabled = false;
            glimpEnabled = false;
            dexcomG5Enabled = false;
            poctechEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceMM640gPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = true;
            glimpEnabled = false;
            dexcomG5Enabled = false;
            poctechEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceGlimpPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = true;
            dexcomG5Enabled = false;
            poctechEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceDexcomG5Plugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = false;
            dexcomG5Enabled = true;
            poctechEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourcePoctechPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = false;
            dexcomG5Enabled = false;
            poctechEnabled = true;
        }

        boolean isNSProfile = MainApp.getConfigBuilder().getActiveProfileInterface() != null && MainApp.getConfigBuilder().getActiveProfileInterface().getClass().equals(NSProfilePlugin.class);

        boolean acceptNSData = !SP.getBoolean(R.string.key_ns_upload_only, false);
        Bundle bundles = intent.getExtras();
        if (bundles != null && bundles.containsKey("islocal")) {
            acceptNSData = acceptNSData || bundles.getBoolean("islocal");
        }

        final String action = intent.getAction();
        if (Intents.ACTION_NEW_BG_ESTIMATE.equals(action)) {
            if (xDripEnabled) {
                processNewBgIntent(SourceXdripPlugin.getPlugin(), intent);
            }
        } else if (Intents.NS_EMULATOR.equals(action)) {
            if (mm640gEnabled) {
                processNewBgIntent(SourceMM640gPlugin.getPlugin(), intent);
            }
        } else if (Intents.GLIMP_BG.equals(action)) {
            if (glimpEnabled) {
                processNewBgIntent(SourceGlimpPlugin.getPlugin(), intent);
            }
        } else if (Intents.DEXCOMG5_BG.equals(action)) {
            if (dexcomG5Enabled) {
                processNewBgIntent(SourceDexcomG5Plugin.getPlugin(), intent);
            }
        } else if (Intents.POCTECH_BG.equals(action)) {
            if (poctechEnabled) {
                processNewBgIntent(SourcePoctechPlugin.getPlugin(), intent);
            }
        } else if (Intents.ACTION_NEW_SGV.equals(action)) {
            if (nsClientEnabled || SP.getBoolean(R.string.key_ns_autobackfill, true))
                handleNewDataFromNSClient(intent);
            // Objectives 0
            ObjectivesPlugin.bgIsAvailableInNS = true;
            ObjectivesPlugin.saveProgress();
        } else if (isNSProfile && Intents.ACTION_NEW_PROFILE.equals(action) || Intents.ACTION_NEW_DEVICESTATUS.equals(action)) {
            // always handle Profile if NSProfile is enabled without looking at nsUploadOnly
            handleNewDataFromNSClient(intent);
        } else if (acceptNSData &&
                (Intents.ACTION_NEW_TREATMENT.equals(action) ||
                        Intents.ACTION_CHANGED_TREATMENT.equals(action) ||
                        Intents.ACTION_REMOVED_TREATMENT.equals(action) ||
                        Intents.ACTION_NEW_STATUS.equals(action) ||
                        Intents.ACTION_NEW_DEVICESTATUS.equals(action) ||
                        Intents.ACTION_NEW_FOOD.equals(action) ||
                        Intents.ACTION_CHANGED_FOOD.equals(action) ||
                        Intents.ACTION_REMOVED_FOOD.equals(action) ||
                        Intents.ACTION_NEW_CAL.equals(action) ||
                        Intents.ACTION_NEW_MBG.equals(action))
                ) {
            handleNewDataFromNSClient(intent);
        } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            handleNewSMS(intent);
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
        MainApp.unsubscribe(this);
    }

    private void processNewBgIntent(BgSourceInterface bgSource, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;
        bgSource.processNewData(bundle);
    }

    private void handleNewDataFromNSClient(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        if (intent.getAction().equals(Intents.ACTION_NEW_STATUS)) {
            if (bundle.containsKey("nsclientversioncode")) {
                ConfigBuilderPlugin.nightscoutVersionCode = bundle.getInt("nightscoutversioncode"); // for ver 1.2.3 contains 10203
                ConfigBuilderPlugin.nightscoutVersionName = bundle.getString("nightscoutversionname");
                ConfigBuilderPlugin.nsClientVersionCode = bundle.getInt("nsclientversioncode"); // for ver 1.17 contains 117
                ConfigBuilderPlugin.nsClientVersionName = bundle.getString("nsclientversionname");
                log.debug("Got versions: NSClient: " + ConfigBuilderPlugin.nsClientVersionName + " Nightscout: " + ConfigBuilderPlugin.nightscoutVersionName);
                try {
                    if (ConfigBuilderPlugin.nsClientVersionCode < MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionCode) {
                        Notification notification = new Notification(Notification.OLD_NSCLIENT, MainApp.gs(R.string.unsupportedclientver), Notification.URGENT);
                        MainApp.bus().post(new EventNewNotification(notification));
                    } else {
                        MainApp.bus().post(new EventDismissNotification(Notification.OLD_NSCLIENT));
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    log.error("Unhandled exception", e);
                }
                if (ConfigBuilderPlugin.nightscoutVersionCode < Config.SUPPORTEDNSVERSION) {
                    Notification notification = new Notification(Notification.OLD_NS, MainApp.gs(R.string.unsupportednsversion), Notification.NORMAL);
                    MainApp.bus().post(new EventNewNotification(notification));
                } else {
                    MainApp.bus().post(new EventDismissNotification(Notification.OLD_NS));
                }
            } else {
                Notification notification = new Notification(Notification.OLD_NSCLIENT, MainApp.gs(R.string.unsupportedclientver), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }
            if (bundle.containsKey("status")) {
                try {
                    JSONObject statusJson = new JSONObject(bundle.getString("status"));
                    NSSettingsStatus.getInstance().setData(statusJson);
                    if (Config.logIncommingData)
                        log.debug("Received status: " + statusJson.toString());
                    Double targetHigh = NSSettingsStatus.getInstance().getThreshold("bgTargetTop");
                    Double targetlow = NSSettingsStatus.getInstance().getThreshold("bgTargetBottom");
                    if (targetHigh != null)
                        OverviewPlugin.bgTargetHigh = targetHigh;
                    if (targetlow != null)
                        OverviewPlugin.bgTargetLow = targetlow;
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            }
        }
        if (intent.getAction().equals(Intents.ACTION_NEW_DEVICESTATUS)) {
            try {
                if (bundle.containsKey("devicestatus")) {
                    JSONObject devicestatusJson = new JSONObject(bundle.getString("devicestatus"));
                    NSDeviceStatus.getInstance().setData(devicestatusJson);
                    if (devicestatusJson.has("pump")) {
                        // Objectives 0
                        ObjectivesPlugin.pumpStatusIsAvailableInNS = true;
                        ObjectivesPlugin.saveProgress();
                    }
                }
                if (bundle.containsKey("devicestatuses")) {
                    String devicestatusesstring = bundle.getString("devicestatuses");
                    JSONArray jsonArray = new JSONArray(devicestatusesstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject devicestatusJson = jsonArray.getJSONObject(i);
                        NSDeviceStatus.getInstance().setData(devicestatusJson);
                        if (devicestatusJson.has("pump")) {
                            // Objectives 0
                            ObjectivesPlugin.pumpStatusIsAvailableInNS = true;
                            ObjectivesPlugin.saveProgress();
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Unhandled exception", e);
            }
        }
        // Handle profile
        if (intent.getAction().equals(Intents.ACTION_NEW_PROFILE)) {
            try {
                String activeProfile = bundle.getString("activeprofile");
                String profile = bundle.getString("profile");
                ProfileStore profileStore = new ProfileStore(new JSONObject(profile));
                NSProfilePlugin.getPlugin().storeNewProfile(profileStore);
                MainApp.bus().post(new EventNSProfileUpdateGUI());
                if (Config.logIncommingData)
                    log.debug("Received profileStore: " + activeProfile + " " + profile);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_TREATMENT) || intent.getAction().equals(Intents.ACTION_CHANGED_TREATMENT)) {
            try {
                if (bundle.containsKey("treatment")) {
                    JSONObject json = new JSONObject(bundle.getString("treatment"));
                    handleTreatmentFromNS(json, intent);
                }
                if (bundle.containsKey("treatments")) {
                    String trstring = bundle.getString("treatments");
                    JSONArray jsonArray = new JSONArray(trstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject json = jsonArray.getJSONObject(i);
                        handleTreatmentFromNS(json, intent);
                    }
                }
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }

        if (intent.getAction().equals(Intents.ACTION_REMOVED_TREATMENT)) {
            try {
                if (bundle.containsKey("treatment")) {
                    String trstring = bundle.getString("treatment");
                    JSONObject json = new JSONObject(trstring);
                    handleTreatmentFromNS(json);
                }

                if (bundle.containsKey("treatments")) {
                    String trstring = bundle.getString("treatments");
                    JSONArray jsonArray = new JSONArray(trstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject json = jsonArray.getJSONObject(i);
                        handleTreatmentFromNS(json);
                    }
                }
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_SGV)) {
            SourceNSClientPlugin.getPlugin().processNewData(bundle);
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_MBG)) {
            try {
                if (bundle.containsKey("mbg")) {
                    String mbgstring = bundle.getString("mbg");
                    JSONObject mbgJson = new JSONObject(mbgstring);
                    storeMbg(mbgJson);
                }

                if (bundle.containsKey("mbgs")) {
                    String sgvstring = bundle.getString("mbgs");
                    JSONArray jsonArray = new JSONArray(sgvstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject mbgJson = jsonArray.getJSONObject(i);
                        storeMbg(mbgJson);
                    }
                }
            } catch (Exception e) {
                log.error("Unhandled exception", e);
            }
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_FOOD)
                || intent.getAction().equals(Intents.ACTION_CHANGED_FOOD)) {
            int mode = Intents.ACTION_NEW_FOOD.equals(intent.getAction()) ? EventNsFood.ADD : EventNsFood.UPDATE;
            EventNsFood evt = new EventNsFood(mode, bundle);
            MainApp.bus().post(evt);
        }

        if (intent.getAction().equals(Intents.ACTION_REMOVED_FOOD)) {
            EventNsFood evt = new EventNsFood(EventNsFood.REMOVE, bundle);
            MainApp.bus().post(evt);
        }
    }

    private void handleTreatmentFromNS(JSONObject json) {
        // new DB model
        EventNsTreatment evtTreatment = new EventNsTreatment(EventNsTreatment.REMOVE, json);
        MainApp.bus().post(evtTreatment);
        // old DB model
        String _id = JsonHelper.safeGetString(json, "_id");
        MainApp.getDbHelper().deleteTempTargetById(_id);
        MainApp.getDbHelper().deleteTempBasalById(_id);
        MainApp.getDbHelper().deleteExtendedBolusById(_id);
        MainApp.getDbHelper().deleteCareportalEventById(_id);
        MainApp.getDbHelper().deleteProfileSwitchById(_id);
    }

    private void handleTreatmentFromNS(JSONObject json, Intent intent) {
        // new DB model
        int mode = Intents.ACTION_NEW_TREATMENT.equals(intent.getAction()) ? EventNsTreatment.ADD : EventNsTreatment.UPDATE;
        double insulin = JsonHelper.safeGetDouble(json, "insulin");
        double carbs = JsonHelper.safeGetDouble(json, "carbs");
        String eventType = JsonHelper.safeGetString(json, "eventType");
        if (insulin > 0 || carbs > 0) {
            EventNsTreatment evtTreatment = new EventNsTreatment(mode, json);
            MainApp.bus().post(evtTreatment);
        } else if (json.has(DanaRNSHistorySync.DANARSIGNATURE)) {
            // old DB model
            MainApp.getDbHelper().updateDanaRHistoryRecordId(json);
        } else if (eventType.equals(CareportalEvent.TEMPORARYTARGET)) {
            MainApp.getDbHelper().createTemptargetFromJsonIfNotExists(json);
        } else if (eventType.equals(CareportalEvent.TEMPBASAL)) {
            MainApp.getDbHelper().createTempBasalFromJsonIfNotExists(json);
        } else if (eventType.equals(CareportalEvent.COMBOBOLUS)) {
            MainApp.getDbHelper().createExtendedBolusFromJsonIfNotExists(json);
        } else if (eventType.equals(CareportalEvent.PROFILESWITCH)) {
            MainApp.getDbHelper().createProfileSwitchFromJsonIfNotExists(json);
        } else if (eventType.equals(CareportalEvent.SITECHANGE) ||
                eventType.equals(CareportalEvent.INSULINCHANGE) ||
                eventType.equals(CareportalEvent.SENSORCHANGE) ||
                eventType.equals(CareportalEvent.BGCHECK) ||
                eventType.equals(CareportalEvent.NOTE) ||
                eventType.equals(CareportalEvent.NONE) ||
                eventType.equals(CareportalEvent.ANNOUNCEMENT) ||
                eventType.equals(CareportalEvent.QUESTION) ||
                eventType.equals(CareportalEvent.EXERCISE) ||
                eventType.equals(CareportalEvent.OPENAPSOFFLINE) ||
                eventType.equals(CareportalEvent.PUMPBATTERYCHANGE)) {
            MainApp.getDbHelper().createCareportalEventFromJsonIfNotExists(json);
        }

        if (eventType.equals(CareportalEvent.ANNOUNCEMENT)) {
            long date = JsonHelper.safeGetLong(json,"mills");
            long now = System.currentTimeMillis();
            String enteredBy = JsonHelper.safeGetString(json, "enteredBy", "");
            String notes = JsonHelper.safeGetString(json, "notes", "");
            if (date > now - 15 * 60 * 1000L && !notes.isEmpty()
                    && !enteredBy.equals(SP.getString("careportal_enteredby", "AndroidAPS"))) {
                Notification announcement = new Notification(Notification.NSANNOUNCEMENT, notes, Notification.ANNOUNCEMENT, 60);
                MainApp.bus().post(new EventNewNotification(announcement));
            }
        }
    }

    private void storeMbg(JSONObject mbgJson) {
        NSMbg nsMbg = new NSMbg(mbgJson);
        CareportalEvent careportalEvent = new CareportalEvent(nsMbg);
        MainApp.getDbHelper().createOrUpdate(careportalEvent);
        if (Config.logIncommingData)
            log.debug("Adding/Updating new MBG: " + careportalEvent.log());
    }

    private void handleNewSMS(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;
        MainApp.bus().post(new EventNewSMS(bundle));
    }

}
