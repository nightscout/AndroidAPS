package info.nightscout.androidaps.plugins.general.nsclient.data;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.BundleLogger;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 25.06.2017.
 */

/*
{
    "_id": "594fdcec327b83c81b6b8c0f",
    "device": "openaps://Sony D5803",
    "pump": {
        "battery": {
            "percent": 100
        },
        "status": {
            "status": "normal",
            "timestamp": "2017-06-25T15:50:14Z"
        },
        "extended": {
            "Version": "1.5-ac98852-2017.06.25",
            "PumpIOB": 1.13,
            "LastBolus": "25. 6. 2017 17:25:00",
            "LastBolusAmount": 0.3,
            "BaseBasalRate": 0.4,
            "ActiveProfile": "2016 +30%"
        },
        "reservoir": 109,
        "clock": "2017-06-25T15:55:10Z"
    },
    "openaps": {
        "suggested": {
            "temp": "absolute",
            "bg": 115.9,
            "tick": "+5",
            "eventualBG": 105,
            "snoozeBG": 105,
            "predBGs": {
                "IOB": [116, 114, 112, 110, 109, 107, 106, 105, 105, 104, 104, 104, 104, 104, 104, 104, 104, 105, 105, 105, 105, 105, 106, 106, 106, 106, 106, 107]
            },
            "COB": 0,
            "IOB": -0.035,
            "reason": "COB: 0, Dev: -18, BGI: 0.43, ISF: 216, Target: 99; Eventual BG 105 > 99 but Min. Delta -2.60 < Exp. Delta 0.1; setting current basal of 0.4 as temp. Suggested rate is same as profile rate, no temp basal is active, doing nothing",
            "timestamp": "2017-06-25T15:55:10Z"
        },
        "iob": {
            "iob": -0.035,
            "basaliob": -0.035,
            "activity": -0.0004,
            "time": "2017-06-25T15:55:10Z"
        }
    },
    "uploaderBattery": 93,
    "created_at": "2017-06-25T15:55:10Z",
    "NSCLIENT_ID": 1498406118857
}
 */
public class NSDeviceStatus {
    private Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    private static NSDeviceStatus instance = null;

    public static NSDeviceStatus getInstance() {
        if (instance == null)
            instance = new NSDeviceStatus();
        return instance;
    }

    private JSONObject data = null;

    public NSDeviceStatus() {
    }

    public void handleNewData(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        if (L.isEnabled(L.NSCLIENT))
            log.debug("Got NS devicestatus: " + BundleLogger.log(bundle));

        try {
            if (bundle.containsKey("devicestatus")) {
                JSONObject devicestatusJson = new JSONObject(bundle.getString("devicestatus"));
                setData(devicestatusJson);
                if (devicestatusJson.has("pump")) {
                    // Objectives 0
                    SP.putBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, true);
                }
            }
            if (bundle.containsKey("devicestatuses")) {
                String devicestatusesstring = bundle.getString("devicestatuses");
                JSONArray jsonArray = new JSONArray(devicestatusesstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject devicestatusJson = jsonArray.getJSONObject(i);
                    setData(devicestatusJson);
                    if (devicestatusJson.has("pump")) {
                        // Objectives 0
                        SP.putBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, true);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public NSDeviceStatus setData(JSONObject obj) {
        this.data = obj;
        updatePumpData(obj);
        updateOpenApsData(obj);
        updateUploaderData(obj);
        return this;
    }

    public String getDevice() {
        try {
            if (data.has("device")) {
                String device = data.getString("device");
                if (device.startsWith("openaps://")) {
                    device = device.substring(10);
                    return device;
                }
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return "";
    }

    public static class Levels {
        static int URGENT = 2;
        static int WARN = 1;
        static int INFO = 0;
        int LOW = -1;
        int LOWEST = -2;
        static int NONE = -3;
    }

    // ***** PUMP DATA ******

    static DeviceStatusPumpData deviceStatusPumpData = null;

    public Spanned getExtendedPumpStatus() {
        if (deviceStatusPumpData != null && deviceStatusPumpData.extended != null)
            return deviceStatusPumpData.extended;
        return Html.fromHtml("");
    }

    public Spanned getPumpStatus() {
        //String[] ALL_STATUS_FIELDS = {"reservoir", "battery", "clock", "status", "device"};

        StringBuilder string = new StringBuilder();
        string.append("<span style=\"color:" + MainApp.gs(R.color.defaulttext).replace("#ff", "#") + "\">");
        string.append(MainApp.gs(R.string.pump));
        string.append(": </span>");

        if (deviceStatusPumpData == null)
            return Html.fromHtml("");

        // test warning level
        int level = Levels.INFO;
        long now = System.currentTimeMillis();
        if (deviceStatusPumpData.clock + NSSettingsStatus.getInstance().extendedPumpSettings("urgentClock") * 60 * 1000L < now)
            level = Levels.URGENT;
        else if (deviceStatusPumpData.reservoir < NSSettingsStatus.getInstance().extendedPumpSettings("urgentRes"))
            level = Levels.URGENT;
        else if (deviceStatusPumpData.isPercent && deviceStatusPumpData.percent < NSSettingsStatus.getInstance().extendedPumpSettings("urgentBattP"))
            level = Levels.URGENT;
        else if (!deviceStatusPumpData.isPercent && deviceStatusPumpData.voltage < NSSettingsStatus.getInstance().extendedPumpSettings("urgentBattV"))
            level = Levels.URGENT;
        else if (deviceStatusPumpData.clock + NSSettingsStatus.getInstance().extendedPumpSettings("warnClock") * 60 * 1000L < now)
            level = Levels.WARN;
        else if (deviceStatusPumpData.reservoir < NSSettingsStatus.getInstance().extendedPumpSettings("warnRes"))
            level = Levels.WARN;
        else if (deviceStatusPumpData.isPercent && deviceStatusPumpData.percent < NSSettingsStatus.getInstance().extendedPumpSettings("warnBattP"))
            level = Levels.WARN;
        else if (!deviceStatusPumpData.isPercent && deviceStatusPumpData.voltage < NSSettingsStatus.getInstance().extendedPumpSettings("warnBattV"))
            level = Levels.WARN;

        string.append("<span style=\"color:");
        if (level == Levels.INFO) string.append("white\">");
        if (level == Levels.WARN) string.append("yellow\">");
        if (level == Levels.URGENT) string.append("red\">");

        String fields = NSSettingsStatus.getInstance().pumpExtentendedSettingsFields();

        if (fields.contains("reservoir")) {
            string.append((int) deviceStatusPumpData.reservoir).append("U ");
        }

        if (fields.contains("battery") && deviceStatusPumpData.isPercent) {
            string.append(deviceStatusPumpData.percent).append("% ");
        }
        if (fields.contains("battery") && !deviceStatusPumpData.isPercent) {
            string.append(Round.roundTo(deviceStatusPumpData.voltage, 0.001d)).append(" ");
        }

        if (fields.contains("clock")) {
            string.append(DateUtil.minAgo(deviceStatusPumpData.clock)).append(" ");
        }

        if (fields.contains("status")) {
            string.append(deviceStatusPumpData.status).append(" ");
        }

        if (fields.contains("device")) {
            string.append(getDevice()).append(" ");
        }


        string.append("</span>"); // color

        return Html.fromHtml(string.toString());
    }

    static class DeviceStatusPumpData {
        long clock = 0L;
        boolean isPercent = false;
        int percent = 0;
        double voltage = 0;

        String status = "N/A";
        double reservoir = 0d;

        Spanned extended = null;
    }

    public void updatePumpData(JSONObject object) {
        try {
            JSONObject pump = data != null && data.has("pump") ? data.getJSONObject("pump") : new JSONObject();

            long clock = 0L;
            if (pump.has("clock"))
                clock = DateUtil.fromISODateString(pump.getString("clock")).getTime();
            // check if this is new data
            if (clock == 0 || deviceStatusPumpData != null && clock < deviceStatusPumpData.clock)
                return;
            // create new status and process data
            deviceStatusPumpData = new DeviceStatusPumpData();
            deviceStatusPumpData.clock = clock;
            if (pump.has("status") && pump.getJSONObject("status").has("status"))
                deviceStatusPumpData.status = pump.getJSONObject("status").getString("status");
            if (pump.has("reservoir"))
                deviceStatusPumpData.reservoir = pump.getDouble("reservoir");
            if (pump.has("battery") && pump.getJSONObject("battery").has("percent")) {
                deviceStatusPumpData.isPercent = true;
                deviceStatusPumpData.percent = pump.getJSONObject("battery").getInt("percent");
            } else if (pump.has("battery") && pump.getJSONObject("battery").has("voltage")) {
                deviceStatusPumpData.isPercent = false;
                deviceStatusPumpData.voltage = pump.getJSONObject("battery").getDouble("voltage");
            }
            if (pump.has("extended")) {
                JSONObject extendedJson = pump.getJSONObject("extended");
                StringBuilder exteneded = new StringBuilder();
                Iterator<?> keys = extendedJson.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String value = extendedJson.getString(key);
                    exteneded.append("<b>").append(key).append(":</b> ").append(value).append("<br>");
                }
                deviceStatusPumpData.extended = Html.fromHtml(exteneded.toString());
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }


    // ********* OpenAPS data ***********

    static DeviceStatusOpenAPSData deviceStatusOpenAPSData = new DeviceStatusOpenAPSData();

    static class DeviceStatusOpenAPSData {
        long clockSuggested = 0L;
        long clockEnacted = 0L;

        JSONObject suggested = null;
        public JSONObject enacted = null;
    }

    public void updateOpenApsData(JSONObject object) {
        try {
            JSONObject openaps = object.has("openaps") ? object.getJSONObject("openaps") : new JSONObject();
            JSONObject suggested = openaps.has("suggested") ? openaps.getJSONObject("suggested") : new JSONObject();
            JSONObject enacted = openaps.has("enacted") ? openaps.getJSONObject("enacted") : new JSONObject();

            long clock = 0L;
            if (suggested.has("timestamp"))
                clock = DateUtil.fromISODateString(suggested.getString("timestamp")).getTime();
            // check if this is new data
            if (clock != 0 && clock > deviceStatusOpenAPSData.clockSuggested) {
                deviceStatusOpenAPSData.suggested = suggested;
                deviceStatusOpenAPSData.clockSuggested = clock;
            }

            clock = 0L;
            if (enacted.has("timestamp"))
                clock = DateUtil.fromISODateString(enacted.getString("timestamp")).getTime();
            // check if this is new data
            if (clock != 0 && clock > deviceStatusOpenAPSData.clockEnacted) {
                deviceStatusOpenAPSData.enacted = enacted;
                deviceStatusOpenAPSData.clockEnacted = clock;
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public Spanned getOpenApsStatus() {
        StringBuilder string = new StringBuilder();
        string.append("<span style=\"color:" + MainApp.gs(R.color.defaulttext).replace("#ff", "#") + "\">");
        string.append(MainApp.gs(R.string.openaps_short));
        string.append(": </span>");

        // test warning level
        int level = Levels.INFO;
        long now = System.currentTimeMillis();
        if (deviceStatusOpenAPSData.clockSuggested != 0 && deviceStatusOpenAPSData.clockSuggested + SP.getInt(R.string.key_nsalarm_urgent_staledatavalue, 16) * 60 * 1000L < now)
            level = Levels.URGENT;
        else if (deviceStatusOpenAPSData.clockSuggested != 0 && deviceStatusOpenAPSData.clockSuggested + SP.getInt(R.string.key_nsalarm_staledatavalue, 16) * 60 * 1000L < now)
            level = Levels.WARN;

        string.append("<span style=\"color:");
        if (level == Levels.INFO) string.append("white\">");
        if (level == Levels.WARN) string.append("yellow\">");
        if (level == Levels.URGENT) string.append("red\">");

        if (deviceStatusOpenAPSData.clockSuggested != 0) {
            string.append(DateUtil.minAgo(deviceStatusOpenAPSData.clockSuggested)).append(" ");
        }
        string.append("</span>"); // color

        return Html.fromHtml(string.toString());
    }

    public static long getOpenApsTimestamp() {

        if (deviceStatusOpenAPSData.clockSuggested != 0) {
            return deviceStatusOpenAPSData.clockSuggested;
        } else {
            return -1;
        }
    }

    public Spanned getExtendedOpenApsStatus() {
        StringBuilder string = new StringBuilder();

        try {
            if (deviceStatusOpenAPSData.enacted != null && deviceStatusOpenAPSData.clockEnacted != deviceStatusOpenAPSData.clockSuggested)
                string.append("<b>").append(DateUtil.minAgo(deviceStatusOpenAPSData.clockEnacted)).append("</b> ").append(deviceStatusOpenAPSData.enacted.getString("reason")).append("<br>");
            if (deviceStatusOpenAPSData.suggested != null)
                string.append("<b>").append(DateUtil.minAgo(deviceStatusOpenAPSData.clockSuggested)).append("</b> ").append(deviceStatusOpenAPSData.suggested.getString("reason")).append("<br>");
            return Html.fromHtml(string.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return Html.fromHtml("");
    }

    // ********* Uploader data ***********

    public static HashMap<String, Uploader> uploaders = new HashMap<>();

    static class Uploader {
        long clock = 0L;
        int battery = 0;
    }

    public void updateUploaderData(JSONObject object) {
        try {

            long clock = 0L;
            if (object.has("mills"))
                clock = object.getLong("mills");
            else if (object.has("created_at"))
                clock = DateUtil.fromISODateString(object.getString("created_at")).getTime();
            String device = getDevice();
            Integer battery = null;
            if (object.has("uploaderBattery"))
                battery = object.getInt("uploaderBattery");
            else if (object.has("uploader")) {
                if (object.getJSONObject("uploader").has("battery"))
                    battery = object.getJSONObject("uploader").getInt("battery");
            }
            Uploader uploader = uploaders.get(device);
            // check if this is new data
            if (clock != 0 && battery != null && (uploader != null && clock > uploader.clock || uploader == null)) {
                if (uploader == null)
                    uploader = new Uploader();
                uploader.battery = battery;
                uploader.clock = clock;
                uploaders.put(device, uploader);
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public String getUploaderStatus() {
        Iterator iter = uploaders.entrySet().iterator();
        int minBattery = 100;
        while (iter.hasNext()) {
            Map.Entry pair = (Map.Entry) iter.next();
            Uploader uploader = (Uploader) pair.getValue();
            if (minBattery > uploader.battery)
                minBattery = uploader.battery;
        }

        return minBattery + "%";
    }

    public Spanned getUploaderStatusSpanned() {
        StringBuilder string = new StringBuilder();
        string.append("<span style=\"color:" + MainApp.gs(R.color.defaulttext).replace("#ff", "#") + "\">");
        string.append(MainApp.gs(R.string.uploader_short));
        string.append(": </span>");

        Iterator iter = uploaders.entrySet().iterator();
        int minBattery = 100;
        while (iter.hasNext()) {
            Map.Entry pair = (Map.Entry) iter.next();
            Uploader uploader = (Uploader) pair.getValue();
            if (minBattery > uploader.battery)
                minBattery = uploader.battery;
        }

        string.append(minBattery);
        string.append("%");
        return Html.fromHtml(string.toString());
    }

    public Spanned getExtendedUploaderStatus() {
        StringBuilder string = new StringBuilder();

        Iterator iter = uploaders.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry pair = (Map.Entry) iter.next();
            Uploader uploader = (Uploader) pair.getValue();
            String device = (String) pair.getKey();
            string.append("<b>").append(device).append(":</b> ").append(uploader.battery).append("%<br>");
        }

        return Html.fromHtml(string.toString());
    }

    public static APSResult getAPSResult() {
        APSResult result = new APSResult();
        result.json = deviceStatusOpenAPSData.suggested;
        result.date = deviceStatusOpenAPSData.clockSuggested;
        return result;
    }

}
