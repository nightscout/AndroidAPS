package info.nightscout.androidaps.database

import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.database.interfaces.DBEntry
import info.nightscout.androidaps.database.transactions.Transaction
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
open class AppRepository @Inject internal constructor(
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
    fun <T> runTransactionForResult(transaction: Transaction<T>): Single<T> {
        val changes = mutableListOf<DBEntry>()
        return Single.fromCallable {
            database.runInTransaction(Callable<T> {
                transaction.database = DelegatedAppDatabase(changes, database)
                transaction.run()
            })
        }.subscribeOn(Schedulers.io()).doOnSuccess {
            changeSubject.onNext(changes)
        }
    }

    fun clearDatabases() = database.clearAllTables()

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
    fun findBgReadingByNSIdSingle(nsId: String): Single<ValueWrapper<GlucoseValue>> =
        database.glucoseValueDao.findByNSIdMaybe(nsId).toWrappedSingle()

    fun getModifiedBgReadingsDataFromId(lastId: Long): Single<List<GlucoseValue>> =
        database.glucoseValueDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getLastGlucoseValueIdWrapped(): Single<ValueWrapper<Long>> =
        database.glucoseValueDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

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
    fun getNextSyncElementGlucoseValue(id: Long): Maybe<Pair<GlucoseValue, Long>> =
        database.glucoseValueDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.glucoseValueDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

    fun getBgReadingsCorrespondingLastHistoryRecord(lastId: Long): GlucoseValue? =
        database.glucoseValueDao.getLastHistoryRecord(lastId)

    @Suppress("unused") // debug purpose only
    fun getAllBgReadingsStartingFrom(lastId: Long): Single<List<GlucoseValue>> =
        database.glucoseValueDao.getAllStartingFrom(lastId)
            .subscribeOn(Schedulers.io())

    // TEMP TARGETS
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementTemporaryTarget(id: Long): Maybe<Pair<TemporaryTarget, Long>> =
        database.temporaryTargetDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.temporaryTargetDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

    fun compatGetTemporaryTargetData(): Single<List<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetData()
            .subscribeOn(Schedulers.io())

    fun getTemporaryTargetDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTemporaryTargetDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getModifiedTemporaryTargetsDataFromId(lastId: Long): Single<List<TemporaryTarget>> =
        database.temporaryTargetDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getTemporaryTargetActiveAt(timestamp: Long): Single<ValueWrapper<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun deleteAllTempTargetEntries() =
        database.temporaryTargetDao.deleteAllEntries()

    fun getLastTempTargetIdWrapped(): Single<ValueWrapper<Long>> =
        database.temporaryTargetDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // USER ENTRY
    fun getAllUserEntries(): Single<List<UserEntry>> =
        database.userEntryDao.getAll()
            .subscribeOn(Schedulers.io())

    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UserEntry>> =
        database.userEntryDao.getUserEntryDataFromTime(timestamp)
            .subscribeOn(Schedulers.io())

    fun getUserEntryFilteredDataFromTime(timestamp: Long): Single<List<UserEntry>> =
        database.userEntryDao.getUserEntryFilteredDataFromTime(UserEntry.Sources.Loop, timestamp)
            .subscribeOn(Schedulers.io())

    fun insert(word: UserEntry) {
        database.userEntryDao.insert(word)
    }

    // PROFILE SWITCH

    fun getNextSyncElementProfileSwitch(id: Long): Maybe<Pair<ProfileSwitch, Long>> =
        database.profileSwitchDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.profileSwitchDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

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
        if (tps == null) return ps
        return null
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

    fun getLastProfileSwitchIdWrapped(): Single<ValueWrapper<Long>> =
        database.profileSwitchDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // EFFECTIVE PROFILE SWITCH
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementEffectiveProfileSwitch(id: Long): Maybe<Pair<EffectiveProfileSwitch, Long>> =
        database.effectiveProfileSwitchDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.effectiveProfileSwitchDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

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

    fun getLastEffectiveProfileSwitchIdWrapped(): Single<ValueWrapper<Long>> =
        database.effectiveProfileSwitchDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // THERAPY EVENT
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementTherapyEvent(id: Long): Maybe<Pair<TherapyEvent, Long>> =
        database.therapyEventDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.therapyEventDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

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

    fun getLastTherapyRecord(type: TherapyEvent.Type): Single<ValueWrapper<TherapyEvent>> =
        database.therapyEventDao.getLastTherapyRecord(type).toWrappedSingle()
            .subscribeOn(Schedulers.io())

    fun getTherapyEventByTimestamp(type: TherapyEvent.Type, timestamp: Long): TherapyEvent? =
        database.therapyEventDao.findByTimestamp(type, timestamp)

    fun compatGetTherapyEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<TherapyEvent>> =
        database.therapyEventDao.compatGetTherapyEventDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun compatGetTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TherapyEvent>> =
        database.therapyEventDao.compatGetTherapyEventDataFromToTime(from, to)
            .subscribeOn(Schedulers.io())

    fun getLastTherapyEventIdWrapped(): Single<ValueWrapper<Long>> =
        database.therapyEventDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // FOOD
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementFood(id: Long): Maybe<Pair<Food, Long>> =
        database.foodDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.foodDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

    fun getModifiedFoodDataFromId(lastId: Long): Single<List<Food>> =
        database.foodDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getFoodData(): Single<List<Food>> =
        database.foodDao.getFoodData()
            .subscribeOn(Schedulers.io())

    fun deleteAllFoods() =
        database.foodDao.deleteAllEntries()

    fun getLastFoodIdWrapped(): Single<ValueWrapper<Long>> =
        database.foodDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // BOLUS
    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */
    fun getNextSyncElementBolus(id: Long): Maybe<Pair<Bolus, Long>> =
        database.bolusDao.getNextModifiedOrNewAfterExclude(id, Bolus.Type.PRIMING)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.bolusDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

    fun getModifiedBolusesDataFromId(lastId: Long): Single<List<Bolus>> =
        database.bolusDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getLastBolusRecord(): Bolus? =
        database.bolusDao.getLastBolusRecord()

    fun getLastBolusRecordWrapped(): Single<ValueWrapper<Bolus>> =
        database.bolusDao.getLastBolusRecordMaybe()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun getLastBolusRecordOfType(type: Bolus.Type): Bolus? =
        database.bolusDao.getLastBolusRecordOfType(type)

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

    fun getBolusesIncludingInvalidFromTimeToTime(from: Long, to: Long, ascending: Boolean): Single<List<Bolus>> =
        database.bolusDao.getBolusesIncludingInvalidFromTimeToTime(from, to)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllBoluses() =
        database.bolusDao.deleteAllEntries()

    fun getLastBolusIdWrapped(): Single<ValueWrapper<Long>> =
        database.bolusDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()
    // CARBS

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
    private fun Single<List<Carbs>>.filterOutExtended() = this.map { it.filter { c -> c.duration == 0L } }
    private fun Single<List<Carbs>>.fromTo(from: Long, to: Long) = this.map { it.filter { c -> c.timestamp in from..to } }
    private fun Single<List<Carbs>>.until(to: Long) = this.map { it.filter { c -> c.timestamp <= to } }
    private fun Single<List<Carbs>>.from(start: Long) = this.map { it.filter { c -> c.timestamp >= start } }
    private fun Single<List<Carbs>>.sort() = this.map { it.sortedBy { c -> c.timestamp } }

    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */
    fun getNextSyncElementCarbs(id: Long): Maybe<Pair<Carbs, Long>> =
        database.carbsDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.carbsDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

    fun getModifiedCarbsDataFromId(lastId: Long): Single<List<Carbs>> =
        database.carbsDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getCarbsByTimestamp(timestamp: Long): Carbs? =
        database.carbsDao.findByTimestamp(timestamp)

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

    fun getCarbsDataFromTimeToTime(from: Long, to: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsFromTimeToTime(from, to)
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

    fun getCarbsIncludingInvalidFromTimeExpanded(timestamp: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsIncludingInvalidFromTimeExpandable(timestamp)
            .expand()
            .from(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCarbsIncludingInvalidFromTimeToTimeExpanded(from: Long, to: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsIncludingInvalidFromTimeToTimeExpandable(from, to)
            .expand()
            .fromTo(from, to)
            .sort()
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllCarbs() =
        database.carbsDao.deleteAllEntries()

    fun getLastCarbsIdWrapped(): Single<ValueWrapper<Long>> =
        database.carbsDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // BOLUS CALCULATOR RESULT
    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */
    fun getNextSyncElementBolusCalculatorResult(id: Long): Maybe<Pair<BolusCalculatorResult, Long>> =
        database.bolusCalculatorResultDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.bolusCalculatorResultDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

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

    fun getLastBolusCalculatorResultIdWrapped(): Single<ValueWrapper<Long>> =
        database.bolusCalculatorResultDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // DEVICE STATUS
    fun insert(deviceStatus: DeviceStatus): Long =
        database.deviceStatusDao.insert(deviceStatus)

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

    fun getModifiedDeviceStatusDataFromId(lastId: Long): Single<List<DeviceStatus>> =
        database.deviceStatusDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getLastDeviceStatusIdWrapped(): Single<ValueWrapper<Long>> =
        database.deviceStatusDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // TEMPORARY BASAL
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */

    fun getNextSyncElementTemporaryBasal(id: Long): Maybe<Pair<TemporaryBasal, Long>> =
        database.temporaryBasalDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.temporaryBasalDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

    fun getModifiedTemporaryBasalDataFromId(lastId: Long): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsData(): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalData()
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalActiveAt(timestamp: Long): Single<ValueWrapper<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

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

    fun getTemporaryBasalsDataIncludingInvalidFromTimeToTime(from: Long, to: Long, ascending: Boolean): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalDataIncludingInvalidFromTimeToTime(from, to)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOldestTemporaryBasalRecord(): TemporaryBasal? =
        database.temporaryBasalDao.getOldestRecord()

    fun getLastTemporaryBasalIdWrapped(): Single<ValueWrapper<Long>> =
        database.temporaryBasalDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // EXTENDED BOLUS
    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */

    fun getNextSyncElementExtendedBolus(id: Long): Maybe<Pair<ExtendedBolus, Long>> =
        database.extendedBolusDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.extendedBolusDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

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

    fun getExtendedBolusDataIncludingInvalidFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusDataIncludingInvalidFromTimeToTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOldestExtendedBolusRecord(): ExtendedBolus? =
        database.extendedBolusDao.getOldestRecord()

    // TotalDailyDose
    fun getAllTotalDailyDoses(ascending: Boolean): Single<List<TotalDailyDose>> =
        database.totalDailyDoseDao.getAllTotalDailyDoses()
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getLastTotalDailyDoses(count: Int, ascending: Boolean): Single<List<TotalDailyDose>> =
        database.totalDailyDoseDao.getLastTotalDailyDoses(count)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getLastExtendedBolusIdWrapped(): Single<ValueWrapper<Long>> =
        database.extendedBolusDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    // OFFLINE EVENT
    /*
       * returns a Pair of the next entity to sync and the ID of the "update".
       * The update id might either be the entry id itself if it is a new entry - or the id
       * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
       *
       * It is a Maybe as there might be no next element.
       * */
    fun getNextSyncElementOfflineEvent(id: Long): Maybe<Pair<OfflineEvent, Long>> =
        database.offlineEventDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement.id)
                } else {
                    database.offlineEventDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement.id }
                }
            }

    fun compatGetOfflineEventData(): Single<List<OfflineEvent>> =
        database.offlineEventDao.getOfflineEventData()
            .subscribeOn(Schedulers.io())

    fun getOfflineEventDataFromTime(timestamp: Long, ascending: Boolean): Single<List<OfflineEvent>> =
        database.offlineEventDao.getOfflineEventDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOfflineEventDataIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<OfflineEvent>> =
        database.offlineEventDao.getOfflineEventDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOfflineEventDataFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<OfflineEvent>> =
        database.offlineEventDao.getOfflineEventDataFromTimeToTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getModifiedOfflineEventsDataFromId(lastId: Long): Single<List<OfflineEvent>> =
        database.offlineEventDao.getModifiedFrom(lastId)
            .subscribeOn(Schedulers.io())

    fun getOfflineEventActiveAt(timestamp: Long): Single<ValueWrapper<OfflineEvent>> =
        database.offlineEventDao.getOfflineEventActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun deleteAllOfflineEventEntries() =
        database.offlineEventDao.deleteAllEntries()

    fun getLastOfflineEventIdWrapped(): Single<ValueWrapper<Long>> =
        database.offlineEventDao.getLastId()
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()


}

@Suppress("USELESS_CAST")
inline fun <reified T : Any> Maybe<T>.toWrappedSingle(): Single<ValueWrapper<T>> =
    this.map { ValueWrapper.Existing(it) as ValueWrapper<T> }
        .switchIfEmpty(Maybe.just(ValueWrapper.Absent()))
        .toSingle()

sealed class ValueWrapper<T> {
    data class Existing<T>(val value: T) : ValueWrapper<T>()
    class Absent<T> : ValueWrapper<T>()
}