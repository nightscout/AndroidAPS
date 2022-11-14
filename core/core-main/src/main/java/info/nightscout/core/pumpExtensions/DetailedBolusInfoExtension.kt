package info.nightscout.core.pumpExtensions

import com.google.gson.Gson
import info.nightscout.database.impl.transactions.InsertOrUpdateBolusTransaction
import info.nightscout.database.impl.transactions.InsertOrUpdateCarbsTransaction
import info.nightscout.interfaces.pump.DetailedBolusInfo

fun DetailedBolusInfo.insertCarbsTransaction(): InsertOrUpdateCarbsTransaction {
    if (carbs == 0.0) throw IllegalStateException("carbs == 0.0")
    return InsertOrUpdateCarbsTransaction(createCarbs()!!)
}

fun DetailedBolusInfo.insertBolusTransaction(): InsertOrUpdateBolusTransaction {
    if (insulin == 0.0) throw IllegalStateException("insulin == 0.0")
    return InsertOrUpdateBolusTransaction(createBolus()!!)
}

fun DetailedBolusInfo.toJsonString(): String = Gson().toJson(this)

// Cannot access Companion extension from java so create common
fun DetailedBolusInfo.fromJsonString(json: String): DetailedBolusInfo =
    Gson().fromJson(json, DetailedBolusInfo::class.java)




