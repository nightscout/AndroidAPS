package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.interfaces.IobCobCalculatorInterface;
import info.nightscout.androidaps.interfaces.LoopInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.UploadQueueInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.plugins.aps.loop.DeviceStatus;
import info.nightscout.androidaps.receivers.ReceiverStatusStore;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 26.05.2017.
 */
@Singleton
public class NSUpload {

    private final HasAndroidInjector injector;
    private final AAPSLogger aapsLogger;
    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final Context context;
    private final UploadQueueInterface uploadQueue;
    private final DatabaseHelperInterface databaseHelper;

    @Inject
    public NSUpload(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            ResourceHelper resourceHelper,
            SP sp,
            Context context,
            UploadQueueInterface uploadQueue,
            DatabaseHelperInterface databaseHelper
    ) {
        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.context = context;
        this.uploadQueue = uploadQueue;
        this.databaseHelper = databaseHelper;
    }

    public void uploadTempBasalStartAbsolute(TemporaryBasal temporaryBasal, Double originalExtendedAmount) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPBASAL);
            data.put("duration", temporaryBasal.durationInMinutes);
            data.put("absolute", temporaryBasal.absoluteRate);
            data.put("rate", temporaryBasal.absoluteRate);
            if (temporaryBasal.pumpId != 0)
                data.put("pumpId", temporaryBasal.pumpId);
            data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            if (originalExtendedAmount != null)
                data.put("originalExtendedAmount", originalExtendedAmount); // for back synchronization
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTempBasalStartPercent(TemporaryBasal temporaryBasal, Profile profile) {
        try {
            boolean useAbsolute = sp.getBoolean(R.string.key_ns_sync_use_absolute, false);
            double absoluteRate = 0;
            if (profile != null) {
                absoluteRate = profile.getBasal(temporaryBasal.date) * temporaryBasal.percentRate / 100d;
            }
            if (useAbsolute) {
                TemporaryBasal t = temporaryBasal.clone();
                t.isAbsolute = true;
                if (profile != null) {
                    t.absoluteRate = absoluteRate;
                    uploadTempBasalStartAbsolute(t, null);
                }
            } else {
                JSONObject data = new JSONObject();
                data.put("eventType", CareportalEvent.TEMPBASAL);
                data.put("duration", temporaryBasal.durationInMinutes);
                data.put("percent", temporaryBasal.percentRate - 100);
                if (profile != null)
                    data.put("rate", absoluteRate);
                if (temporaryBasal.pumpId != 0)
                    data.put("pumpId", temporaryBasal.pumpId);
                data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
                data.put("enteredBy", "openaps://" + "AndroidAPS");
                uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTempBasalEnd(long time, boolean isFakedTempBasal, long pumpId) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPBASAL);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            if (isFakedTempBasal)
                data.put("isFakedTempBasal", isFakedTempBasal);
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadExtendedBolus(ExtendedBolus extendedBolus) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.COMBOBOLUS);
            data.put("duration", extendedBolus.durationInMinutes);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", extendedBolus.insulin);
            data.put("relative", extendedBolus.insulin / extendedBolus.durationInMinutes * 60); // U/h
            if (extendedBolus.pumpId != 0)
                data.put("pumpId", extendedBolus.pumpId);
            data.put("created_at", DateUtil.toISOString(extendedBolus.date));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadExtendedBolusEnd(long time, long pumpId) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.COMBOBOLUS);
            data.put("duration", 0);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", 0);
            data.put("relative", 0);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadDeviceStatus(LoopInterface loopPlugin, IobCobCalculatorInterface iobCobCalculatorPlugin, ProfileFunction profileFunction, PumpInterface pumpInterface, ReceiverStatusStore receiverStatusStore, String version) {
        Profile profile = profileFunction.getProfile();
        String profileName = profileFunction.getProfileName();

        if (profile == null) {
            aapsLogger.error("Profile is null. Skipping upload");
            return;
        }

        DeviceStatus deviceStatus = new DeviceStatus(aapsLogger);
        try {
            LoopInterface.LastRun lastRun = loopPlugin.getLastRun();
            if (lastRun != null && lastRun.getLastAPSRun() > System.currentTimeMillis() - 300 * 1000L) {
                // do not send if result is older than 1 min
                APSResult apsResult = lastRun.getRequest();
                apsResult.json().put("timestamp", DateUtil.toISOString(lastRun.getLastAPSRun()));
                deviceStatus.suggested = apsResult.json();

                deviceStatus.iob = lastRun.getRequest().iob.json();
                deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.getLastAPSRun()));

                JSONObject requested = new JSONObject();

                if (lastRun.getTbrSetByPump() != null && lastRun.getTbrSetByPump().enacted) { // enacted
                    deviceStatus.enacted = lastRun.getRequest().json();
                    deviceStatus.enacted.put("rate", lastRun.getTbrSetByPump().json(profile).get("rate"));
                    deviceStatus.enacted.put("duration", lastRun.getTbrSetByPump().json(profile).get("duration"));
                    deviceStatus.enacted.put("recieved", true);
                    requested.put("duration", lastRun.getRequest().duration);
                    requested.put("rate", lastRun.getRequest().rate);
                    requested.put("temp", "absolute");
                    deviceStatus.enacted.put("requested", requested);
                }
                if (lastRun.getTbrSetByPump() != null && lastRun.getTbrSetByPump().enacted) { // enacted
                    if (deviceStatus.enacted == null) {
                        deviceStatus.enacted = lastRun.getRequest().json();
                    }
                    deviceStatus.enacted.put("smb", lastRun.getTbrSetByPump().bolusDelivered);
                    requested.put("smb", lastRun.getRequest().smb);
                    deviceStatus.enacted.put("requested", requested);
                }
            } else {
                aapsLogger.debug(LTag.NSCLIENT, "OpenAPS data too old to upload, sending iob only");
                IobTotal[] iob = iobCobCalculatorPlugin.calculateIobArrayInDia(profile);
                if (iob.length > 0) {
                    deviceStatus.iob = iob[0].json();
                    deviceStatus.iob.put("time", DateUtil.toISOString(DateUtil.now()));
                }
            }
            deviceStatus.device = "openaps://" + Build.MANUFACTURER + " " + Build.MODEL;
            JSONObject pumpstatus = pumpInterface.getJSONStatus(profile, profileName, version);
            if (pumpstatus != null) {
                deviceStatus.pump = pumpstatus;
            }

            int batteryLevel = receiverStatusStore.getBatteryLevel();
            deviceStatus.uploaderBattery = batteryLevel;

            deviceStatus.created_at = DateUtil.toISOString(new Date());
            uploadQueue.add(new DbRequest("dbAdd", "devicestatus", deviceStatus.mongoRecord()));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTreatmentRecord(DetailedBolusInfo detailedBolusInfo) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", detailedBolusInfo.eventType);
            if (detailedBolusInfo.insulin != 0d) data.put("insulin", detailedBolusInfo.insulin);
            if (detailedBolusInfo.carbs != 0d) data.put("carbs", (int) detailedBolusInfo.carbs);
            data.put("created_at", DateUtil.toISOString(detailedBolusInfo.date));
            data.put("date", detailedBolusInfo.date);
            data.put("isSMB", detailedBolusInfo.isSMB);
            if (detailedBolusInfo.pumpId != 0)
                data.put("pumpId", detailedBolusInfo.pumpId);
            if (detailedBolusInfo.glucose != 0d)
                data.put("glucose", detailedBolusInfo.glucose);
            if (!detailedBolusInfo.glucoseType.equals(""))
                data.put("glucoseType", detailedBolusInfo.glucoseType);
            if (detailedBolusInfo.boluscalc != null)
                data.put("boluscalc", detailedBolusInfo.boluscalc);
            if (detailedBolusInfo.carbTime != 0)
                data.put("preBolus", detailedBolusInfo.carbTime);
            if (!StringUtils.isEmpty(detailedBolusInfo.notes)) {
                data.put("notes", detailedBolusInfo.notes);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadCareportalEntryToNS(data);
    }

    public void uploadProfileSwitch(ProfileSwitch profileSwitch) {
        try {
            JSONObject data = getJson(profileSwitch);
            uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTempTarget(TempTarget tempTarget, ProfileFunction profileFunction) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPORARYTARGET);
            data.put("duration", tempTarget.durationInMinutes);
            if (tempTarget.low > 0) {
                data.put("reason", tempTarget.reason);
                data.put("targetBottom", Profile.fromMgdlToUnits(tempTarget.low, profileFunction.getUnits()));
                data.put("targetTop", Profile.fromMgdlToUnits(tempTarget.high, profileFunction.getUnits()));
                data.put("units", profileFunction.getUnits());
            }
            data.put("created_at", DateUtil.toISOString(tempTarget.date));
            data.put("enteredBy", "AndroidAPS");
            uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void updateProfileSwitch(ProfileSwitch profileSwitch) {
        try {
            JSONObject data = getJson(profileSwitch);
            if (profileSwitch._id != null) {
                uploadQueue.add(new DbRequest("dbUpdate", "treatments", profileSwitch._id, data));
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    private static JSONObject getJson(ProfileSwitch profileSwitch) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("eventType", CareportalEvent.PROFILESWITCH);
        data.put("duration", profileSwitch.durationInMinutes);
        data.put("profile", profileSwitch.getCustomizedName());
        data.put("profileJson", profileSwitch.profileJson);
        data.put("profilePlugin", profileSwitch.profilePlugin);
        if (profileSwitch.isCPP) {
            data.put("CircadianPercentageProfile", true);
            data.put("timeshift", profileSwitch.timeshift);
            data.put("percentage", profileSwitch.percentage);
        }
        data.put("created_at", DateUtil.toISOString(profileSwitch.date));
        data.put("enteredBy", "AndroidAPS");

        return data;
    }

    public void uploadCareportalEntryToNS(JSONObject data) {
        try {
            if (data.has("preBolus") && data.has("carbs")) {
                JSONObject prebolus = new JSONObject();
                prebolus.put("carbs", data.get("carbs"));
                data.remove("carbs");
                prebolus.put("eventType", data.get("eventType"));
                if (data.has("enteredBy")) prebolus.put("enteredBy", data.get("enteredBy"));
                if (data.has("notes")) prebolus.put("notes", data.get("notes"));
                long mills = DateUtil.fromISODateString(data.getString("created_at")).getTime();
                Date preBolusDate = new Date(mills + data.getInt("preBolus") * 60000L + 1000L);
                prebolus.put("created_at", DateUtil.toISOString(preBolusDate));
                uploadCareportalEntryToNS(prebolus);
            }
            DbRequest dbr = new DbRequest("dbAdd", "treatments", data);
            aapsLogger.debug("Prepared: " + dbr.log());
            uploadQueue.add(dbr);
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }

    }

    public void removeCareportalEntryFromNS(String _id) {
        uploadQueue.add(new DbRequest("dbRemove", "treatments", _id));
    }

    public void uploadOpenAPSOffline(CareportalEvent event) {
        try {
            JSONObject data = new JSONObject(event.json);
            data.put("created_at", DateUtil.toISOString(event.date));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadError(String error) {
        uploadError(error, new Date());
    }

    public void uploadError(String error, Date date) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Announcement");
            data.put("created_at", DateUtil.toISOString(date));
            data.put("enteredBy", sp.getString("careportal_enteredby", "AndroidAPS"));
            data.put("notes", error);
            data.put("isAnnouncement", true);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
    }

    public void uploadBg(BgReading reading, String source) {
        JSONObject data = new JSONObject();
        try {
            data.put("device", source);
            data.put("date", reading.date);
            data.put("dateString", DateUtil.toISOString(reading.date));
            data.put("sgv", reading.value);
            data.put("direction", reading.direction);
            data.put("type", "sgv");
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadQueue.add(new DbRequest("dbAdd", "entries", data));
    }

    public void uploadAppStart() {
        if (sp.getBoolean(R.string.key_ns_logappstartedevent, true)) {
            JSONObject data = new JSONObject();
            try {
                data.put("eventType", "Note");
                data.put("created_at", DateUtil.toISOString(new Date()));
                data.put("notes", resourceHelper.gs(R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL);
            } catch (JSONException e) {
                aapsLogger.error("Unhandled exception", e);
            }
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
        }
    }

    public void uploadProfileStore(JSONObject profileStore) {
        if (sp.getBoolean(R.string.key_ns_uploadlocalprofile, false)) {
            uploadQueue.add(new DbRequest("dbAdd", "profile", profileStore));
        }
    }

    public void uploadEvent(String careportalEvent, long time, @Nullable String notes) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", careportalEvent);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", sp.getString("careportal_enteredby", "AndroidAPS"));
            if (notes != null) {
                data.put("notes", notes);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadQueue.add(new DbRequest("dbAdd", "treatments", data));
    }

    public void removeFoodFromNS(String _id) {
        try {
            uploadQueue.add(new DbRequest("dbRemove", "food", _id));
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }

    }

    public void sendToXdrip(BgReading bgReading) {
        final String XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

        try {
            final JSONArray entriesBody = new JSONArray();
            JSONObject json = new JSONObject();
            json.put("sgv", bgReading.value);
            if (bgReading.direction == null) {
                json.put("direction", "NONE");
            } else {
                json.put("direction", bgReading.direction);
            }
            json.put("device", "G5");
            json.put("type", "sgv");
            json.put("date", bgReading.date);
            json.put("dateString", format.format(bgReading.date));
            entriesBody.put(json);

            final Bundle bundle = new Bundle();
            bundle.putString("action", "add");
            bundle.putString("collection", "entries");
            bundle.putString("data", entriesBody.toString());
            final Intent intent = new Intent(XDRIP_PLUS_NS_EMULATOR);
            intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            List<ResolveInfo> receivers = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (receivers.size() < 1) {
                aapsLogger.debug("No xDrip receivers found. ");
            } else {
                aapsLogger.debug(receivers.size() + " xDrip receivers");
            }


        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }

    }

    public void createNSTreatment(JSONObject data, ProfileStore profileStore, ProfileFunction profileFunction, long eventTime) {
        if (JsonHelper.safeGetString(data, "eventType", "").equals(CareportalEvent.PROFILESWITCH)) {
            ProfileSwitch profileSwitch = profileFunction.prepareProfileSwitch(
                    profileStore,
                    JsonHelper.safeGetString(data, "profile"),
                    JsonHelper.safeGetInt(data, "duration"),
                    JsonHelper.safeGetInt(data, "percentage"),
                    JsonHelper.safeGetInt(data, "timeshift"),
                    eventTime
            );
            uploadProfileSwitch(profileSwitch);
        } else {
            uploadCareportalEntryToNS(data);
        }
    }

    public static boolean isIdValid(String _id) {
        if (_id == null)
            return false;
        if (_id.length() == 24)
            return true;
        return false;
    }

    public void generateCareportalEvent(String eventType, long time, String notes) {
        CareportalEvent careportalEvent = new CareportalEvent(injector);
        careportalEvent.source = Source.USER;
        careportalEvent.date = time;
        careportalEvent.json = generateJson(eventType, time, notes).toString();
        careportalEvent.eventType = eventType;
        databaseHelper.createOrUpdate(careportalEvent);
        uploadEvent(eventType, time, notes);
    }

    private JSONObject generateJson(String careportalEvent, long time, String notes) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", careportalEvent);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("mills", time);
            data.put("enteredBy", sp.getString("careportal_enteredby", "AndroidAPS"));
            if (!notes.isEmpty()) data.put("notes", notes);
        } catch (JSONException ignored) {
        }
        return data;
    }

}
