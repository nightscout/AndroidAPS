package info.nightscout.androidaps.interaction.utils

import info.nightscout.annotations.OpenForTesting
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.EventData
import info.nightscout.rx.weardata.EventData.Companion.deserialize
import info.nightscout.rx.weardata.EventData.SingleBg
import info.nightscout.rx.weardata.EventData.TreatmentData
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by dlvoy on 2019-11-12
 * Refactored by MilosKozak 25/04/2022
 */
@Singleton
@OpenForTesting
open class Persistence @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val sp: SP
) {

    companion object {

        const val BG_DATA_PERSISTENCE_KEY = "bg_data"
        const val GRAPH_DATA_PERSISTENCE_KEY = "graph_data"
        const val TREATMENT_PERSISTENCE_KEY = "treatment_data"
        const val STATUS_PERSISTENCE_KEY = "status_data"

        const val KEY_COMPLICATIONS = "complications"
        const val KEY_LAST_SHOWN_SINCE_VALUE = "lastSince"
        const val KEY_STALE_REPORTED = "staleReported"
        const val KEY_DATA_UPDATED = "data_updated_at"

        const val CUSTOM_WATCHFACE = "custom_watchface"
        const val CUSTOM_DEFAULT_WATCHFACE = "custom_default_watchface"
    }

    fun getString(key: String, defaultValue: String): String {
        return sp.getString(key, defaultValue)
    }

    fun putString(key: String, value: String) {
        sp.putString(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sp.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        sp.putBoolean(key, value)
    }

    fun whenDataUpdated(): Long {
        return sp.getLong(KEY_DATA_UPDATED, 0)
    }

    private fun markDataUpdated() {
        sp.putLong(KEY_DATA_UPDATED, dateUtil.now())
    }

    fun getSetOf(key: String): Set<String> {
        return explodeSet(getString(key, ""), "|")
    }

    fun addToSet(key: String, value: String) {
        val set = explodeSet(getString(key, ""), "|")
        set.add(value)
        putString(key, joinSet(set, "|"))
    }

    fun removeFromSet(key: String, value: String) {
        val set = explodeSet(getString(key, ""), "|")
        set.remove(value)
        putString(key, joinSet(set, "|"))
    }

    fun readSingleBg(): SingleBg? {
        try {
            val s = sp.getStringOrNull(BG_DATA_PERSISTENCE_KEY, null)
            //aapsLogger.debug(LTag.WEAR, "Loaded BG data: $s")
            if (s != null) {
                return deserialize(s) as SingleBg
            }
        } catch (exception: Exception) {
            aapsLogger.error(LTag.WEAR, exception.toString())
        }
        return null
    }

    fun readStatus(): EventData.Status? {
        try {
            val s = sp.getStringOrNull(STATUS_PERSISTENCE_KEY, null)
            //aapsLogger.debug(LTag.WEAR, "Loaded Status data: $s")
            if (s != null) {
                return deserialize(s) as EventData.Status
            }
        } catch (exception: Exception) {
            aapsLogger.error(LTag.WEAR, exception.toString())
        }
        return null
    }

    fun readTreatments(): TreatmentData? {
        try {
            val s = sp.getStringOrNull(TREATMENT_PERSISTENCE_KEY, null)
            //aapsLogger.debug(LTag.WEAR, "Loaded Treatments data: $s")
            if (s != null) {
                return deserialize(s) as TreatmentData
            }
        } catch (exception: Exception) {
            aapsLogger.error(LTag.WEAR, exception.toString())
        }
        return null
    }

    fun readGraphData(): EventData.GraphData? {
        try {
            val s = sp.getStringOrNull(GRAPH_DATA_PERSISTENCE_KEY, null)
            //aapsLogger.debug(LTag.WEAR, "Loaded Graph data: $s")
            if (s != null) {
                return deserialize(s) as EventData.GraphData
            }
        } catch (exception: Exception) {
            aapsLogger.error(LTag.WEAR, exception.toString())
        }
        return null
    }

    fun readCustomWatchface(isDefault: Boolean = false): EventData.ActionSetCustomWatchface? {
        try {
            var s = sp.getStringOrNull(if (isDefault) CUSTOM_DEFAULT_WATCHFACE else CUSTOM_WATCHFACE, null)
            if (s != null) {
                return deserialize(s) as EventData.ActionSetCustomWatchface
            } else {
                s = sp.getStringOrNull(CUSTOM_DEFAULT_WATCHFACE, null)
                if (s != null) {
                    return deserialize(s) as EventData.ActionSetCustomWatchface
                }
            }
        } catch (exception: Exception) {
            aapsLogger.error(LTag.WEAR, exception.toString())
        }
        return null
    }

    fun store(singleBg: SingleBg) {
        putString(BG_DATA_PERSISTENCE_KEY, singleBg.serialize())
        aapsLogger.debug(LTag.WEAR, "Stored BG data: $singleBg")
        markDataUpdated()
    }

    fun store(graphData: EventData.GraphData) {
        putString(GRAPH_DATA_PERSISTENCE_KEY, graphData.serialize())
        aapsLogger.debug(LTag.WEAR, "Stored Graph data: $graphData")
    }

    fun store(treatmentData: TreatmentData) {
        putString(TREATMENT_PERSISTENCE_KEY, treatmentData.serialize())
        aapsLogger.debug(LTag.WEAR, "Stored Treatments data: $treatmentData")
    }

    fun store(status: EventData.Status) {
        putString(STATUS_PERSISTENCE_KEY, status.serialize())
        aapsLogger.debug(LTag.WEAR, "Stored Status data: $status")
    }

    fun store(customWatchface: EventData.ActionSetCustomWatchface, isdefault: Boolean = false) {
        putString(if (isdefault) CUSTOM_DEFAULT_WATCHFACE else CUSTOM_WATCHFACE, customWatchface.serialize())
        aapsLogger.debug(LTag.WEAR, "Stored Custom Watchface ${customWatchface.customWatchfaceData} ${isdefault}: $customWatchface")
    }

    fun setDefaultWatchface() {
        readCustomWatchface(true)?.let { store(it) }
        aapsLogger.debug(LTag.WEAR, "Custom Watchface reset to default")
    }

    fun joinSet(set: Set<String>, separator: String?): String {
        val sb = StringBuilder()
        var i = 0
        for (item in set) {
            val itemToAdd = item.trim { it <= ' ' }
            if (itemToAdd.isNotEmpty()) {
                if (i > 0) sb.append(separator)
                i++
                sb.append(itemToAdd)
            }
        }
        return sb.toString()
    }

    fun explodeSet(joined: String, separator: String): MutableSet<String> {
        // special RegEx literal \\Q starts sequence we escape, \\E ends is
        // we use it to escape separator for use in RegEx
        val items = joined.split(Regex("\\Q$separator\\E")).toTypedArray()
        val set: MutableSet<String> = HashSet()
        for (item in items) {
            val itemToAdd = item.trim { it <= ' ' }
            if (itemToAdd.isNotEmpty()) {
                set.add(itemToAdd)
            }
        }
        return set
    }

    fun turnOff() {
        aapsLogger.debug(LTag.WEAR, "TURNING OFF all active complications")
        putString(KEY_COMPLICATIONS, "")
    }
}
