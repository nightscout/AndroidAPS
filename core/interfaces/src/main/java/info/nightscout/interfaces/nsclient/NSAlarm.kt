package info.nightscout.interfaces.nsclient

import info.nightscout.interfaces.utils.JsonHelper
import org.json.JSONObject

class NSAlarm(private var data: JSONObject) {
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

    fun level(): Int =
        JsonHelper.safeGetInt(data, "level", 0)

    fun group(): String =
        JsonHelper.safeGetString(data, "group", "N/A")

    fun title(): String =
        JsonHelper.safeGetString(data, "title", "N/A")

    fun message(): String =
        JsonHelper.safeGetString(data, "message", "N/A")

    fun low() :Boolean =
        JsonHelper.safeGetString(data, "eventName", "") == "low"

    fun high() :Boolean =
        JsonHelper.safeGetString(data, "eventName", "") == "high"

    fun timeago() :Boolean =
        JsonHelper.safeGetString(data, "eventName", "") == "timeago"
}