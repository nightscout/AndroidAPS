package app.aaps.implementation.pump

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.abs

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DetailedBolusInfoStorageImpl @Inject constructor(
    val aapsLogger: AAPSLogger,
    val preferences: Preferences
) : DetailedBolusInfoStorage {

    val store = loadStore()

    private val lock = AapsLock()

    fun DetailedBolusInfo.toJsonString(): String = json.encodeToString(toStored())

    override fun add(detailedBolusInfo: DetailedBolusInfo) = lock.withLock {
        aapsLogger.debug("Stored bolus info: ${detailedBolusInfo.toJsonString()}")
        store.add(detailedBolusInfo)
        saveStore()
    }

    override fun findDetailedBolusInfo(bolusTime: Long, bolus: Double): DetailedBolusInfo? = lock.withLock {
        // Look for info with bolus
        for (i in store.indices) {
            val d = store[i]
            if (bolusTime > d.timestamp - T.mins(1).msecs() && bolusTime < d.timestamp + T.mins(1).msecs() && abs(store[i].insulin - bolus) < 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using & removing bolus info for time $bolusTime: ${store[i]}")
                store.removeAt(i)
                saveStore()
                return@withLock d
            }
        }
        // If not found use time only
        for (i in store.indices) {
            val d = store[i]
            if (bolusTime > d.timestamp - T.mins(1).msecs() && bolusTime < d.timestamp + T.mins(1).msecs() && bolus <= store[i].insulin + 0.01) {
                aapsLogger.debug(LTag.PUMP, "Using TIME-ONLY & removing bolus info for time $bolusTime: ${store[i]}")
                store.removeAt(i)
                saveStore()
                return@withLock d
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
        null
    }

    private fun saveStore() {
        var lastTwoEntries = store
        // Only save last two entries, to avoid too much data in preferences
        if (store.size > 2) {
            lastTwoEntries = ArrayList(store.subList(store.size - 2, store.size))
        }
        val jsonString = json.encodeToString(lastTwoEntries.map { it.toStored() })
        preferences.put(StringNonKey.BolusInfoStorage, jsonString)
    }

    /**
     * Never throws. This runs from a property initializer, so it happens while the object graph is
     * being built - an exception here does not lose a bolus record, it stops the app from starting.
     * A half written or otherwise unreadable preference is recoverable: the store is a short lived
     * cache of at most two pending boluses, so starting empty costs the matching of a bolus that is
     * already in flight, which is what happens on any fresh install anyway.
     */
    private fun loadStore(): ArrayList<DetailedBolusInfo> {
        val jsonString = preferences.get(StringNonKey.BolusInfoStorage)
        if (jsonString.isEmpty()) return ArrayList()
        return runCatching {
            ArrayList(json.decodeFromString<List<StoredBolusInfo>>(jsonString).map { it.toDetailedBolusInfo() })
        }.getOrElse { error ->
            aapsLogger.error(LTag.PUMP, "Unreadable bolus info storage, starting empty: $error")
            ArrayList()
        }
    }

    private companion object {

        /**
         * `ignoreUnknownKeys` is what makes a preference written by the previous Gson version readable:
         * Gson serialized the whole object, including `id`, which is not persisted any more. Every field
         * has a default for the same reason in reverse - Gson omits nulls, so an absent key must not fail.
         */
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * What actually gets persisted.
 *
 * A separate type rather than making [DetailedBolusInfo] `@Serializable`: kotlinx only serializes
 * constructor properties, and that class keeps all of its state as body `var`s so callers can write
 * `DetailedBolusInfo().apply { ... }` - which 69 call sites do. Moving them into the constructor to
 * suit the storage would be the tail wagging the dog, and this keeps the persisted shape explicit and
 * free to differ from the domain object.
 *
 * `id` is deliberately not carried. It is a per-instance value from the clock, and [DetailedBolusInfo.copy]
 * already does not preserve it, so a reloaded record getting a fresh one matches existing behaviour.
 */
@Serializable
private data class StoredBolusInfo(
    val timestamp: Long = 0L,
    val insulin: Double = 0.0,
    val carbs: Double = 0.0,
    val lastKnownBolusTime: Long = 0L,
    val deliverAtTheLatest: Long = 0L,
    val bolusCalculatorResult: BCR? = null,
    val eventType: TE.Type = TE.Type.MEAL_BOLUS,
    val notes: String? = null,
    val mgdlGlucose: Double? = null,
    val glucoseType: TE.MeterType? = null,
    val bolusType: BS.Type = BS.Type.NORMAL,
    val carbsDuration: Long = 0L,
    val pumpType: PumpType? = null,
    val pumpSerial: String? = null,
    val bolusPumpId: Long? = null,
    val bolusTimestamp: Long? = null,
    val carbsTimestamp: Long? = null
)

private fun DetailedBolusInfo.toStored(): StoredBolusInfo = StoredBolusInfo(
    timestamp = timestamp,
    insulin = insulin,
    carbs = carbs,
    lastKnownBolusTime = lastKnownBolusTime,
    deliverAtTheLatest = deliverAtTheLatest,
    bolusCalculatorResult = bolusCalculatorResult,
    eventType = eventType,
    notes = notes,
    mgdlGlucose = mgdlGlucose,
    glucoseType = glucoseType,
    bolusType = bolusType,
    carbsDuration = carbsDuration,
    pumpType = pumpType,
    pumpSerial = pumpSerial,
    bolusPumpId = bolusPumpId,
    bolusTimestamp = bolusTimestamp,
    carbsTimestamp = carbsTimestamp
)

private fun StoredBolusInfo.toDetailedBolusInfo(): DetailedBolusInfo = DetailedBolusInfo().also {
    it.timestamp = timestamp
    it.insulin = insulin
    it.carbs = carbs
    it.lastKnownBolusTime = lastKnownBolusTime
    it.deliverAtTheLatest = deliverAtTheLatest
    it.bolusCalculatorResult = bolusCalculatorResult
    it.eventType = eventType
    it.notes = notes
    it.mgdlGlucose = mgdlGlucose
    it.glucoseType = glucoseType
    it.bolusType = bolusType
    it.carbsDuration = carbsDuration
    it.pumpType = pumpType
    it.pumpSerial = pumpSerial
    it.bolusPumpId = bolusPumpId
    it.bolusTimestamp = bolusTimestamp
    it.carbsTimestamp = carbsTimestamp
}
