package app.aaps.plugins.sync.nsShared

import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

class NSAlarmObject(private var data: JSONObject) : NSAlarm {
    /*
    {
    "level":2,
    "title":"Urgent HIGH",
    "message":"BG Now: 5.2 -0.1 → mmol\/L\nRaw BG: 5 mmol\/L Čistý\nBG 15m: 5 mmol\/L\nIOB: 0.00U\nCOB: 0g",
    "eventName":"high",
    "plugin":{"name":"simplealarms","label":"Simple Alarms","pluginType":"notification","enabled":true},
    "pushoverSound":"persistent",
    "debug":{"lastSGV":5.2,"thresholds":{"bgHigh":80,"bgTargetTop":75,"bgTargetBottom":72,"bgLow":70}},
    "group":"default",
    "key":"simplealarms_2"
    }
     */

    override val level: Int
        get() =
        JsonHelper.safeGetInt(data, "level", 0)

    override val group: String
        get() =
        JsonHelper.safeGetString(data, "group", "N/A")

    override val title: String
        get() =
        JsonHelper.safeGetString(data, "title", "N/A")

    override val message: String
        get() =
        JsonHelper.safeGetString(data, "message", "N/A")

    override val low: Boolean
        get() =
        JsonHelper.safeGetString(data, "eventName", "") == "low"

    override val high: Boolean
        get() =
        JsonHelper.safeGetString(data, "eventName", "") == "high"

    override val timeAgo: Boolean
        get() =
        JsonHelper.safeGetString(data, "eventName", "") == "timeago"
}