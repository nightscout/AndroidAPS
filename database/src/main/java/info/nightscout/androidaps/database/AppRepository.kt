package info.nightscout.androidaps.database

import info.nightscout.androidaps.database.embedments.InterfaceIDs
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

    fun getTemporaryTargetsCorrespondingLastHistoryRecord(lastId: Long): TemporaryTarget? =
        database.temporaryTargetDao.getLastHistoryRecord(lastId)

    fun getTemporaryTargetActiveAt(timestamp: Long): Single<ValueWrapper<TemporaryTarget>> =
        database.temporaryTargetDao.getTemporaryTargetActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .toWrappedSingle()

    fun deleteAllTempTargetEntries() =
        database.temporaryTargetDao.deleteAllEntries()

    // USER ENTRY
    fun getAllUserEntries(): Single<List<UserEntry>> =
        database.userEntryDao.getAll()
            .subscribeOn(Schedulers.io())

    fun insert(word: UserEntry) {
        database.userEntryDao.insert(word)
    }

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

    // BOLUS
    /*
      * returns a Pair of the next entity to sync and the ID of the "update".
      * The update id might either be the entry id itself if it is a new entry - or the id
      * of the update ("historic") entry. The sync counter should be incremented to that id if it was synced successfully.
      *
      * It is a Maybe as there might be no next element.
      * */
    fun getNextSyncElementBolus(id: Long): Maybe<Pair<Bolus, Long>> =
        database.bolusDao.getNextModifiedOrNewAfter(id)
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

    fun findBolusByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): Bolus? =
        database.bolusDao.findByPumpIds(pumpId, pumpType, pumpSerial)

    fun getBolusesDataFromTime(timestamp: Long, ascending: Boolean): Single<List<Bolus>> =
        database.bolusDao.getBolusesFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getBolusesIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<Bolus>> =
        database.bolusDao.getBolusesIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllBoluses() =
        database.bolusDao.deleteAllEntries()

    // CARBS
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

    fun getCarbsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCarbsIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<Carbs>> =
        database.carbsDao.getCarbsIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun deleteAllCarbs() =
        database.carbsDao.deleteAllEntries()

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