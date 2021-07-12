package info.nightscout.androidaps.plugins.pump.common.sync

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.thoughtworks.xstream.XStream
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class is intended for Pump Drivers that use temporaryId and need way to pair records
 */
@Singleton
class PumpSyncStorage @Inject constructor(
    val pumpSync: PumpSync,
    val sp: SP,
    val aapsLogger: AAPSLogger
) {

    val pumpSyncStorageKey: String = "pump_sync_storage_xstream_v2"
    var pumpSyncStorage: MutableMap<String, MutableList<PumpDbEntry>> = mutableMapOf()
    var TBR: String = "TBR"
    var BOLUS: String = "BOLUS"
    var storageInitialized: Boolean = false
    var gson: Gson = GsonBuilder().create()
    var xstream: XStream = XStream()

    init {
        initStorage()
        cleanOldStorage();
    }

    fun initStorage() {
        if (storageInitialized)
            return

        var loaded = false

        if (sp.contains(pumpSyncStorageKey)) {
            val jsonData: String = sp.getString(pumpSyncStorageKey, "");

            if (!jsonData.isBlank()) {
                pumpSyncStorage = xstream.fromXML(jsonData, MutableMap::class.java) as MutableMap<String, MutableList<PumpDbEntry>>

                aapsLogger.debug(LTag.PUMP, String.format("Loading Pump Sync Storage: boluses=%d, tbrs=%d.", pumpSyncStorage[BOLUS]!!.size, pumpSyncStorage[TBR]!!.size))
                aapsLogger.debug(LTag.PUMP, "DD: PumpSyncStorage=$pumpSyncStorage")

                loaded = true
            }
        }

        if (!loaded) {
            pumpSyncStorage[BOLUS] = mutableListOf()
            pumpSyncStorage[TBR] = mutableListOf()
        }
    }

    fun saveStorage() {
        if (!isStorageEmpty()) {
            sp.putString(pumpSyncStorageKey, xstream.toXML(pumpSyncStorage))
            aapsLogger.debug(String.format("Saving Pump Sync Storage: boluses=%d, tbrs=%d.", pumpSyncStorage[BOLUS]!!.size, pumpSyncStorage[TBR]!!.size))
        }
    }

    fun cleanOldStorage(): Unit {
        val oldSpKeys = setOf("pump_sync_storage", "pump_sync_storage_xstream")

        for (oldSpKey in oldSpKeys) {
            if (sp.contains(oldSpKey))
                sp.remove(oldSpKey)
        }
    }

    fun isStorageEmpty(): Boolean {
        return pumpSyncStorage[BOLUS]!!.isEmpty() && pumpSyncStorage[TBR]!!.isEmpty()
    }

    fun getBoluses(): MutableList<PumpDbEntry> {
        return pumpSyncStorage[BOLUS]!!;
    }

    fun getTBRs(): MutableList<PumpDbEntry> {
        return pumpSyncStorage[TBR]!!;
    }

    fun addBolusWithTempId(detailedBolusInfo: DetailedBolusInfo, writeToInternalHistory: Boolean, creator: PumpSyncEntriesCreator): Boolean {
        val temporaryId = creator.generateTempId(detailedBolusInfo.timestamp)
        val result = pumpSync.addBolusWithTempId(
            detailedBolusInfo.timestamp,
            detailedBolusInfo.insulin,
            temporaryId,
            detailedBolusInfo.bolusType,
            creator.model(),
            creator.serialNumber())

        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "addBolusWithTempId [date=%d, temporaryId=%d, insulin=%.2f, type=%s, pumpSerial=%s] - Result: %b",
            detailedBolusInfo.timestamp, temporaryId, detailedBolusInfo.insulin, detailedBolusInfo.bolusType,
            creator.serialNumber(), result))

        if (detailedBolusInfo.carbs > 0.0) {
            addCarbs(PumpDbEntryCarbs(detailedBolusInfo, creator))
        }

        if (result && writeToInternalHistory) {
            val innerList: MutableList<PumpDbEntry> = pumpSyncStorage[BOLUS]!!

            val dbEntry = PumpDbEntry(temporaryId, detailedBolusInfo.timestamp, creator.model(), creator.serialNumber(), detailedBolusInfo)

            aapsLogger.debug("PumpDbEntry: $dbEntry")

            innerList.add(dbEntry)
            pumpSyncStorage[BOLUS] = innerList
            saveStorage()
        }
        return result
    }

    fun addCarbs(carbsDto: PumpDbEntryCarbs) {
        val result = pumpSync.syncCarbsWithTimestamp(
            carbsDto.date,
            carbsDto.carbs,
            null,
            carbsDto.pumpType,
            carbsDto.serialNumber)

        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "syncCarbsWithTimestamp [date=%d, carbs=%.2f, pumpSerial=%s] - Result: %b",
            carbsDto.date, carbsDto.carbs, carbsDto.serialNumber, result))
    }

    fun addTemporaryBasalRateWithTempId(temporaryBasal: PumpDbEntryTBR, writeToInternalHistory: Boolean, creator: PumpSyncEntriesCreator): Boolean {
        val timenow: Long = System.currentTimeMillis()
        val temporaryId = creator.generateTempId(timenow)

        val response = pumpSync.addTemporaryBasalWithTempId(
            timenow,
            temporaryBasal.rate,
            (temporaryBasal.durationInSeconds * 1000L),
            temporaryBasal.isAbsolute,
            temporaryId,
            temporaryBasal.tbrType,
            creator.model(),
            creator.serialNumber())

        if (response && writeToInternalHistory) {
            val innerList: MutableList<PumpDbEntry> = pumpSyncStorage[TBR]!!

            innerList.add(PumpDbEntry(temporaryId, timenow, creator.model(), creator.serialNumber(), null, temporaryBasal))
            pumpSyncStorage[BOLUS] = innerList
            saveStorage()
        }

        return response;
    }

    fun removeBolusWithTemporaryId(temporaryId: Long) {
        val bolusList = removeTemporaryId(temporaryId, pumpSyncStorage[BOLUS]!!)
        pumpSyncStorage[BOLUS] = bolusList
        saveStorage()
    }

    fun removeTemporaryBasalWithTemporaryId(temporaryId: Long) {
        val tbrList = removeTemporaryId(temporaryId, pumpSyncStorage[TBR]!!)
        pumpSyncStorage[TBR] = tbrList
        saveStorage()
    }

    private fun removeTemporaryId(temporaryId: Long, list: MutableList<PumpDbEntry>): MutableList<PumpDbEntry> {
        var dbEntry: PumpDbEntry? = null

        for (pumpDbEntry in list) {
            if (pumpDbEntry.temporaryId == temporaryId) {
                dbEntry = pumpDbEntry
            }
        }

        if (dbEntry != null) {
            list.remove(dbEntry)
        }

        return list
    }

}