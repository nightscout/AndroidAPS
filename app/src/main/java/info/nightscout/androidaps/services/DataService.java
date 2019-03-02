package info.nightscout.androidaps.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventNsFood;
import info.nightscout.androidaps.events.EventNsTreatment;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSMbg;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.pump.danaR.activities.DanaRNSHistorySync;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.source.SourceDexcomG5Plugin;
import info.nightscout.androidaps.plugins.source.SourceDexcomG6Plugin;
import info.nightscout.androidaps.plugins.source.SourceEversensePlugin;
import info.nightscout.androidaps.plugins.source.SourceGlimpPlugin;
import info.nightscout.androidaps.plugins.source.SourceMM640gPlugin;
import info.nightscout.androidaps.plugins.source.SourceNSClientPlugin;
import info.nightscout.androidaps.plugins.source.SourcePoctechPlugin;
import info.nightscout.androidaps.plugins.source.SourceTomatoPlugin;
import info.nightscout.androidaps.plugins.source.SourceXdripPlugin;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.logging.BundleLogger;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;


public class DataService extends IntentService {
    private Logger log = LoggerFactory.getLogger(L.DATASERVICE);

    public DataService() {
        super("DataService");
        registerBus();
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (L.isEnabled(L.DATASERVICE)) {
            log.debug("onHandleIntent " + intent);
            log.debug("onHandleIntent " + BundleLogger.log(intent.getExtras()));
        }

        boolean acceptNSData = !SP.getBoolean(R.string.key_ns_upload_only, false);
        Bundle bundles = intent.getExtras();
        if (bundles != null && bundles.containsKey("islocal")) {
            acceptNSData = acceptNSData || bundles.getBoolean("islocal");
        }


        final String action = intent.getAction();
        if (Intents.ACTION_NEW_BG_ESTIMATE.equals(action)) {
            SourceXdripPlugin.getPlugin().handleNewData(intent);
        } else if (Intents.NS_EMULATOR.equals(action)) {
            SourceMM640gPlugin.getPlugin().handleNewData(intent);
        } else if (Intents.GLIMP_BG.equals(action)) {
            SourceGlimpPlugin.getPlugin().handleNewData(intent);
        } else if (Intents.DEXCOMG5_BG.equals(action)) {
            SourceDexcomG5Plugin.getPlugin().handleNewData(intent);
        } else if (Intents.DEXCOMG5_BG_NEW.equals(action)) {
            SourceDexcomG5Plugin.getPlugin().handleNewData(intent);
        } else if (Intents.DEXCOMG6_BG.equals(action)) {
            SourceDexcomG6Plugin.getPlugin().handleNewData(intent);
        } else if (Intents.POCTECH_BG.equals(action)) {
            SourcePoctechPlugin.getPlugin().handleNewData(intent);
        } else if (Intents.TOMATO_BG.equals(action)) {
            SourceTomatoPlugin.getPlugin().handleNewData(intent);
        } else if (Intents.EVERSENSE_BG.equals(action)) {
            SourceEversensePlugin.getPlugin().handleNewData(intent);
        } else if (Intents.ACTION_NEW_SGV.equals(action)) {
            SourceNSClientPlugin.getPlugin().handleNewData(intent);
        } else if (Intents.ACTION_NEW_PROFILE.equals(action)) {
            // always handle Profile if NSProfile is enabled without looking at nsUploadOnly
            NSProfilePlugin.getPlugin().handleNewData(intent);
        } else if (Intents.ACTION_NEW_DEVICESTATUS.equals(action)) {
            NSDeviceStatus.getInstance().handleNewData(intent);
        } else if (Intents.ACTION_NEW_STATUS.equals(action)) {
            NSSettingsStatus.getInstance().handleNewData(intent);
        } else if (Intents.ACTION_NEW_FOOD.equals(action)) {
            EventNsFood evt = new EventNsFood(EventNsFood.ADD, bundles);
            MainApp.bus().post(evt);
        } else if (Intents.ACTION_CHANGED_FOOD.equals(action)) {
            EventNsFood evt = new EventNsFood(EventNsFood.UPDATE, bundles);
            MainApp.bus().post(evt);
        } else if (Intents.ACTION_REMOVED_FOOD.equals(action)) {
            EventNsFood evt = new EventNsFood(EventNsFood.REMOVE, bundles);
            MainApp.bus().post(evt);
        } else if (acceptNSData &&
                (Intents.ACTION_NEW_TREATMENT.equals(action) ||
                        Intents.ACTION_CHANGED_TREATMENT.equals(action) ||
                        Intents.ACTION_REMOVED_TREATMENT.equals(action) ||
                        Intents.ACTION_NEW_CAL.equals(action) ||
                        Intents.ACTION_NEW_MBG.equals(action))
                ) {
            handleNewDataFromNSClient(intent);
        } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            SmsCommunicatorPlugin.getPlugin().handleNewData(intent);
        }

        if (L.isEnabled(L.DATASERVICE))
            log.debug("onHandleIntent exit " + intent);
        DataReceiver.completeWakefulIntent(intent);
    }

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

    private void handleNewDataFromNSClient(Intent intent) {
        Bundle bundles = intent.getExtras();
        if (bundles == null) return;
        if (L.isEnabled(L.DATASERVICE))
            log.debug("Got intent: " + intent.getAction());


        if (intent.getAction().equals(Intents.ACTION_NEW_TREATMENT) || intent.getAction().equals(Intents.ACTION_CHANGED_TREATMENT)) {
            try {
                if (bundles.containsKey("treatment")) {
                    JSONObject json = new JSONObject(bundles.getString("treatment"));
                    handleTreatmentFromNS(json, intent);
                }
                if (bundles.containsKey("treatments")) {
                    String trstring = bundles.getString("treatments");
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
                if (bundles.containsKey("treatment")) {
                    String trstring = bundles.getString("treatment");
                    JSONObject json = new JSONObject(trstring);
                    handleRemovedTreatmentFromNS(json);
                }

                if (bundles.containsKey("treatments")) {
                    String trstring = bundles.getString("treatments");
                    JSONArray jsonArray = new JSONArray(trstring);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject json = jsonArray.getJSONObject(i);
                        handleRemovedTreatmentFromNS(json);
                    }
                }
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }

        if (intent.getAction().equals(Intents.ACTION_NEW_MBG)) {
            try {
                if (bundles.containsKey("mbg")) {
                    String mbgstring = bundles.getString("mbg");
                    JSONObject mbgJson = new JSONObject(mbgstring);
                    storeMbg(mbgJson);
                }

                if (bundles.containsKey("mbgs")) {
                    String sgvstring = bundles.getString("mbgs");
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
    }

    private void handleRemovedTreatmentFromNS(JSONObject json) {
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
        if (eventType == null) {
            log.debug("Wrong treatment. Ignoring : " + json.toString());
            return;
        }
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
            long date = JsonHelper.safeGetLong(json, "mills");
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
        if (L.isEnabled(L.DATASERVICE))
            log.debug("Adding/Updating new MBG: " + careportalEvent.toString());
    }

}
