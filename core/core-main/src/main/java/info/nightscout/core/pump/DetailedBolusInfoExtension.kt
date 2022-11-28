package info.nightscout.core.pump

import com.google.gson.Gson
import info.nightscout.interfaces.pump.DetailedBolusInfo

fun DetailedBolusInfo.toJsonString(): String = Gson().toJson(this)

// Cannot access Companion extension from java so create common
fun DetailedBolusInfo.fromJsonString(json: String): DetailedBolusInfo =
    Gson().fromJson(json, DetailedBolusInfo::class.java)




