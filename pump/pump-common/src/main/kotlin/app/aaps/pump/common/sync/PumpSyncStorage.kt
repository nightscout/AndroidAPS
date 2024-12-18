package app.aaps.pump.common.sync

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.sharedPreferences.SP
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.security.AnyTypePermission
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

    companion object {

        const val pumpSyncStorageBolusKey: String = "pump_sync_storage_bolus"
        const val pumpSyncStorageTBRKey: String = "pump_sync_storage_tbr"
    }

    var pumpSyncStorageBolus: MutableList<PumpDbEntryBolus> = mutableListOf()
    var pumpSyncStorageTBR: MutableList<PumpDbEntryTBR> = mutableListOf()

    private var storageInitialized: Boolean = false
    private var xstream: XStream = XStream()

    init {
        initStorage()
        cleanOldStorage()
    }

    fun initStorage() {
        if (storageInitialized)
            return

        xstream.addPermission(AnyTypePermission.ANY)

        if (sp.contains(pumpSyncStorageBolusKey)) {
            val jsonData: String = sp.getString(pumpSyncStorageBolusKey, "")

            if (jsonData.isNotBlank()) {
                @Suppress("UNCHECKED_CAST")
                pumpSyncStorageBolus = try {
                    xstream.fromXML(jsonData, MutableList::class.java) as MutableList<PumpDbEntryBolus>
                } catch (e: Exception) {
                    mutableListOf()
                }

                aapsLogger.debug(LTag.PUMP, "Loading Pump Sync Storage Bolus: boluses=${pumpSyncStorageBolus.size}")
                aapsLogger.debug(LTag.PUMP, "DD: PumpSyncStorageBolus=$pumpSyncStorageBolus")
            }
        }

        if (sp.contains(pumpSyncStorageTBRKey)) {
            val jsonData: String = sp.getString(pumpSyncStorageTBRKey, "")

            if (jsonData.isNotBlank()) {
                @Suppress("UNCHECKED_CAST")
                pumpSyncStorageTBR = try {
                    xstream.fromXML(jsonData, MutableList::class.java) as MutableList<PumpDbEntryTBR>
                } catch (e: Exception) {
                    mutableListOf()
                }

                aapsLogger.debug(LTag.PUMP, "Loading Pump Sync Storage: tbrs=${pumpSyncStorageTBR.size}.")
                aapsLogger.debug(LTag.PUMP, "DD: PumpSyncStorageTBR=$pumpSyncStorageTBR")
            }
        }
        storageInitialized = true
    }

    fun saveStorageBolus() {
        if (pumpSyncStorageBolus.isNotEmpty()) {
            sp.putString(pumpSyncStorageBolusKey, xstream.toXML(pumpSyncStorageBolus))
            aapsLogger.debug(LTag.PUMP, "Saving Pump Sync Storage: boluses=${pumpSyncStorageBolus.size}")
        } else {
            if (sp.contains(pumpSyncStorageBolusKey))
                sp.remove(pumpSyncStorageBolusKey)
        }
    }

    fun saveStorageTBR() {
        if (pumpSyncStorageTBR.isNotEmpty()) {
            sp.putString(pumpSyncStorageTBRKey, xstream.toXML(pumpSyncStorageTBR))
            aapsLogger.debug(LTag.PUMP, "Saving Pump Sync Storage: tbr=${pumpSyncStorageTBR.size}")
        } else {
            if (sp.contains(pumpSyncStorageTBRKey))
                sp.remove(pumpSyncStorageTBRKey)
        }
    }

    private fun cleanOldStorage() {
        val oldSpKeys = setOf("pump_sync_storage", "pump_sync_storage_xstream", "pump_sync_storage_xstream_v2")

        for (oldSpKey in oldSpKeys) {
            if (sp.contains(oldSpKey))
                sp.remove(oldSpKey)
        }
    }

    fun getBoluses(): MutableList<PumpDbEntryBolus> {
        return pumpSyncStorageBolus
    }

    fun getTBRs(): MutableList<PumpDbEntryTBR> {
        return pumpSyncStorageTBR
    }

    fun addBolusWithTempId(detailedBolusInfo: DetailedBolusInfo, writeToInternalHistory: Boolean, creator: PumpSyncEntriesCreator): Boolean {
        val temporaryId = creator.generateTempId(detailedBolusInfo.timestamp)
        val result = pumpSync.addBolusWithTempId(
            detailedBolusInfo.timestamp,
            detailedBolusInfo.insulin,
            temporaryId,
            detailedBolusInfo.bolusType,
            creator.model(),
            creator.serialNumber()
        )

        aapsLogger.debug(
            LTag.PUMP, "addBolusWithTempId [date=${detailedBolusInfo.timestamp}, temporaryId=$temporaryId, " +
                "insulin=${detailedBolusInfo.insulin}, type=${detailedBolusInfo.bolusType}, pumpSerial=${creator.serialNumber()}] - " +
                "Result: $result"
        )

        if (detailedBolusInfo.carbs > 0.0) {
            addCarbs(PumpDbEntryCarbs(detailedBolusInfo, creator))
        }

        if (result && writeToInternalHistory) {
            val dbEntry = PumpDbEntryBolus(
                temporaryId = temporaryId,
                date = detailedBolusInfo.timestamp,
                pumpType = creator.model(),
                serialNumber = creator.serialNumber(),
                detailedBolusInfo = detailedBolusInfo
            )

            aapsLogger.debug("PumpDbEntryBolus: $dbEntry")

            pumpSyncStorageBolus.add(dbEntry)
            saveStorageBolus()
        }
        return result
    }

    fun addCarbs(carbsDto: PumpDbEntryCarbs) {
        val result = pumpSync.syncCarbsWithTimestamp(
            carbsDto.date,
            carbsDto.carbs,
            null,
            carbsDto.pumpType,
            carbsDto.serialNumber
        )

        aapsLogger.debug(
            LTag.PUMP, "syncCarbsWithTimestamp [date=${carbsDto.date}, " +
                "carbs=${carbsDto.carbs}, pumpSerial=${carbsDto.serialNumber}] - Result: $result"
        )
    }

    fun addTemporaryBasalRateWithTempId(temporaryBasal: PumpDbEntryTBR, writeToInternalHistory: Boolean, creator: PumpSyncEntriesCreator): Boolean {
        val timeNow: Long = System.currentTimeMillis()
        val temporaryId = creator.generateTempId(timeNow)

        val response = pumpSync.addTemporaryBasalWithTempId(
            timeNow,
            temporaryBasal.rate,
            (temporaryBasal.durationInSeconds * 1000L),
            temporaryBasal.isAbsolute,
            temporaryId,
            temporaryBasal.tbrType,
            creator.model(),
            creator.serialNumber()
        )

        if (response && writeToInternalHistory) {
            val dbEntry = PumpDbEntryTBR(
                temporaryId = temporaryId,
                date = timeNow,
                pumpType = creator.model(),
                serialNumber = creator.serialNumber(),
                entry = temporaryBasal,
                pumpId = null
            )

            aapsLogger.debug("PumpDbEntryTBR: $dbEntry")

            pumpSyncStorageTBR.add(dbEntry)
            saveStorageTBR()
        }

        return response
    }

    fun removeBolusWithTemporaryId(temporaryId: Long) {
        var dbEntry: PumpDbEntryBolus? = null

        for (pumpDbEntry in pumpSyncStorageBolus) {
            if (pumpDbEntry.temporaryId == temporaryId) {
                dbEntry = pumpDbEntry
            }
        }

        if (dbEntry != null) {
            pumpSyncStorageBolus.remove(dbEntry)
        }

        saveStorageBolus()
    }

    fun removeTemporaryBasalWithTemporaryId(temporaryId: Long) {
        var dbEntry: PumpDbEntryTBR? = null

        for (pumpDbEntry in pumpSyncStorageTBR) {
            if (pumpDbEntry.temporaryId == temporaryId) {
                dbEntry = pumpDbEntry
            }
        }

        if (dbEntry != null) {
            pumpSyncStorageTBR.remove(dbEntry)
        }

        saveStorageTBR()
    }

}