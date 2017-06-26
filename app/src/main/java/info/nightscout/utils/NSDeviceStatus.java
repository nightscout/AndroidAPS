package info.nightscout.utils;

import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSettingsStatus;

import static android.R.attr.value;

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

    private static NSDeviceStatus instance = null;

    public static NSDeviceStatus getInstance() {
        if (instance == null)
            instance = new NSDeviceStatus();
        return instance;
    }

    private JSONObject data = null;

    public NSDeviceStatus() {
    }

    public NSDeviceStatus setData(JSONObject obj) {
        this.data = obj;
        updatePumpData(obj);
        return this;
    }

    public String getDevice() {
        try {
            if (data.has("device")) {
                return data.getString("device");
            }
        } catch (JSONException e) {
            e.printStackTrace();
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
        if (deviceStatusPumpData.extended != null)
            return deviceStatusPumpData.extended;
        return Html.fromHtml("");
    }

    public Spanned getPumpStatus() {
        //String[] ALL_STATUS_FIELDS = {"reservoir", "battery", "clock", "status", "device"};

        if (deviceStatusPumpData == null)
            return Html.fromHtml("");

        StringBuilder string = new StringBuilder();
        // test wanring level
        int level = Levels.INFO;
        long now = System.currentTimeMillis();
        if (deviceStatusPumpData.clock + NSSettingsStatus.getInstance().extendedPumpSettings("urgentClock") < now)
            level = Levels.URGENT;
        else if (deviceStatusPumpData.reservoir < NSSettingsStatus.getInstance().extendedPumpSettings("urgentRes"))
            level = Levels.URGENT;
        else if (deviceStatusPumpData.isPercent && deviceStatusPumpData.percent < NSSettingsStatus.getInstance().extendedPumpSettings("urgentBattP"))
            level = Levels.URGENT;
        else if (!deviceStatusPumpData.isPercent && deviceStatusPumpData.voltage < NSSettingsStatus.getInstance().extendedPumpSettings("urgentBattV"))
            level = Levels.URGENT;
        else if (deviceStatusPumpData.clock + NSSettingsStatus.getInstance().extendedPumpSettings("warnClock") < now)
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
            e.printStackTrace();
        }
    }

}
