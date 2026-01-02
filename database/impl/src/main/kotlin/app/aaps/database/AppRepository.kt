package app.aaps.database

import app.aaps.database.entities.APSResult
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.Food
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.StepsCount
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.TotalDailyDose
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.data.NewEntries
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.transactions.Transaction
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

    fun clearApsResults() = database.apsResultDao.deleteAllEntries()

    fun cleanupDatabase(keepDays: Long, deleteTrackedChanges: Boolean): String {
        val than = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays)
        val removed = mutableListOf<Pair<String, Int>>()
        removed.add(Pair("APSResult", database.apsResultDao.deleteOlderThan(than)))
        removed.add(Pair("GlucoseValue", database.glucoseValueDao.deleteOlderThan(than)))
        removed.add(Pair("TherapyEvent", database.therapyEventDao.deleteOlderThan(than)))
        removed.add(Pair("TemporaryBasal", database.temporaryBasalDao.deleteOlderThan(than)))
        removed.add(Pair("ExtendedBolus", database.extendedBolusDao.deleteOlderThan(than)))
        removed.add(Pair("Bolus", database.bolusDao.deleteOlderThan(than)))
        removed.add(Pair("TotalDailyDose", database.totalDailyDoseDao.deleteOlderThan(than)))
        removed.add(Pair("Carbs", database.carbsDao.deleteOlderThan(than)))
        removed.add(Pair("TemporaryTarget", database.temporaryTargetDao.deleteOlderThan(than)))
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
        removed.add(Pair("RunningMode", database.runningModeDao.deleteOlderThan(than)))
        removed.add(Pair("HeartRate", database.heartRateDao.deleteOlderThan(than)))
        removed.add(Pair("StepsCount", database.stepsCountDao.deleteOlderThan(than)))

        if (deleteTrackedChanges) {
            removed.add(Pair("CHANGES APSResult", database.apsResultDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES GlucoseValue", database.glucoseValueDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TherapyEvent", database.therapyEventDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TemporaryBasal", database.temporaryBasalDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES Bolus", database.bolusDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES ExtendedBolus", database.extendedBolusDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TotalDailyDose", database.totalDailyDoseDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES Carbs", database.carbsDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES TemporaryTarget", database.temporaryTargetDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES BolusCalculatorResult", database.bolusCalculatorResultDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES EffectiveProfileSwitch", database.effectiveProfileSwitchDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES ProfileSwitch", database.profileSwitchDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES ApsResult", database.apsResultDao.deleteTrackedChanges()))
            // keep food database.foodDao.deleteHistory()
            removed.add(Pair("CHANGES RunningMode", database.runningModeDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES HeartRate", database.heartRateDao.deleteTrackedChanges()))
            removed.add(Pair("CHANGES StepsCount", database.stepsCountDao.deleteTrackedChanges()))
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

    fun getLastGlucoseValueId(): Long? =
        database.glucoseValueDao.getLastId()

    fun getLastGlucoseValue(): GlucoseValue? =
        database.glucoseValueDao.getLast()
            .subscribeOn(Schedulers.io())
            .blockingGet()

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

    fun getTemporaryTargetActiveAt(timestamp: Long): Maybe<TemporaryTarget> =
        database.temporaryTargetDao.getTemporaryTargetActiveAt(timestamp)
            .subscribeOn(Schedulers.io())

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

    fun getProfileSwitchActiveAt(timestamp: Long): ProfileSwitch? {
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

    fun getPermanentProfileSwitchActiveAt(timestamp: Long): Maybe<ProfileSwitch> =
        database.profileSwitchDao.getPermanentProfileSwitchActiveAt(timestamp)
            .subscribeOn(Schedulers.io())

    fun getAllProfileSwitches(): Single<List<ProfileSwitch>> =
        database.profileSwitchDao.getAllProfileSwitches()
            .subscribeOn(Schedulers.io())

    fun getProfileSwitchesFromTime(timestamp: Long, ascending: Boolean): Single<List<ProfileSwitch>> =
        database.profileSwitchDao.getProfileSwitchDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getProfileSwitchesIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<ProfileSwitch>> =
        database.profileSwitchDao.getProfileSwitchDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getLastProfileSwitchId(): Long? =
        database.profileSwitchDao.getLastId()

    // RUNNING MODE

    fun findRunningModeByNSId(nsId: String): RunningMode? =
        database.runningModeDao.findByNSId(nsId)

    fun getNextSyncElementRunningMode(id: Long): Maybe<Pair<RunningMode, RunningMode>> =
        database.runningModeDao.getNextModifiedOrNewAfter(id)
            .flatMap { nextIdElement ->
                val nextIdElemReferenceId = nextIdElement.referenceId
                if (nextIdElemReferenceId == null) {
                    Maybe.just(nextIdElement to nextIdElement)
                } else {
                    database.runningModeDao.getCurrentFromHistoric(nextIdElemReferenceId)
                        .map { it to nextIdElement }
                }
            }

    fun getRunningModeActiveAt(timestamp: Long): RunningMode? {
        val trm = database.runningModeDao.getTemporaryRunningModeActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .blockingGet()
        val prm = database.runningModeDao.getPermanentRunningModeActiveAt(timestamp)
            .subscribeOn(Schedulers.io())
            .blockingGet()
        if (trm != null && prm != null)
            return if (prm.timestamp > trm.timestamp) prm else trm
        if (prm == null) return trm
        return prm // if (trm == null)
    }

    fun getPermanentRunningModeActiveAt(timestamp: Long): Maybe<RunningMode> =
        database.runningModeDao.getPermanentRunningModeActiveAt(timestamp)
            .subscribeOn(Schedulers.io())

    fun getAllRunningModes(): Single<List<RunningMode>> =
        database.runningModeDao.getAllRunningModes()
            .subscribeOn(Schedulers.io())

    fun getRunningModesFromTime(timestamp: Long, ascending: Boolean): Single<List<RunningMode>> =
        database.runningModeDao.getRunningModeDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getRunningModesFromTimeToTime(startTime: Long, endTime: Long, ascending: Boolean): Single<List<RunningMode>> =
        database.runningModeDao.getRunningModeDataFromTimeToTime(startTime, endTime)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getRunningModesIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<RunningMode>> =
        database.runningModeDao.getRunningModeDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getLastRunningModeId(): Long? =
        database.runningModeDao.getLastId()

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

    fun getOldestEffectiveProfileSwitchRecord(): Maybe<EffectiveProfileSwitch> =
        database.effectiveProfileSwitchDao.getOldestEffectiveProfileSwitchRecord()
            .subscribeOn(Schedulers.io())

    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Maybe<EffectiveProfileSwitch> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchActiveAt(timestamp)
            .subscribeOn(Schedulers.io())

    fun getEffectiveProfileSwitchesFromTime(timestamp: Long, ascending: Boolean): Single<List<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getEffectiveProfileSwitchesIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getEffectiveProfileSwitchesFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<EffectiveProfileSwitch>> =
        database.effectiveProfileSwitchDao.getEffectiveProfileSwitchDataFromTimeToTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

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

    fun getLastTherapyRecordUpToNow(type: TherapyEvent.Type): Maybe<TherapyEvent> =
        database.therapyEventDao.getLastTherapyRecord(type, System.currentTimeMillis())
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

    fun getFoodData(): Single<List<Food>> =
        database.foodDao.getFoodData()
            .subscribeOn(Schedulers.io())

    fun getLastFoodId(): Long? =
        database.foodDao.getLastId()

    // BOLUS
    fun getBolusByNSId(nsId: String): Bolus? =
        database.bolusDao.getByNSId(nsId)

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

    fun getNewestBolus(): Maybe<Bolus> =
        database.bolusDao.getLastBolusRecord()
            .subscribeOn(Schedulers.io())

    fun getLastBolusRecordOfType(type: Bolus.Type): Maybe<Bolus> =
        database.bolusDao.getLastBolusRecordOfType(type)
            .subscribeOn(Schedulers.io())

    fun getOldestBolus(): Maybe<Bolus> =
        database.bolusDao.getOldestBolusRecord()
            .subscribeOn(Schedulers.io())

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

    fun getLastBolusId(): Long? =
        database.bolusDao.getLastId()
    // CARBS

    fun getCarbsByNSId(nsId: String): Carbs? =
        database.carbsDao.getByNSId(nsId)

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

    fun getLastCarbs(): Maybe<Carbs> =
        database.carbsDao.getLastCarbsRecordMaybe()
            .subscribeOn(Schedulers.io())

    fun getOldestCarbs(): Maybe<Carbs> =
        database.carbsDao.getOldestCarbsRecord()
            .subscribeOn(Schedulers.io())

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

    fun getBolusCalculatorResultsDataFromTime(timestamp: Long, ascending: Boolean): Single<List<BolusCalculatorResult>> =
        database.bolusCalculatorResultDao.getBolusCalculatorResultsFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getBolusCalculatorResultsIncludingInvalidFromTime(timestamp: Long, ascending: Boolean): Single<List<BolusCalculatorResult>> =
        database.bolusCalculatorResultDao.getBolusCalculatorResultsIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

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

    fun getTemporaryBasalActiveAt(timestamp: Long): Maybe<TemporaryBasal> =
        database.temporaryBasalDao.getTemporaryBasalActiveAt(timestamp)
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsActiveBetweenTimeAndTime(from: Long, to: Long): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalActiveBetweenTimeAndTime(from, to)
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsStartingFromTime(timestamp: Long, ascending: Boolean): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalDataFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsStartingFromTimeToTime(from: Long, to: Long, ascending: Boolean): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalStartingFromTimeToTime(from, to)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getTemporaryBasalsStartingFromTimeIncludingInvalid(timestamp: Long, ascending: Boolean): Single<List<TemporaryBasal>> =
        database.temporaryBasalDao.getTemporaryBasalDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOldestTemporaryBasalRecord(): Maybe<TemporaryBasal> =
        database.temporaryBasalDao.getOldestRecord()
            .subscribeOn(Schedulers.io())

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

    fun getExtendedBolusActiveAt(timestamp: Long): Maybe<ExtendedBolus> =
        database.extendedBolusDao.getExtendedBolusActiveAt(timestamp)
            .subscribeOn(Schedulers.io())

    fun getExtendedBolusesStartingFromTime(timestamp: Long, ascending: Boolean): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusesStartingFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getExtendedBolusesStartingFromTimeToTime(start: Long, end: Long, ascending: Boolean): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusDataFromTimeToTime(start, end)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getExtendedBolusStartingFromTimeIncludingInvalid(timestamp: Long, ascending: Boolean): Single<List<ExtendedBolus>> =
        database.extendedBolusDao.getExtendedBolusDataIncludingInvalidFromTime(timestamp)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getOldestExtendedBolusRecord(): Maybe<ExtendedBolus> =
        database.extendedBolusDao.getOldestRecord()
            .subscribeOn(Schedulers.io())

    fun getLastExtendedBolusId(): Long? =
        database.extendedBolusDao.getLastId()

    // TotalDailyDose
    fun getLastTotalDailyDoses(count: Int, ascending: Boolean): Single<List<TotalDailyDose>> =
        database.totalDailyDoseDao.getLastTotalDailyDoses(count)
            .map { if (!ascending) it.reversed() else it }
            .subscribeOn(Schedulers.io())

    fun getCalculatedTotalDailyDose(timestamp: Long): Maybe<TotalDailyDose> =
        database.totalDailyDoseDao.findByTimestamp(timestamp, InterfaceIDs.PumpType.CACHE)
            .subscribeOn(Schedulers.io())

    // HEART RATES

    fun getHeartRatesFromTime(timeMillis: Long): List<HeartRate> =
        database.heartRateDao.getFromTime(timeMillis)
            .subscribeOn(Schedulers.io())
            .blockingGet()

    fun getHeartRatesFromTimeToTime(startMillis: Long, endMillis: Long): Single<List<HeartRate>> =
        database.heartRateDao.getFromTimeToTime(startMillis, endMillis)
            .subscribeOn(Schedulers.io())

    fun getStepsCountFromTime(timeMillis: Long): Single<List<StepsCount>> =
        database.stepsCountDao.getFromTime(timeMillis)
            .subscribeOn(Schedulers.io())

    fun getStepsCountFromTimeToTime(startMillis: Long, endMillis: Long) =
        database.stepsCountDao.getFromTimeToTime(startMillis, endMillis)

    fun getLastStepsCountFromTimeToTime(startMillis: Long, endMillis: Long) =
        database.stepsCountDao.getLastStepsCountFromTimeToTime(startMillis, endMillis)

    fun collectNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int) = NewEntries(
        apsResults = database.apsResultDao.getNewEntriesSince(since, until, limit, offset),
        bolusCalculatorResults = database.bolusCalculatorResultDao.getNewEntriesSince(since, until, limit, offset),
        boluses = database.bolusDao.getNewEntriesSince(since, until, limit, offset),
        carbs = database.carbsDao.getNewEntriesSince(since, until, limit, offset),
        effectiveProfileSwitches = database.effectiveProfileSwitchDao.getNewEntriesSince(since, until, limit, offset),
        extendedBoluses = database.extendedBolusDao.getNewEntriesSince(since, until, limit, offset),
        glucoseValues = database.glucoseValueDao.getNewEntriesSince(since, until, limit, offset),
        runningModes = database.runningModeDao.getNewEntriesSince(since, until, limit, offset),
        preferencesChanges = database.preferenceChangeDao.getNewEntriesSince(since, until, limit, offset),
        profileSwitches = database.profileSwitchDao.getNewEntriesSince(since, until, limit, offset),
        temporaryBasals = database.temporaryBasalDao.getNewEntriesSince(since, until, limit, offset),
        temporaryTarget = database.temporaryTargetDao.getNewEntriesSince(since, until, limit, offset),
        therapyEvents = database.therapyEventDao.getNewEntriesSince(since, until, limit, offset),
        totalDailyDoses = database.totalDailyDoseDao.getNewEntriesSince(since, until, limit, offset),
        versionChanges = database.versionChangeDao.getNewEntriesSince(since, until, limit, offset),
        heartRates = database.heartRateDao.getNewEntriesSince(since, until, limit, offset),
        stepsCount = database.stepsCountDao.getNewEntriesSince(since, until, limit, offset),
    )

    fun getApsResultCloseTo(timestamp: Long): Maybe<APSResult> =
        database.apsResultDao.getApsResult(timestamp - 5 * 60 * 1000, timestamp)
            .subscribeOn(Schedulers.io())

    fun getApsResults(start: Long, end: Long): Single<List<APSResult>> =
        database.apsResultDao.getApsResults(start, end)
            .subscribeOn(Schedulers.io())

}

@Suppress("USELESS_CAST", "unused")
inline fun <reified T : Any> Maybe<T>.toWrappedSingle(): Single<ValueWrapper<T>> =
    this.map { ValueWrapper.Existing(it) as ValueWrapper<T> }
        .switchIfEmpty(Maybe.just(ValueWrapper.Absent()))
        .toSingle()
