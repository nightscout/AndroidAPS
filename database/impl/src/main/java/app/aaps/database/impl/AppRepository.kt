package app.aaps.database.impl

import androidx.annotation.OpenForTesting
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.data.NewEntries
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.transactions.Transaction
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@OpenForTesting
@Singleton
class AppRepository @Inject internal constructor(
    internal val database: AppDatabase
) {

    private val changeSubject = PublishSubject.create<List<DBEntry>>()

    fun changeObservable(): Observable<List<DBEntry>> = changeSubject.subscribeOn(Schedulers.io())

    /**
     * Executes a transaction ignoring its result
     * Runs on IO scheduler
     */
    fun <T> runTransaction(transaction: Transaction<T>): Completable {
        val changes = mutableListOf<DBEntry>()
        return Completable.fromCallable {
            database.runInTransaction {
                transaction.database = DelegatedAppDatabase(changes, database)
                transaction.run()
            }
        }.subscribeOn(Schedulers.io()).doOnComplete {
            changeSubject.onNext(changes)
        }
    }

    /**
     * Executes a transaction and returns its result
     * Runs on IO scheduler
     */
    fun <T : Any> runTransactionForResult(transaction: Transaction<T>): Single<T> {
        val changes = mutableListOf<DBEntry>()
        return Single.fromCallable {
            database.runInTransaction(Callable {
                transaction.database = DelegatedAppDatabase(changes, database)
                transaction.run()
            })
        }.subscribeOn(Schedulers.io()).doOnSuccess {
            changeSubject.onNext(changes)
        }
    }

    fun clearDatabases() = database.clearAllTables()

    fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String {
        val than = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays)
        val removed = mutableListOf<Pair<String, Int>>()
        removed.add(Pair("GlucoseValue", database.glucoseValueDao.deleteOlderThan(than)))
        removed.add(Pair("TherapyEvent", database.therapyEventDao.deleteOlderThan(than)))
        removed.add(Pair("TemporaryBasal", database.temporaryBasalDao.deleteOlderThan(than)))
        removed.add(Pair("ExtendedBolus", database.extendedBolusDao.deleteOlderThan(than)))
        removed.add(Pair("Bolus", database.bolusDao.deleteOlderThan(than)))
        removed.add(Pair("MultiWaveBolus", database.multiwaveBolusLinkDao.deleteOlderThan(than)))
        removed.add(Pair("TotalDailyDose", database.totalDailyDoseDao.deleteOlderThan(than)))
        removed.add(Pair("Carbs", database.carbsDao.deleteOlderThan(than)))
        removed.add(Pair("TemporaryTarget", database.temporaryTargetDao.deleteOlderThan(than)))
        removed.add(Pair("ApsResultLink", database.apsResultLinkDao.deleteOlderThan(than)))
        removed.add(Pair("BolusCalculatorResult", database.bolusCalculatorResultDao.deleteOlderThan(than)))
        // keep at least one EPS
        if (database.effectiveProfileSwitchDao.getEffectiveProfileSwitchDataFromTime(than + 1).blockingGet().isNotEmpty())
            removed.add(Pair("EffectiveProfileSwitch", database.effectiveProfileSwitchDao.deleteOlderThan(than)))
        removed.add(Pair("ProfileSwitch", database.profileSwitchDao.deleteOlderThan(than)))
        removed.add(Pair("ApsResult", database.apsResultDao.deleteOlderThan(than)))
        // keep version history database.versionChangeDao.deleteOlderThan(than)
        removed.add(Pair("UserEntry", database.userEntryDao.deleteOlderThan(than)))
        removed.add(Pair("PreferenceChange", database.preferenceChangeDao.deleteOlderThan(than)))
        // keep foods database.foodDao.deleteOlderThan(than)
        removed.add(Pair("DeviceStatus", database.deviceStatusDao.deleteOlderThan(than)))
        removed.add(Pair("OfflineEvent", database.offlineEventDao.deleteOlderThan(than)))
        removed.add(Pair("HeartRate", database.heartRateDao.deleteOlderThan(than)))

        if (deleteTrackedChanges) {
            removed.add(Pair("CHANGES GlucoseValue", database.glucoseValueDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TherapyEvent", database.therapyEventDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TemporaryBasal", database.temporaryBasalDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES Bolus", database.bolusDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES ExtendedBolus", database.extendedBolusDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES MultiWaveBolus", database.multiwaveBolusLinkDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TotalDailyDose", database.totalDailyDoseDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES Carbs", database.carbsDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TemporaryTarget", database.temporaryTargetDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES ApsResultLink", database.apsResultLinkDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES BolusCalculatorResult", database.bolusCalculatorResultDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES EffectiveProfileSwitch", database.effectiveProfileSwitchDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES ProfileSwitch", database.profileSwitchDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES ApsResult", database.apsResultDao.deleteTrackedChanges()))
            // keep food database.foodDao.deleteHistory()
            removed.add(Pair("CHANGES OfflineEvent", database.offlineEventDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES HeartRate", database.heartRateDao.deleteTrackedChanges()))
        }
        val ret = StringBuilder()
        removed
            .filter { it.second > 0 }
            .map { ret.append(it.first + " " + it.second + "<br>") }
        return ret.toString()
    }

    fun clearCachedTddData(from: Long) {
        database.totalDailyDoseDao.deleteNewerThan(from, InterfaceIDs.PumpType.CACHE)
    }

    //BG READINGS -- only valid records
    fun compatGetBgReadingsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<GlucoseValue>> =
        database.glucoseValueDao.compatGetBgReadingsDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun compatGetBgReadingsDataFromTime(start: Long, end: Long, ascending: Boolean): Single<List<GlucoseValue>> =
        database.glucoseValueDao.compatGetBgReadingsDataFromTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    //BG READINGS -- including invalid/history records
    fun findBgReadingByNSId(nsId: String): GlucoseValue? =
        database.glucoseValueDao.findByNSId(nsId)

    @Suppress("unused")
    fun getModifiedBgReadingsDataFromId(lastId: Long): Single<List<GlucoseValue>> =
        database.glucoseValueDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getLastGlucoseValueId(): Long? =
        database.glucoseValueDao.getLastId()

    fun getLastGlucoseValueWrapped(): Single<ValueWrapper<GlucoseValue>> =
        database.glucoseValueDao.getLast()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementGlucoseValue(id: Long): Maybe<Pair<GlucoseValue, GlucoseValue>> =
        database.glucoseValueDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.glucoseValueDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    // TEMP TARGETS
    fun findTemporaryTargetByNSId(nsId: String): TemporaryTarget? =
        database.temporaryTargetDao.findByNSId(nsId)

    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementTemporaryTarget(id: Long): Maybe<Pair<TemporaryTarget, TemporaryTarget>> =
        database.temporaryTargetDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.temporaryTargetDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTemporaryTargetActiveAt(timestamp: Long): Single<ValueWrapper<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun deleteAllTempTargetEntries() =
        database.temporaryTargetDao.deleteAllEntries()

    fun getLastTempTargetId(): Long? =
        database.temporaryTargetDao.getLastId()

    // USER ENTRY
    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UserEntry>> =
        database.userEntryDao.getUserEntryDataFromTime(timestamp)
            .subscribeOn(Schedulers.io())

    fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UserEntry>> =
        database.userEntryDao.getUserEntryFilteredDataFromTime(UserEntry.Sources.Loop, timestamp)
            .subscribeOn(Schedulers.io())

    fun insert(word: UserEntry) {
        database.userEntryDao.insert(word)
        changeSubject.onNext(mutableListOf(word)) // Not TraceableDao
    }

    // PROFILE SWITCH

    fun findProfileSwitchByNSId(nsId: String): ProfileSwitch? =
        database.profileSwitchDao.findByNSId(nsId)

    fun getNextSyncElementProfileSwitch(id: Long): Maybe<Pair<ProfileSwitch, ProfileSwitch>> =
        database.profileSwitchDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.profileSwitchDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    @Suppress("unused")
    fun getModifiedProfileSwitchDataFromId(lastId: Long): Single<List<ProfileSwitch>> =
        database.profileSwitchDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getActiveProfileSwitch(timestamp: Long): ProfileSwitch? {
        val tps = database.profileSwitchDao.getTemporaryProfileSwitchActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .blockingGet()
        val ps = database.profileSwitchDao.getPermanentProfileSwitchActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .blockingGet()
        if (tps != null && ps != null)
            return if (ps.timestamp > tps.timestamp) ps else tps
        if (ps == null) return tps
        return ps // if (tps == null)
    }

    fun getPermanentProfileSwitch(timestamp: Long): ProfileSwitch? =
        database.profileSwitchDao.getPermanentProfileSwitchActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .blockingGet()

    fun getAllProfileSwitches(): Single<List<ProfileSwitch>> =
        database.profileSwitchDao.getAllProfileSwitches()
            .subscribeOn(Schedulers.io())

    fun deleteAllProfileSwitches() =
        database.profileSwitchDao.deleteAllEntries()

    fun getProfileSwitchDataFromTime(timestamp: Long, ascending: Boolean): Single<List<ProfileSwitch>> =
        database.profileSwitchDao.getProfileSwitchDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getProfileSwitchDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<ProfileSwitch>> =
        database.profileSwitchDao.getProfileSwitchDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getLastProfileSwitchId(): Long? =
        database.profileSwitchDao.getLastId()

    // EFFECTIVE PROFILE SWITCH
    fun findEffectiveProfileSwitchByNSId(nsId: String): EffectiveProfileSwitch? =
        database.effectiveProfileSwitchDao.findByNSId(nsId)

    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementEffectiveProfileSwitch(id: Long): Maybe<Pair<EffectiveProfileSwitch, EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.effectiveProfileSwitchDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    @Suppress("unused")
    fun getModifiedEffectiveProfileSwitchDataFromId(lastId: Long): Single<List<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun createEffectiveProfileSwitch(profileSwitch: EffectiveProfileSwitch) {
        database.effectiveProfileSwitchDao.insert(profileSwitch)
    }

    fun getOldestEffectiveProfileSwitchRecord(): EffectiveProfileSwitch? =
        database.effectiveProfileSwitchDao.getOldestEffectiveProfileSwitchRecord()

    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Single<ValueWrapper<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun getEffectiveProfileSwitchDataFromTime(timestamp: Long, ascending: Boolean): Single<List<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getEffectiveProfileSwitchDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getEffectiveProfileSwitchDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchDataFromTimeToTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllEffectiveProfileSwitches() =
        database.effectiveProfileSwitchDao.deleteAllEntries()

    fun getLastEffectiveProfileSwitchId(): Long? =
        database.effectiveProfileSwitchDao.getLastId()

    // THERAPY EVENT
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun findTherapyEventByNSId(nsId: String): TherapyEvent? =
        database.therapyEventDao.findByNSId(nsId)

    fun getNextSyncElementTherapyEvent(id: Long): Maybe<Pair<TherapyEvent, TherapyEvent>> =
        database.therapyEventDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.therapyEventDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    @Suppress("unused")
    fun getModifiedTherapyEventDataFromId(lastId: Long): Single<List<TherapyEvent>> =
        database.therapyEventDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TherapyEvent>> =
        database.therapyEventDao.getTherapyEventDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTherapyEventDataFromTime(timestamp: Long, type: TherapyEvent.Type, ascending: Boolean): Single<List<TherapyEvent>> =
        database.therapyEventDao.getTherapyEventDataFromTime(timestamp, type)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TherapyEvent>> =
        database.therapyEventDao.getTherapyEventDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    @Suppress("unused")
    fun getValidTherapyEventsByType(type: TherapyEvent.Type): List<TherapyEvent> =
        database.therapyEventDao.getValidByType(type)

    fun deleteAllTherapyEventsEntries() =
        database.therapyEventDao.deleteAllEntries()

    fun getLastTherapyRecordUpToNow(type: TherapyEvent.Type): Single<ValueWrapper<TherapyEvent>> =
        database.therapyEventDao.getLastTherapyRecord(type, System.currentTimeMillis()).toWrappedSingle()
            .subscribeOn(Schedulers.io())

    fun compatGetTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TherapyEvent>> =
        database.therapyEventDao.compatGetTherapyEventDataFromToTime(from, to)
            .subscribeOn(Schedulers.io())

    fun getLastTherapyEventId(): Long? =
        database.therapyEventDao.getLastId()

    // FOOD
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementFood(id: Long): Maybe<Pair<Food, Food>> =
        database.foodDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.foodDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    @Suppress("unused")
    fun getModifiedFoodDataFromId(lastId: Long): Single<List<Food>> =
        database.foodDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getFoodData(): Single<List<Food>> =
        database.foodDao.getFoodData()
            .subscribeOn(Schedulers.io())

    @Suppress("unused")
    fun deleteAllFoods() =
        database.foodDao.deleteAllEntries()

    fun getLastFoodId(): Long? =
        database.foodDao.getLastId()

    // BOLUS
    fun findBolusByNSId(nsId: String): Bolus? =
        database.bolusDao.findByNSId(nsId)

    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */
    fun getNextSyncElementBolus(id: Long): Maybe<Pair<Bolus, Bolus>> =
        database.bolusDao.getNextModifiedOrNewAfterExclude(id, Bolus.Type.PRIMING)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.bolusDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    fun getLastBolusRecord(): Bolus? =
        database.bolusDao.getLastBolusRecord()

    fun getLastBolusRecordWrapped(): Single<ValueWrapper<Bolus>> =
        database.bolusDao.getLastBolusRecordMaybe()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun getLastBolusRecordOfTypeWrapped(type: Bolus.Type): Single<ValueWrapper<Bolus>> =
        database.bolusDao.getLastBolusRecordOfType(type)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun getOldestBolusRecord(): Bolus? =
        database.bolusDao.getOldestBolusRecord()

    fun getBolusesDataFromTime(timestamp: Long, ascending: Boolean): Single<List<Bolus>> =
        database.bolusDao.getBolusesFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getBolusesDataFromTimeToTime(from: Long, to: Long, ascending: Boolean): Single<List<Bolus>> =
        database.bolusDao.getBolusesFromTime(from, to)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getBolusesIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<Bolus>> =
        database.bolusDao.getBolusesIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllBoluses() =
        database.bolusDao.deleteAllEntries()

    fun getLastBolusId(): Long? =
        database.bolusDao.getLastId()
    // CARBS

    fun findCarbsByNSId(nsId: String): Carbs? =
        database.carbsDao.findByNSId(nsId)

    private fun expandCarbs(carbs: Carbs): List<Carbs> =
        if (carbs.duration == 0L) {
            listOf(carbs)
        } else {
            var remainingCarbs = carbs.amount
            val ticks = (carbs.duration / 1000 / 60 / 15).coerceAtLeast(1L)
            (0 until ticks).map {
                val carbTime = carbs.timestamp + it * 15 * 60 * 1000
                val smallCarbAmount = (1.0 * remainingCarbs / (ticks - it)).roundToInt() //on last iteration (ticks-i) is 1 -> smallCarbAmount == remainingCarbs
                remainingCarbs -= smallCarbAmount.toLong()
                Carbs(timestamp = carbTime, amount = smallCarbAmount.toDouble(), duration = 0)
            }.filter { it.amount != 0.0 }
        }

    private fun Single<List<Carbs>>.expand() = this.map { it.map(::expandCarbs).flatten() }
    private fun Single<List<Carbs>>.fromTo(from: Long, to: Long) = this.map { it.filter { c -> c.timestamp in from..to } }
    private fun Single<List<Carbs>>.from(start: Long) = this.map { it.filter { c -> c.timestamp >= start } }
    private fun Single<List<Carbs>>.sort() = this.map { it.sortedBy { c -> c.timestamp } }

    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */
    fun getNextSyncElementCarbs(id: Long): Maybe<Pair<Carbs, Carbs>> =
        database.carbsDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.carbsDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    @Suppress("unused")
    fun getModifiedCarbsDataFromId(lastId: Long): Single<List<Carbs>> =
        database.carbsDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getLastCarbsRecord(): Carbs? =
        database.carbsDao.getLastCarbsRecord()

    fun getLastCarbsRecordWrapped(): Single<ValueWrapper<Carbs>> =
        database.carbsDao.getLastCarbsRecordMaybe()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun getOldestCarbsRecord(): Carbs? =
        database.carbsDao.getOldestCarbsRecord()

    fun getCarbsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCarbsDataFromTimeExpanded(timestamp: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsFromTimeExpandable(timestamp)
            .expand()
            .from(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCarbsDataFromTimeNotExpanded(timestamp: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsFromTimeExpandable(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCarbsDataFromTimeToTimeExpanded(from: Long, to: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsFromTimeToTimeExpandable(from, to)
            .expand()
            .fromTo(from, to)
            .sort()
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCarbsIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllCarbs() =
        database.carbsDao.deleteAllEntries()

    fun getLastCarbsId(): Long? =
        database.carbsDao.getLastId()

    // BOLUS CALCULATOR RESULT
    fun findBolusCalculatorResultByNSId(nsId: String): BolusCalculatorResult? =
        database.bolusCalculatorResultDao.findByNSId(nsId)

    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */
    fun getNextSyncElementBolusCalculatorResult(id: Long): Maybe<Pair<BolusCalculatorResult, BolusCalculatorResult>> =
        database.bolusCalculatorResultDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.bolusCalculatorResultDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    @Suppress("unused")
    fun getModifiedBolusCalculatorResultsDataFromId(lastId: Long): Single<List<BolusCalculatorResult>> =
        database.bolusCalculatorResultDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getBolusCalculatorResultsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<BolusCalculatorResult>> =
        database.bolusCalculatorResultDao.getBolusCalculatorResultsFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getBolusCalculatorResultsIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<BolusCalculatorResult>> =
        database.bolusCalculatorResultDao.getBolusCalculatorResultsIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllBolusCalculatorResults() =
        database.bolusCalculatorResultDao.deleteAllEntries()

    fun getLastBolusCalculatorResultId(): Long? =
        database.bolusCalculatorResultDao.getLastId()

    // DEVICE STATUS
    fun insert(deviceStatus: DeviceStatus) {
        database.deviceStatusDao.insert(deviceStatus)
        changeSubject.onNext(mutableListOf(deviceStatus)) // Not TraceableDao
    }

    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */

    fun getNextSyncElementDeviceStatus(id: Long): Maybe<DeviceStatus> =
        database.deviceStatusDao.getNextModifiedOrNewAfter(id)
            .subscribeOn(Schedulers.io())

    fun getLastDeviceStatusId(): Long? =
        database.deviceStatusDao.getLastId()

    // TEMPORARY BASAL
    fun findTemporaryBasalByNSId(nsId: String): TemporaryBasal? =
        database.temporaryBasalDao.findByNSId(nsId)

    /*
        * returns a Pair of the next entity to sync and the ID of the "update".
        * The update id might either be the entry id itself if it is a new entry - or the id
        * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
        *
        * It is a Maybe as there might be no next element.
        * */

    fun getNextSyncElementTemporaryBasal(id: Long): Maybe<Pair<TemporaryBasal, TemporaryBasal>> =
        database.temporaryBasalDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.temporaryBasalDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    fun getTemporaryBasalActiveAt(timestamp: Long): Single<ValueWrapper<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun getTemporaryBasalsDataActiveBetweenTimeAndTime(from: Long, to: Long): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalActiveBetweenTimeAndTime(from, to)
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsDataFromTimeToTime(from: Long, to: Long, ascending: Boolean): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalDataFromTimeToTime(from, to)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOldestTemporaryBasalRecord(): TemporaryBasal? =
        database.temporaryBasalDao.getOldestRecord()

    fun getLastTemporaryBasalId(): Long? =
        database.temporaryBasalDao.getLastId()

    // EXTENDED BOLUS
    fun findExtendedBolusByNSId(nsId: String): ExtendedBolus? =
        database.extendedBolusDao.findByNSId(nsId)

    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */

    fun getNextSyncElementExtendedBolus(id: Long): Maybe<Pair<ExtendedBolus, ExtendedBolus>> =
        database.extendedBolusDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.extendedBolusDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    @Suppress("unused")
    fun getModifiedExtendedBolusDataFromId(lastId: Long): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getExtendedBolusActiveAt(timestamp: Long): Single<ValueWrapper<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun getExtendedBolusDataFromTime(timestamp: Long, ascending: Boolean): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getExtendedBolusDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusDataFromTimeToTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getExtendedBolusDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOldestExtendedBolusRecord(): ExtendedBolus? =
        database.extendedBolusDao.getOldestRecord()

    fun getLastExtendedBolusId(): Long? =
        database.extendedBolusDao.getLastId()

    // TotalDailyDose
    fun getLastTotalDailyDoses(count: Int, ascending: Boolean): Single<List<TotalDailyDose>> =
        database.totalDailyDoseDao.getLastTotalDailyDoses(count)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCalculatedTotalDailyDose(timestamp: Long): Single<ValueWrapper<TotalDailyDose>> =
        database.totalDailyDoseDao.findByTimestamp(timestamp, InterfaceIDs.PumpType.CACHE)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun insertTotalDailyDose(tdd: TotalDailyDose) {
        database.totalDailyDoseDao.insert(tdd)
    }

    // OFFLINE EVENT
    fun findOfflineEventByNSId(nsId: String): OfflineEvent? =
        database.offlineEventDao.findByNSId(nsId)

    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementOfflineEvent(id: Long): Maybe<Pair<OfflineEvent, OfflineEvent>> =
        database.offlineEventDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.offlineEventDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    fun getOfflineEventDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<OfflineEvent>> =
        database.offlineEventDao.getOfflineEventDataFromTimeToTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    @Suppress("unused")
    fun getModifiedOfflineEventsDataFromId(lastId: Long): Single<List<OfflineEvent>> =
        database.offlineEventDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getOfflineEventActiveAt(timestamp: Long): Single<ValueWrapper<OfflineEvent>> =
        database.offlineEventDao.getOfflineEventActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    @Suppress("unused")
    fun deleteAllOfflineEventEntries() =
        database.offlineEventDao.deleteAllEntries()

    fun getLastOfflineEventId(): Long? =
        database.offlineEventDao.getLastId()

    fun getHeartRatesFromTime(timeMillis: Long): Single<List<HeartRate>> =
        database.heartRateDao.getFromTime(timeMillis)
            .subscribeOn(Schedulers.io())

    fun getHeartRatesFromTimeToTime(startMillis: Long, endMillis: Long) =
        database.heartRateDao.getFromTimeToTime(startMillis, endMillis)

    suspend fun collectNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int) = NewEntries(
        apsResults = database.apsResultDao.getNewEntriesSince(since, until, limit, offset),
        apsResultLinks = database.apsResultLinkDao.getNewEntriesSince(since, until, limit, offset),
        bolusCalculatorResults = database.bolusCalculatorResultDao.getNewEntriesSince(since, until, limit, offset),
        boluses = database.bolusDao.getNewEntriesSince(since, until, limit, offset),
        carbs = database.carbsDao.getNewEntriesSince(since, until, limit, offset),
        effectiveProfileSwitches = database.effectiveProfileSwitchDao.getNewEntriesSince(since, until, limit, offset),
        extendedBoluses = database.extendedBolusDao.getNewEntriesSince(since, until, limit, offset),
        glucoseValues = database.glucoseValueDao.getNewEntriesSince(since, until, limit, offset),
        multiwaveBolusLinks = database.multiwaveBolusLinkDao.getNewEntriesSince(since, until, limit, offset),
        offlineEvents = database.offlineEventDao.getNewEntriesSince(since, until, limit, offset),
        preferencesChanges = database.preferenceChangeDao.getNewEntriesSince(since, until, limit, offset),
        profileSwitches = database.profileSwitchDao.getNewEntriesSince(since, until, limit, offset),
        temporaryBasals = database.temporaryBasalDao.getNewEntriesSince(since, until, limit, offset),
        temporaryTarget = database.temporaryTargetDao.getNewEntriesSince(since, until, limit, offset),
        therapyEvents = database.therapyEventDao.getNewEntriesSince(since, until, limit, offset),
        totalDailyDoses = database.totalDailyDoseDao.getNewEntriesSince(since, until, limit, offset),
        versionChanges = database.versionChangeDao.getNewEntriesSince(since, until, limit, offset),
        heartRates = database.heartRateDao.getNewEntriesSince(since, until, limit, offset),
    )
}

@Suppress("USELESS_CAST")
inline fun <reified T : Any> Maybe<T>.toWrappedSingle(): Single<ValueWrapper<T>> =
    this.map { ValueWrapper.Existing(it) as ValueWrapper<T> }
        .switchIfEmpty(Maybe.just(ValueWrapper.Absent()))
        .toSingle()
