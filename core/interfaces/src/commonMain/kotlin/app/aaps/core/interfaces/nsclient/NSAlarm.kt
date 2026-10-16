package app.aaps.core.interfaces.nsclient

interface NSAlarm {
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

    val level: Int
    val group: String
    val title: String
    val message: String
    val low: Boolean
    val high: Boolean
    val timeAgo: Boolean
}