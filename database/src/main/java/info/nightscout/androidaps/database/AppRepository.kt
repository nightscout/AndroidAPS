package info.nightscout.androidaps.database

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.UserEntry
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

    fun getBgReadingsCorrespondingLastHistoryRecord(lastId: Long): GlucoseValue? =
        database.glucoseValueDao.getLastHistoryRecord(lastId)

    @Suppress("unused") // debug purpose only
    fun getAllBgReadingsStartingFrom(lastId: Long): Single<List<GlucoseValue>> =
        database.glucoseValueDao.getAllStartingFrom(lastId)
            .subscribeOn(Schedulers.io())

    // TEMP TARGETS
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

    fun findTemporaryTargetByNSIdSingle(nsId: String): TemporaryTarget? =
        database.temporaryTargetDao.findByNSId(nsId)

    fun findTemporaryTargetByTimestamp(timestamp: Long): TemporaryTarget? =
        database.temporaryTargetDao.findByTimestamp(timestamp)

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