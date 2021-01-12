package info.nightscout.androidaps.services;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import dagger.android.DaggerIntentService;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventNsTreatment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.BundleLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSMbg;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.source.DexcomPlugin;
import info.nightscout.androidaps.plugins.source.EversensePlugin;
import info.nightscout.androidaps.plugins.source.GlimpPlugin;
import info.nightscout.androidaps.plugins.source.MM640gPlugin;
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin;
import info.nightscout.androidaps.plugins.source.PoctechPlugin;
import info.nightscout.androidaps.plugins.source.TomatoPlugin;
import info.nightscout.androidaps.plugins.source.XdripPlugin;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.buildHelper.BuildHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


public class DataService extends DaggerIntentService {
    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;
    @Inject RxBusWrapper rxBus;
    @Inject NSUpload nsUpload;
    @Inject SmsCommunicatorPlugin smsCommunicatorPlugin;
    @Inject DexcomPlugin dexcomPlugin;
    @Inject EversensePlugin eversensePlugin;
    @Inject GlimpPlugin glimpPlugin;
    @Inject MM640gPlugin mm640GPlugin;
    @Inject NSClientSourcePlugin nsClientSourcePlugin;
    @Inject PoctechPlugin poctechPlugin;
    @Inject TomatoPlugin tomatoPlugin;
    @Inject XdripPlugin xdripPlugin;
    @Inject NSProfilePlugin nsProfilePlugin;
    @Inject ActivePluginProvider activePlugin;
    @Inject Config config;
    @Inject BuildHelper buildHelper;

    public DataService() {
        super("DataService");
    }


    @Override
    protected void onHandleIntent(final Intent intent) {
        aapsLogger.debug(LTag.DATASERVICE, "onHandleIntent " + intent);
        aapsLogger.debug(LTag.DATASERVICE, "onHandleIntent " + BundleLogger.log(intent.getExtras()));


        boolean acceptNSData = !sp.getBoolean(R.string.key_ns_upload_only, true) && buildHelper.isEngineeringMode() || config.getNSCLIENT();

        final String action = intent.getAction();
        if (Intents.ACTION_NEW_BG_ESTIMATE.equals(action)) {
            xdripPlugin.handleNewData(intent);
        } else if (Intents.NS_EMULATOR.equals(action)) {
            mm640GPlugin.handleNewData(intent);
        } else if (Intents.GLIMP_BG.equals(action)) {
            glimpPlugin.handleNewData(intent);
        } else if (Intents.DEXCOM_BG.equals(action)) {
            dexcomPlugin.handleNewData(intent);
        } else if (Intents.POCTECH_BG.equals(action)) {
            poctechPlugin.handleNewData(intent);
        } else if (Intents.TOMATO_BG.equals(action)) {
            tomatoPlugin.handleNewData(intent);
        } else if (Intents.EVERSENSE_BG.equals(action)) {
            eversensePlugin.handleNewData(intent);
        } else if (Intents.ACTION_NEW_SGV.equals(action)) {
            nsClientSourcePlugin.handleNewData(intent);
        } else if (Intents.ACTION_NEW_PROFILE.equals(action)) {
            // always handle Profile if NSProfile is enabled without looking at nsUploadOnly
            nsProfilePlugin.handleNewData(intent);
        } else if (acceptNSData &&
                (Intents.ACTION_NEW_TREATMENT.equals(action) ||
                        Intents.ACTION_CHANGED_TREATMENT.equals(action) ||
                        Intents.ACTION_REMOVED_TREATMENT.equals(action) ||
                        Intents.ACTION_NEW_CAL.equals(action) ||
                        Intents.ACTION_NEW_MBG.equals(action))
        ) {
            handleNewDataFromNSClient(intent);
        } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            smsCommunicatorPlugin.handleNewData(intent);
        }

        aapsLogger.debug(LTag.DATASERVICE, "onHandleIntent exit " + intent);
        DataReceiver.completeWakefulIntent(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void handleNewDataFromNSClient(Intent intent) {
        Bundle bundles = intent.getExtras();
        if (bundles == null) return;
        aapsLogger.debug(LTag.DATASERVICE, "Got intent: " + intent.getAction());


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
                aapsLogger.error(LTag.DATASERVICE, "Unhandled exception", e);
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
                aapsLogger.error(LTag.DATASERVICE, "Unhandled exception", e);
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
                aapsLogger.error(LTag.DATASERVICE, "Unhandled exception", e);
            }
        }
    }

    private void handleRemovedTreatmentFromNS(JSONObject json) {
        // new DB model
        EventNsTreatment evtTreatment = new EventNsTreatment(EventNsTreatment.Companion.getREMOVE(), json);
        rxBus.send(evtTreatment);
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
        int mode = Intents.ACTION_NEW_TREATMENT.equals(intent.getAction()) ? EventNsTreatment.Companion.getADD() : EventNsTreatment.Companion.getUPDATE();
        double insulin = JsonHelper.safeGetDouble(json, "insulin");
        double carbs = JsonHelper.safeGetDouble(json, "carbs");
        String eventType = JsonHelper.safeGetString(json, "eventType");
        if (eventType == null) {
            aapsLogger.debug(LTag.DATASERVICE, "Wrong treatment. Ignoring : " + json.toString());
            return;
        }
        if (insulin > 0 || carbs > 0) {
            EventNsTreatment evtTreatment = new EventNsTreatment(mode, json);
            rxBus.send(evtTreatment);
        } else if (eventType.equals(CareportalEvent.TEMPORARYTARGET)) {
            MainApp.getDbHelper().createTemptargetFromJsonIfNotExists(json);
        } else if (eventType.equals(CareportalEvent.TEMPBASAL)) {
            MainApp.getDbHelper().createTempBasalFromJsonIfNotExists(json);
        } else if (eventType.equals(CareportalEvent.COMBOBOLUS)) {
            MainApp.getDbHelper().createExtendedBolusFromJsonIfNotExists(json);
        } else if (eventType.equals(CareportalEvent.PROFILESWITCH)) {
            MainApp.getDbHelper().createProfileSwitchFromJsonIfNotExists(activePlugin, nsUpload, json);
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
                    && !enteredBy.equals(sp.getString("careportal_enteredby", "AndroidAPS"))) {
                boolean defaultVal = config.getNSCLIENT();
                if (sp.getBoolean(R.string.key_ns_announcements, defaultVal)) {
                    Notification announcement = new Notification(Notification.NSANNOUNCEMENT, notes, Notification.ANNOUNCEMENT, 60);
                    rxBus.send(new EventNewNotification(announcement));
                }
            }
        }
    }

    private void storeMbg(JSONObject mbgJson) {
        NSMbg nsMbg = new NSMbg(mbgJson);
        CareportalEvent careportalEvent = new CareportalEvent(nsMbg);
        MainApp.getDbHelper().createOrUpdate(careportalEvent);
        aapsLogger.debug(LTag.DATASERVICE, "Adding/Updating new MBG: " + careportalEvent.toString());
    }

}
