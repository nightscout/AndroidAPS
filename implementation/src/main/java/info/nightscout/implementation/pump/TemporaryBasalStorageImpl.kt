package info.nightscout.implementation.pump

import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@OpenForTesting
@Singleton
class TemporaryBasalStorageImpl @Inject constructor(
    val aapsLogger: AAPSLogger
) : TemporaryBasalStorage {

    val store = ArrayList<PumpSync.PumpState.TemporaryBasal>()

    @Synchronized
    override fun add(temporaryBasal: PumpSync.PumpState.TemporaryBasal) {
        aapsLogger.debug("Stored temporary basal info: $temporaryBasal")
        store.add(temporaryBasal)
    }

    @Synchronized
    override fun findTemporaryBasal(time: Long, rate: Double): PumpSync.PumpState.TemporaryBasal? {
        // Look for info with temporary basal
        for (i in store.indices) {
            val d = store[i]
            //aapsLogger.debug(LTag.PUMP, "Existing temporary basal info: " + store[i])
            if (time > d.timestamp - T.mins(1).msecs() && time < d.timestamp + T.mins(1).msecs() && abs(store[i].rate - rate) < 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using & removing temporary basal info: ${store[i]}")
                store.removeAt(i)
                return d
            }
        }
        // If not found use time only
        for (i in store.indices) {
            val d = store[i]
            if (time > d.timestamp - T.mins(1).msecs() && time < d.timestamp + T.mins(1).msecs() && rate <= store[i].rate + 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using TIME-ONLY & removing temporary basal info: ${store[i]}")
                store.removeAt(i)
                return d
            }
        }
        // If not found, use last record if amount is the same
        if (store.size > 0) {
            val d = store[store.size - 1]
            if (abs(d.rate - rate) < 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using LAST & removing temporary basal info: $d")
                store.removeAt(store.size - 1)
                return d
            }
        }
        //Not found
        //aapsLogger.debug(LTag.PUMP, "Temporary basal info not found")
        return null
    }
}