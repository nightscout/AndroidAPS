package info.nightscout.androidaps.plugins.pump.common.bolusInfo

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.T
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DetailedBolusInfoStorage @Inject constructor(
    val aapsLogger: AAPSLogger
){

    val store = ArrayList<DetailedBolusInfo>()

    @Synchronized
    fun add(detailedBolusInfo: DetailedBolusInfo) {
        aapsLogger.debug("Stored bolus info: $detailedBolusInfo")
        store.add(detailedBolusInfo)
    }

    @Synchronized
    fun findDetailedBolusInfo(bolusTime: Long, bolus: Double): DetailedBolusInfo? {
        // Look for info with bolus
        for (i in store.indices) {
            val d = store[i]
                aapsLogger.debug(LTag.PUMP, "Existing bolus info: " + store[i])
            if (bolusTime > d.date - T.mins(1).msecs() && bolusTime < d.date + T.mins(1).msecs() && abs(store[i].insulin - bolus) < 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using & removing bolus info: ${store[i]}")
                store.removeAt(i)
                return d
            }
        }
        // If not found use time only
        for (i in store.indices) {
            val d = store[i]
            if (bolusTime > d.date - T.mins(1).msecs() && bolusTime < d.date + T.mins(1).msecs() && bolus <= store[i].insulin + 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using TIME-ONLY & removing bolus info: ${store[i]}")
                store.removeAt(i)
                return d
            }
        }
        // If not found, use last record if amount is the same
        if (store.size > 0) {
            val d = store[store.size - 1]
            if (abs(d.insulin - bolus) < 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using LAST & removing bolus info: $d")
                store.removeAt(store.size - 1)
                return d
            }
        }
        //Not found
        aapsLogger.debug(LTag.PUMP, "Bolus info not found")
        return null
    }
}