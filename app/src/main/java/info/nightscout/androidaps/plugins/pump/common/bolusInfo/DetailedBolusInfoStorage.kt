package info.nightscout.androidaps.plugins.pump.common.bolusInfo

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData
import info.nightscout.androidaps.utils.T
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.math.abs

object DetailedBolusInfoStorage {
    private val log = LoggerFactory.getLogger(L.PUMP)
    val store = ArrayList<DetailedBolusInfo>()

    @Synchronized
    fun add(detailedBolusInfo: DetailedBolusInfo) {
        log.debug("Stored bolus info: $detailedBolusInfo")
        store.add(detailedBolusInfo)
    }

    @Synchronized
    fun findDetailedBolusInfo(bolusTime: Long, bolus: Double): DetailedBolusInfo? {

        if (MedtronicHistoryData.doubleBolusDebug)
            log.debug("DoubleBolusDebug: findDetailedBolusInfo::bolusTime={}, bolus={}", bolusTime, bolus)

        // Look for info with bolus
        for (i in store.indices) {
            val d = store[i]
            if (L.isEnabled(L.PUMP))
                log.debug("Existing bolus info: " + store[i])
            if (bolusTime > d.date - T.mins(1).msecs() && bolusTime < d.date + T.mins(1).msecs() && abs(store[i].insulin - bolus) < 0.01) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Using & removing bolus info: " + store[i])
                store.removeAt(i)
                if (MedtronicHistoryData.doubleBolusDebug)
                    log.debug("DoubleBolusDebug: findDetailedBolusInfo::selectedBolus[DetailedBolusInfo={}]", d)

                return d
            }
        }
        // If not found use time only
        for (i in store.indices) {
            val d = store[i]
            if (bolusTime > d.date - T.mins(1).msecs() && bolusTime < d.date + T.mins(1).msecs() && bolus <= store[i].insulin + 0.01) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Using & removing bolus info: " + store[i])
                store.removeAt(i)
                if (MedtronicHistoryData.doubleBolusDebug)
                    log.debug("DoubleBolusDebug: findDetailedBolusInfo::selectedBolus[DetailedBolusInfo={}]", d)
                return d
            }
        }
        return null
    }
}