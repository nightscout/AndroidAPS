package app.aaps.pump.omnipod.eros.extensions

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import com.google.gson.Gson

fun DetailedBolusInfo.toJsonString(): String = Gson().toJson(this)

// Cannot access Companion extension from java so create common
fun DetailedBolusInfo.fromJsonString(json: String): DetailedBolusInfo =
    Gson().fromJson(json, DetailedBolusInfo::class.java)




