package info.nightscout.implementation.pump

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.implementation.R
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@OpenForTesting
@Singleton
class DetailedBolusInfoStorageImpl @Inject constructor(
    val aapsLogger: AAPSLogger,
    val sp: SP,
    val rh: ResourceHelper
) : DetailedBolusInfoStorage {

    val store = loadStore()

    fun DetailedBolusInfo.toJsonString(): String = Gson().toJson(this)

    @Synchronized
    override fun add(detailedBolusInfo: DetailedBolusInfo) {
        aapsLogger.debug("Stored bolus info: ${detailedBolusInfo.toJsonString()}")
        store.add(detailedBolusInfo)
        saveStore()
    }

    @Synchronized
    override fun findDetailedBolusInfo(bolusTime: Long, bolus: Double): DetailedBolusInfo? {
        // Look for info with bolus
        for (i in store.indices) {
            val d = store[i]
            //aapsLogger.debug(LTag.PUMP, "Existing bolus info: " + store[i])
            if (bolusTime > d.timestamp - T.mins(1).msecs() && bolusTime < d.timestamp + T.mins(1).msecs() && abs(store[i].insulin - bolus) < 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using & removing bolus info for time $bolusTime: ${store[i]}")
                store.removeAt(i)
                saveStore()
                return d
            }
        }
        // If not found use time only
        for (i in store.indices) {
            val d = store[i]
            if (bolusTime > d.timestamp - T.mins(1).msecs() && bolusTime < d.timestamp + T.mins(1).msecs() && bolus <= store[i].insulin + 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using TIME-ONLY & removing bolus info for time $bolusTime: ${store[i]}")
                store.removeAt(i)
                saveStore()
                return d
            }
        }
        // If not found, use last record if amount is the same
        // if (store.size > 0) {
        //     val d = store[store.size - 1]
        //     if (abs(d.insulin - bolus) < 0.01) {
        //         aapsLogger.debug(LTag.PUMP, "Using LAST & removing bolus info for time $bolusTime: $d")
        //         store.removeAt(store.size - 1)
        //         return d
        //     }
        // }
        //Not found
        aapsLogger.debug(LTag.PUMP, "Bolus info not found for time $bolusTime")
        return null
    }

    private fun saveStore() {
        var lastTwoEntries = store
        // Only save last two entries, to avoid too much data in preferences
        if (store.size > 2) {
            lastTwoEntries = ArrayList(store.subList(store.size - 2, store.size))
        }
        val jsonString = Gson().toJson(lastTwoEntries)
        sp.putString(rh.gs(R.string.key_bolus_storage), jsonString)
    }

    private fun loadStore(): ArrayList<DetailedBolusInfo> {
        val jsonString = sp.getString(rh.gs(R.string.key_bolus_storage), "")
        return if (jsonString.isNotEmpty()) {
            val type = object : TypeToken<List<DetailedBolusInfo>>() {}.type
            Gson().fromJson(jsonString, type)
        } else {
            ArrayList()
        }
    }
}