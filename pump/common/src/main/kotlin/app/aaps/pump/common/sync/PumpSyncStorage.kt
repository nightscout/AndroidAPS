package app.aaps.pump.common.sync

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * This class is intended for Pump Drivers that use temporaryId and need way to pair records
 *
 * The preference store is the only copy. There is no in-memory list, and that is deliberate: the
 * previous version loaded once behind a `storageInitialized` flag and then held the entries for the
 * life of the process, so it never saw an outside change to its own keys - not from a settings
 * import, not from anything - and the next save wrote the stale copy straight back over it.
 *
 * Reading on every call costs a small JSON parse. Measured against how this is actually used that is
 * nothing: [getBoluses] and [getTBRs] are read once per Medtronic history pass, not in a loop, and the
 * list only ever holds what is in flight to the pump.
 */
@SingleIn(AppScope::class)
@Inject
class PumpSyncStorage(
    val pumpSync: PumpSync,
    val preferences: Preferences,
    val aapsLogger: AAPSLogger
) {

    /**
     * Guards read-modify-write. The store makes each write atomic on its own, which is not the same
     * thing: two threads adding a bolus at once would both read the same list, and one of the two
     * would be written over. The old version had no lock at all on the mutation path.
     */
    private val lock = AapsLock()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * A copy, not the stored list.
     *
     * Callers do mutate what they get back - `MedtronicHistoryData` removes the entry it has just
     * matched - and on the old shared list that mutation silently skipped the store. Now the removal
     * they make locally affects this pass only, and the durable one goes through
     * [removeBolusWithTemporaryId] as it already did.
     */
    fun getBoluses(): MutableList<PumpDbEntryBolus> = lock.withLock { readBoluses() }

    fun getTBRs(): MutableList<PumpDbEntryTBR> = lock.withLock { readTbrs() }

    private fun readBoluses(): MutableList<PumpDbEntryBolus> =
        decode(StringNonKey.PumpCommonBolusStorage) { text ->
            json.decodeFromString<List<StoredBolus>>(text).map { it.toEntry() }
        }

    private fun readTbrs(): MutableList<PumpDbEntryTBR> =
        decode(StringNonKey.PumpCommonTbrStorage) { text ->
            json.decodeFromString<List<StoredTbr>>(text).map { it.toEntry() }
        }

    /**
     * Never throws. Unreadable stored data costs the pairing of whatever was already in flight, which
     * is what a fresh install has anyway; throwing would take the pump driver down with it.
     */
    private fun <T> decode(key: StringNonKey, parse: (String) -> List<T>): MutableList<T> {
        val text = preferences.getIfExists(key)
        if (text.isNullOrBlank()) return mutableListOf()
        return runCatching { parse(text).toMutableList() }
            .getOrElse { error ->
                aapsLogger.error(LTag.PUMP, "Unreadable ${key.key}, starting empty: $error")
                mutableListOf()
            }
    }

    private fun writeBoluses(entries: List<PumpDbEntryBolus>) {
        if (entries.isEmpty()) preferences.remove(StringNonKey.PumpCommonBolusStorage)
        else preferences.put(StringNonKey.PumpCommonBolusStorage, json.encodeToString(entries.map { it.toStored() }))
        aapsLogger.debug(LTag.PUMP, "Pump sync storage: boluses=${entries.size}")
    }

    private fun writeTbrs(entries: List<PumpDbEntryTBR>) {
        if (entries.isEmpty()) preferences.remove(StringNonKey.PumpCommonTbrStorage)
        else preferences.put(StringNonKey.PumpCommonTbrStorage, json.encodeToString(entries.map { it.toStored() }))
        aapsLogger.debug(LTag.PUMP, "Pump sync storage: tbrs=${entries.size}")
    }

    fun addBolusWithTempId(detailedBolusInfo: DetailedBolusInfo, writeToInternalHistory: Boolean, creator: PumpSyncEntriesCreator): Boolean {
        val temporaryId = creator.generateTempId(detailedBolusInfo.timestamp)
        val result = runBlocking {
            pumpSync.addBolusWithTempId(
                detailedBolusInfo.timestamp,
                amount = PumpInsulin(detailedBolusInfo.insulin),
                temporaryId,
                detailedBolusInfo.bolusType,
                creator.model(),
                creator.serialNumber()
            )
        }

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

            lock.withLock { writeBoluses(readBoluses().apply { add(dbEntry) }) }
        }
        return result
    }

    fun addCarbs(carbsDto: PumpDbEntryCarbs) {
        val result = runBlocking {
            pumpSync.syncCarbsWithTimestamp(
                carbsDto.date,
                carbsDto.carbs,
                null,
                carbsDto.pumpType,
                carbsDto.serialNumber
            )
        }

        aapsLogger.debug(
            LTag.PUMP, "syncCarbsWithTimestamp [date=${carbsDto.date}, " +
                "carbs=${carbsDto.carbs}, pumpSerial=${carbsDto.serialNumber}] - Result: $result"
        )
    }

    fun addTemporaryBasalRateWithTempId(temporaryBasal: PumpDbEntryTBR, writeToInternalHistory: Boolean, creator: PumpSyncEntriesCreator): Boolean {
        val timeNow: Long = System.currentTimeMillis()
        val temporaryId = creator.generateTempId(timeNow)

        val response = runBlocking {
            pumpSync.addTemporaryBasalWithTempId(
                timeNow,
                PumpRate(temporaryBasal.rate),
                (temporaryBasal.durationInSeconds * 1000L),
                temporaryBasal.isAbsolute,
                temporaryId,
                temporaryBasal.tbrType,
                creator.model(),
                creator.serialNumber()
            )
        }

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

            lock.withLock { writeTbrs(readTbrs().apply { add(dbEntry) }) }
        }

        return response
    }

    fun removeBolusWithTemporaryId(temporaryId: Long) = lock.withLock {
        val remaining = readBoluses().filterNot { it.temporaryId == temporaryId }
        writeBoluses(remaining)
    }

    fun removeTemporaryBasalWithTemporaryId(temporaryId: Long) = lock.withLock {
        val remaining = readTbrs().filterNot { it.temporaryId == temporaryId }
        writeTbrs(remaining)
    }
}

/**
 * The stored shape, separate from [PumpDbEntryBolus] itself.
 *
 * Same reason `DetailedBolusInfoStorageImpl` keeps a `StoredBolusInfo`: kotlinx only serializes types
 * it owns, and the three enums here belong to other modules. Holding them by `name` also means a value
 * that no longer exists is one unreadable entry rather than a parse failure for the whole list.
 */
@Serializable
private data class StoredBolus(
    val temporaryId: Long,
    val date: Long,
    val pumpType: String,
    val serialNumber: String,
    val pumpId: Long? = null,
    val insulin: Double,
    val carbs: Double,
    val bolusType: String
)

@Serializable
private data class StoredTbr(
    val temporaryId: Long,
    val date: Long,
    val pumpType: String,
    val serialNumber: String,
    val pumpId: Long? = null,
    val rate: Double,
    val isAbsolute: Boolean,
    val durationInSeconds: Int,
    val tbrType: String
)

private fun PumpDbEntryBolus.toStored() = StoredBolus(
    temporaryId = temporaryId, date = date, pumpType = pumpType.name, serialNumber = serialNumber,
    pumpId = pumpId, insulin = insulin, carbs = carbs, bolusType = bolusType.name
)

private fun StoredBolus.toEntry() = PumpDbEntryBolus(
    temporaryId = temporaryId, date = date, pumpType = PumpType.valueOf(pumpType), serialNumber = serialNumber,
    pumpId = pumpId, insulin = insulin, carbs = carbs, bolusType = BS.Type.valueOf(bolusType)
)

private fun PumpDbEntryTBR.toStored() = StoredTbr(
    temporaryId = temporaryId, date = date, pumpType = pumpType.name, serialNumber = serialNumber,
    pumpId = pumpId, rate = rate, isAbsolute = isAbsolute, durationInSeconds = durationInSeconds,
    tbrType = tbrType.name
)

private fun StoredTbr.toEntry() = PumpDbEntryTBR(
    temporaryId = temporaryId, date = date, pumpType = PumpType.valueOf(pumpType), serialNumber = serialNumber,
    pumpId = pumpId, rate = rate, isAbsolute = isAbsolute, durationInSeconds = durationInSeconds,
    tbrType = PumpSync.TemporaryBasalType.valueOf(tbrType)
)
