package info.nightscout.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.DeviceStatus;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.DbLogger;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.OpenAPSMA.DetermineBasalResultMA;

/**
 * Created by mike on 26.05.2017.
 */

public class NSUpload {
    private static Logger log = LoggerFactory.getLogger(NSUpload.class);

    public static void uploadTempBasalStartAbsolute(TemporaryBasal temporaryBasal, Double originalExtendedAmount) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPBASAL);
            data.put("duration", temporaryBasal.durationInMinutes);
            data.put("absolute", temporaryBasal.absoluteRate);
            if (temporaryBasal.pumpId != 0)
                data.put("pumpId", temporaryBasal.pumpId);
            data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
            data.put("enteredBy", "openaps://" + MainApp.instance().getString(R.string.app_name));
            if (originalExtendedAmount != null)
                data.put("originalExtendedAmount", originalExtendedAmount); // for back synchronization
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadTempBasalStartPercent(TemporaryBasal temporaryBasal) {
        try {
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            boolean useAbsolute = SP.getBoolean("ns_sync_use_absolute", false);
            if (useAbsolute) {
                TemporaryBasal t = temporaryBasal.clone();
                t.isAbsolute = true;
                Profile profile = MainApp.getConfigBuilder().getProfile();
                if (profile != null) {
                    t.absoluteRate = profile.getBasal(temporaryBasal.date) * temporaryBasal.percentRate / 100d;
                    uploadTempBasalStartAbsolute(t, null);
                }
            } else {
                Context context = MainApp.instance().getApplicationContext();
                JSONObject data = new JSONObject();
                data.put("eventType", CareportalEvent.TEMPBASAL);
                data.put("duration", temporaryBasal.durationInMinutes);
                data.put("percent", temporaryBasal.percentRate - 100);
                if (temporaryBasal.pumpId != 0)
                    data.put("pumpId", temporaryBasal.pumpId);
                data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
                data.put("enteredBy", "openaps://" + MainApp.instance().getString(R.string.app_name));
                Bundle bundle = new Bundle();
                bundle.putString("action", "dbAdd");
                bundle.putString("collection", "treatments");
                bundle.putString("data", data.toString());
                Intent intent = new Intent(Intents.ACTION_DATABASE);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
                DbLogger.dbAdd(intent, data.toString());
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadTempBasalEnd(long time, boolean isFakedTempBasal, long pumpId) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPBASAL);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + MainApp.instance().getString(R.string.app_name));
            if (isFakedTempBasal)
                data.put("isFakedTempBasal", isFakedTempBasal);
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadExtendedBolus(ExtendedBolus extendedBolus) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.COMBOBOLUS);
            data.put("duration", extendedBolus.durationInMinutes);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", extendedBolus.insulin);
            data.put("relative", extendedBolus.insulin);
            if (extendedBolus.pumpId != 0)
                data.put("pumpId", extendedBolus.pumpId);
            data.put("created_at", DateUtil.toISOString(extendedBolus.date));
            data.put("enteredBy", "openaps://" + MainApp.instance().getString(R.string.app_name));
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadExtendedBolusEnd(long time, long pumpId) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.COMBOBOLUS);
            data.put("duration", 0);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", 0);
            data.put("relative", 0);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + MainApp.instance().getString(R.string.app_name));
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadDeviceStatus() {
        DeviceStatus deviceStatus = new DeviceStatus();
        try {
            LoopPlugin.LastRun lastRun = LoopPlugin.lastRun;
            if (lastRun != null && lastRun.lastAPSRun.getTime() > System.currentTimeMillis() - 300 * 1000L) {
                // do not send if result is older than 1 min
                APSResult apsResult = lastRun.request;
                apsResult.json().put("timestamp", DateUtil.toISOString(lastRun.lastAPSRun));
                deviceStatus.suggested = apsResult.json();

                if (lastRun.request instanceof DetermineBasalResultMA) {
                    DetermineBasalResultMA result = (DetermineBasalResultMA) lastRun.request;
                    deviceStatus.iob = result.iob.json();
                    deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.lastAPSRun));
                }

                if (lastRun.request instanceof DetermineBasalResultAMA) {
                    DetermineBasalResultAMA result = (DetermineBasalResultAMA) lastRun.request;
                    deviceStatus.iob = result.iob.json();
                    deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.lastAPSRun));
                }

                if (lastRun.setByPump != null && lastRun.setByPump.enacted) { // enacted
                    deviceStatus.enacted = lastRun.request.json();
                    deviceStatus.enacted.put("rate", lastRun.setByPump.json().get("rate"));
                    deviceStatus.enacted.put("duration", lastRun.setByPump.json().get("duration"));
                    deviceStatus.enacted.put("recieved", true);
                    JSONObject requested = new JSONObject();
                    requested.put("duration", lastRun.request.duration);
                    requested.put("rate", lastRun.request.rate);
                    requested.put("temp", "absolute");
                    deviceStatus.enacted.put("requested", requested);
                }
            } else {
                log.debug("OpenAPS data too old to upload");
            }
            deviceStatus.device = "openaps://" + Build.MANUFACTURER + " " + Build.MODEL;
            JSONObject pumpstatus = ConfigBuilderPlugin.getActivePump().getJSONStatus();
            if (pumpstatus != null) {
                deviceStatus.pump = pumpstatus;
            }

            int batteryLevel = BatteryLevel.getBatteryLevel();
            deviceStatus.uploaderBattery = batteryLevel;

            deviceStatus.created_at = DateUtil.toISOString(new Date());
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "devicestatus");
            bundle.putString("data", deviceStatus.mongoRecord().toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, deviceStatus.mongoRecord().toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadBolusWizardRecord(DetailedBolusInfo detailedBolusInfo) {
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
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        uploadCareportalEntryToNS(data);
    }

    public static void uploadProfileSwitch(ProfileSwitch profileSwitch) {
        try {
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
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void updateProfileSwitch(ProfileSwitch profileSwitch) {
        try {
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
            data.put("enteredBy", MainApp.instance().getString(R.string.app_name));
            if (profileSwitch._id != null) {
                Context context = MainApp.instance().getApplicationContext();
                Bundle bundle = new Bundle();
                bundle.putString("action", "dbUpdate");
                bundle.putString("collection", "treatments");
                bundle.putString("data", data.toString());
                bundle.putString("_id", profileSwitch._id);
                Intent intent = new Intent(Intents.ACTION_DATABASE);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
                DbLogger.dbAdd(intent, data.toString());
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadCareportalEntryToNS(JSONObject data) {
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
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString());
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public static void removeCareportalEntryFromNS(String _id) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbRemove");
            bundle.putString("collection", "treatments");
            bundle.putString("_id", _id);
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbRemove(intent, _id);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public static void uploadOpenAPSOffline(double durationInMinutes) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "OpenAPS Offline");
            data.put("duration", durationInMinutes);
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", "openaps://" + MainApp.instance().getString(R.string.app_name));
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void uploadError(String error) {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Announcement");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("notes", error);
            data.put("isAnnouncement", true);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        bundle.putString("data", data.toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString());
    }

    public static void uploadBg(BgReading reading) {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "entries");
        JSONObject data = new JSONObject();
        try {
            data.put("device", "AndroidAPS-DexcomG5");
            data.put("date", reading.date);
            data.put("dateString", DateUtil.toISOString(reading.date));
            data.put("sgv", reading.value);
            data.put("direction", reading.direction);
            data.put("type", "sgv");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        bundle.putString("data", data.toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString());
    }

    public static void uploadAppStart() {
        if (SP.getBoolean(R.string.key_ns_logappstartedevent, true)) {
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            JSONObject data = new JSONObject();
            try {
                data.put("eventType", "Note");
                data.put("created_at", DateUtil.toISOString(new Date()));
                data.put("notes", MainApp.sResources.getString(R.string.androidaps_start));
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbAdd(intent, data.toString());
        }
    }

    public static void removeFoodFromNS(String _id) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbRemove");
            bundle.putString("collection", "food");
            bundle.putString("_id", _id);
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            DbLogger.dbRemove(intent, _id);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public static void sendToXdrip(BgReading bgReading) {
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
            MainApp.instance().sendBroadcast(intent);
            List<ResolveInfo> receivers = MainApp.instance().getPackageManager().queryBroadcastReceivers(intent, 0);
            if (receivers.size() < 1) {
                log.debug("No xDrip receivers found. ");
            } else {
                log.debug(receivers.size() + " xDrip receivers");
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public static boolean isIdValid(String _id) {
        if (_id == null)
            return false;
        if (_id.length() == 24)
            return true;
        return false;
    }
}
