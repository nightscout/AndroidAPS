package info.nightscout.plugins.sync.nsShared

import info.nightscout.core.utils.JsonHelper
import info.nightscout.interfaces.nsclient.NSAlarm
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

    override fun level(): Int =
        JsonHelper.safeGetInt(data, "level", 0)

    override fun group(): String =
        JsonHelper.safeGetString(data, "group", "N/A")

    override fun title(): String =
        JsonHelper.safeGetString(data, "title", "N/A")

    override fun message(): String =
        JsonHelper.safeGetString(data, "message", "N/A")

    override fun low(): Boolean =
        JsonHelper.safeGetString(data, "eventName", "") == "low"

    override fun high(): Boolean =
        JsonHelper.safeGetString(data, "eventName", "") == "high"

    override fun timeago(): Boolean =
        JsonHelper.safeGetString(data, "eventName", "") == "timeago"
}