package app.aaps.implementation.pump

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DetailedBolusInfoStorageImpl @Inject constructor(
    val aapsLogger: AAPSLogger,
    val preferences: Preferences,
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
        preferences.put(StringNonKey.BolusInfoStorage, jsonString)
    }

    private fun loadStore(): ArrayList<DetailedBolusInfo> {
        val jsonString = preferences.get(StringNonKey.BolusInfoStorage)
        return if (jsonString.isNotEmpty()) {
            val type = object : TypeToken<List<DetailedBolusInfo>>() {}.type
            Gson().fromJson(jsonString, type)
        } else {
            ArrayList()
        }
    }
}