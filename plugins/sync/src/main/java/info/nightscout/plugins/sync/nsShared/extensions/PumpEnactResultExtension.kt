package info.nightscout.plugins.sync.nsShared.extensions

import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.utils.Round
import org.json.JSONObject

fun PumpEnactResult.log(): String {
    return "Success: " + success +
        " Enacted: " + enacted +
        " Comment: " + comment +
        " Duration: " + duration +
        " Absolute: " + absolute +
        " Percent: " + percent +
        " IsPercent: " + isPercent +
        " IsTempCancel: " + isTempCancel +
        " bolusDelivered: " + bolusDelivered +
        " carbsDelivered: " + carbsDelivered +
        " Queued: " + queued
}