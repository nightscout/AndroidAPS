package info.nightscout.androidaps.plugins.pump.medtronic.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.db.*
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.*
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpBolusType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.apache.commons.lang3.StringUtils
import org.joda.time.LocalDateTime
import org.joda.time.Minutes
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 10/12/18.
 */
// TODO: After release we need to refactor how data is retrieved from pump, each entry in history needs to be marked, and sorting
//  needs to happen according those markings, not on time stamp (since AAPS can change time anytime it drifts away). This
//  needs to include not returning any records if TZ goes into -x area. To fully support this AAPS would need to take note of
//  all times that time changed (TZ, DST, etc.). Data needs to be returned in batches (time_changed batches, so that we can
//  handle it. It would help to assign sort_ids to items (from oldest (1) to newest (x)
// All things marked with "TODO: Fix db code" needs to be updated in new 2.5 database code
@Suppress("DEPRECATION")
@Singleton
class MedtronicHistoryData @Inject constructor(
    val injector: HasAndroidInjector,
    val aapsLogger: AAPSLogger,
    val sp: SP,
    val activePlugin: ActivePlugin,
    val medtronicUtil: MedtronicUtil,
    val medtronicPumpHistoryDecoder: MedtronicPumpHistoryDecoder,
    val medtronicPumpStatus: MedtronicPumpStatus,
    val databaseHelper: DatabaseHelperInterface,
    val pumpSync: PumpSync
) {

    val allHistory: MutableList<PumpHistoryEntry> = mutableListOf()
    private var newHistory: MutableList<PumpHistoryEntry> = mutableListOf()
    private var isInit = false

    private var pumpTime: ClockDTO? = null
    private var lastIdUsed: Long = 0
    private var gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
    private var gsonCore: Gson = GsonBuilder().create()


    /**
     * Add New History entries
     *
     * @param result PumpHistoryResult instance
     */
    fun addNewHistory(result: PumpHistoryResult) {
        val validEntries: List<PumpHistoryEntry> = result.validEntries
        val newEntries: MutableList<PumpHistoryEntry> = mutableListOf()
        for (validEntry in validEntries) {
            if (!allHistory.contains(validEntry)) {
                newEntries.add(validEntry)
            }
        }
        newHistory = newEntries
        showLogs("List of history (before filtering): [" + newHistory.size + "]", gson.toJson(newHistory))
    }

    private fun showLogs(header: String?, data: String) {
        if (header != null) {
            aapsLogger.debug(LTag.PUMP, header)
        }
        if (StringUtils.isNotBlank(data)) {
            for (token in StringUtil.splitString(data, 3500)) {
                aapsLogger.debug(LTag.PUMP, token)
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "No data.")
        }
    }

    // fun getAllHistory(): List<PumpHistoryEntry?> {
    //     return allHistory
    // }

    fun filterNewEntries() {
        val newHistory2: MutableList<PumpHistoryEntry> = mutableListOf()
        var tbrs: MutableList<PumpHistoryEntry> = mutableListOf()
        val bolusEstimates: MutableList<PumpHistoryEntry> = mutableListOf()
        val atechDate = DateTimeUtil.toATechDate(GregorianCalendar())

        //aapsLogger.debug(LTag.PUMP, "Filter new entries: Before {}", newHistory);
        if (!isCollectionEmpty(newHistory)) {
            for (pumpHistoryEntry in newHistory) {
                if (!allHistory.contains(pumpHistoryEntry)) {
                    val type = pumpHistoryEntry.entryType
                    if (type === PumpHistoryEntryType.TempBasalRate || type === PumpHistoryEntryType.TempBasalDuration) {
                        tbrs.add(pumpHistoryEntry)
                    } else if (type === PumpHistoryEntryType.BolusWizard || type === PumpHistoryEntryType.BolusWizard512) {
                        bolusEstimates.add(pumpHistoryEntry)
                        newHistory2.add(pumpHistoryEntry)
                    } else {
                        if (type === PumpHistoryEntryType.EndResultTotals) {
                            if (!DateTimeUtil.isSameDay(atechDate, pumpHistoryEntry.atechDateTime!!)) {
                                newHistory2.add(pumpHistoryEntry)
                            }
                        } else {
                            newHistory2.add(pumpHistoryEntry)
                        }
                    }
                }
            }
            tbrs = preProcessTBRs(tbrs)
            if (bolusEstimates.size > 0) {
                extendBolusRecords(bolusEstimates, newHistory2)
            }
            newHistory2.addAll(tbrs)
            newHistory = newHistory2
            sort(newHistory)
        }
        aapsLogger.debug(LTag.PUMP, "New History entries found: " + newHistory.size)
        showLogs("List of history (after filtering): [" + newHistory.size + "]", gson.toJson(newHistory))
    }

    private fun extendBolusRecords(bolusEstimates: MutableList<PumpHistoryEntry>, newHistory2: MutableList<PumpHistoryEntry>) {
        val boluses: MutableList<PumpHistoryEntry> = getFilteredItems(newHistory2, PumpHistoryEntryType.Bolus)
        for (bolusEstimate in bolusEstimates) {
            for (bolus in boluses) {
                if (bolusEstimate.atechDateTime == bolus.atechDateTime) {
                    bolus.addDecodedData("Estimate", bolusEstimate.decodedData!!["Object"])
                }
            }
        }
    }

    fun finalizeNewHistoryRecords() {
        if (newHistory.isEmpty()) return
        var pheLast = newHistory[0]

        // find last entry
        for (pumpHistoryEntry in newHistory) {
            if (pumpHistoryEntry.atechDateTime != null && pumpHistoryEntry.isAfter(pheLast.atechDateTime!!)) {
                pheLast = pumpHistoryEntry
            }
        }

        // add new entries
        newHistory.reverse()
        for (pumpHistoryEntry in newHistory) {
            if (!allHistory.contains(pumpHistoryEntry)) {
                lastIdUsed++
                pumpHistoryEntry.id = lastIdUsed
                allHistory.add(pumpHistoryEntry)
            }
        }
        // if (pheLast == null) // if we don't have any valid record we don't do the filtering and setting
        //     return
        //setLastHistoryRecordTime(pheLast.atechDateTime)
        sp.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, pheLast.atechDateTime!!)
        var dt: LocalDateTime? = null
        try {
            dt = DateTimeUtil.toLocalDateTime(pheLast.atechDateTime!!)
        } catch (ex: Exception) {
            aapsLogger.error("Problem decoding date from last record: $pheLast")
        }
        if (dt != null) {
            dt = dt.minusDays(1) // we keep 24 hours
            val dtRemove = DateTimeUtil.toATechDate(dt)
            val removeList: MutableList<PumpHistoryEntry?> = ArrayList()
            for (pumpHistoryEntry in allHistory) {
                if (!pumpHistoryEntry.isAfter(dtRemove)) {
                    removeList.add(pumpHistoryEntry)
                }
            }
            allHistory.removeAll(removeList)
            this.sort(allHistory)
            aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "All History records [afterFilterCount=%d, removedItemsCount=%d, newItemsCount=%d]",
                allHistory.size, removeList.size, newHistory.size))
        } else {
            aapsLogger.error("Since we couldn't determine date, we don't clean full history. This is just workaround.")
        }
        newHistory.clear()
    }

    fun hasRelevantConfigurationChanged(): Boolean {
        return getStateFromFilteredList( //
            setOf(PumpHistoryEntryType.ChangeBasalPattern,  //
                PumpHistoryEntryType.ClearSettings,  //
                PumpHistoryEntryType.SaveSettings,  //
                PumpHistoryEntryType.ChangeMaxBolus,  //
                PumpHistoryEntryType.ChangeMaxBasal,  //
                PumpHistoryEntryType.ChangeTempBasalType))
    }

    private fun isCollectionEmpty(col: List<*>?): Boolean {
        return col == null || col.isEmpty()
    }

    private fun isCollectionNotEmpty(col: List<*>?): Boolean {
        return col != null && !col.isEmpty()
    }////////

    //
    val isPumpSuspended: Boolean
        get() {
            val items = getDataForPumpSuspends()
            showLogs("isPumpSuspended: ", gson.toJson(items))
            return if (isCollectionNotEmpty(items)) {
                val pumpHistoryEntryType = items[0].entryType
                val isSuspended = !(pumpHistoryEntryType === PumpHistoryEntryType.TempBasalCombined || //
                    pumpHistoryEntryType === PumpHistoryEntryType.BasalProfileStart || //
                    pumpHistoryEntryType === PumpHistoryEntryType.Bolus || //
                    pumpHistoryEntryType === PumpHistoryEntryType.ResumePump || //
                    pumpHistoryEntryType === PumpHistoryEntryType.BatteryChange || //
                    pumpHistoryEntryType === PumpHistoryEntryType.Prime)
                aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "isPumpSuspended. Last entry type=%s, isSuspended=%b", pumpHistoryEntryType, isSuspended))
                isSuspended
            } else false
        }

    private fun getDataForPumpSuspends(): MutableList<PumpHistoryEntry> {
            val newAndAll: MutableList<PumpHistoryEntry> = mutableListOf()
            if (isCollectionNotEmpty(allHistory)) {
                newAndAll.addAll(allHistory)
            }
            if (isCollectionNotEmpty(newHistory)) {
                for (pumpHistoryEntry in newHistory!!) {
                    if (!newAndAll.contains(pumpHistoryEntry)) {
                        newAndAll.add(pumpHistoryEntry)
                    }
                }
            }
            if (newAndAll.isEmpty()) return newAndAll
            this.sort(newAndAll)
            var newAndAll2: MutableList<PumpHistoryEntry> = getFilteredItems(newAndAll,  //
                setOf(PumpHistoryEntryType.Bolus,  //
                    PumpHistoryEntryType.TempBasalCombined,  //
                    PumpHistoryEntryType.Prime,  //
                    PumpHistoryEntryType.SuspendPump,  //
                    PumpHistoryEntryType.ResumePump,  //
                    PumpHistoryEntryType.Rewind,  //
                    PumpHistoryEntryType.NoDeliveryAlarm,  //
                    PumpHistoryEntryType.BatteryChange,  //
                    PumpHistoryEntryType.BasalProfileStart))
            newAndAll2 = filterPumpSuspend(newAndAll2, 10)
            return newAndAll2
        }

    private fun filterPumpSuspend(newAndAll: MutableList<PumpHistoryEntry>, filterCount: Int): MutableList<PumpHistoryEntry> {
        if (newAndAll.size <= filterCount) {
            return newAndAll
        }
        val newAndAllOut: MutableList<PumpHistoryEntry> = ArrayList()
        for (i in 0 until filterCount) {
            newAndAllOut.add(newAndAll[i])
        }
        return newAndAllOut
    }

    /**
     * Process History Data: Boluses(Treatments), TDD, TBRs, Suspend-Resume (or other pump stops: battery, prime)
     */
    fun processNewHistoryData() {

        // TODO: Fix db code
        // Prime (for reseting autosense)
        val primeRecords: MutableList<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.Prime)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: Prime [count=%d, items=%s]", primeRecords.size, gson.toJson(primeRecords)))
        if (isCollectionNotEmpty(primeRecords)) {
            try {
                processPrime(primeRecords)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing Prime entries: " + ex.message, ex)
                throw ex
            }
        }

        // Rewind (for marking insulin change)
        val rewindRecords: MutableList<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.Rewind)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: Rewind [count=%d, items=%s]", rewindRecords.size, gson.toJson(rewindRecords)))
        if (isCollectionNotEmpty(rewindRecords)) {
            try {
                processRewind(rewindRecords)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing Rewind entries: " + ex.message, ex)
                throw ex
            }
        }

        // TDD
        val tdds: MutableList<PumpHistoryEntry> = getFilteredItems(setOf(PumpHistoryEntryType.EndResultTotals, tDDType))
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: TDD [count=%d, items=%s]", tdds.size, gson.toJson(tdds)))
        if (isCollectionNotEmpty(tdds)) {
            try {
                processTDDs(tdds)
            } catch (ex: Exception) {
                aapsLogger.error("ProcessHistoryData: Error processing TDD entries: " + ex.message, ex)
                throw ex
            }
        }
        pumpTime = medtronicUtil.pumpTime

        // Bolus
        val treatments = getFilteredItems(PumpHistoryEntryType.Bolus)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: Bolus [count=%d, items=%s]", treatments.size, gson.toJson(treatments)))
        if (treatments.size > 0) {
            try {
                processBolusEntries(treatments)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing Bolus entries: " + ex.message, ex)
                throw ex
            }
        }

        // TBR
        val tbrs: MutableList<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.TempBasalCombined)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: TBRs Processed [count=%d, items=%s]", tbrs.size, gson.toJson(tbrs)))
        if (tbrs.size > 0) {
            try {
                processTBREntries(tbrs)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing TBR entries: " + ex.message, ex)
                throw ex
            }
        }

        // 'Delivery Suspend'
        val suspends: MutableList<TempBasalProcessDTO>
        suspends = try {
            getSuspendRecords()
        } catch (ex: Exception) {
            aapsLogger.error("ProcessHistoryData: Error getting Suspend entries: " + ex.message, ex)
            throw ex
        }
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: 'Delivery Suspend' Processed [count=%d, items=%s]", suspends.size,
            gson.toJson(suspends)))
        if (isCollectionNotEmpty(suspends)) {
            try {
                processSuspends(suspends)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing Suspends entries: " + ex.message, ex)
                throw ex
            }
        }
    }

    private fun processPrime(primeRecords: List<PumpHistoryEntry?>) {
        val maxAllowedTimeInPast = DateTimeUtil.getATDWithAddedMinutes(GregorianCalendar(), -30)
        var lastPrimeRecord = 0L
        for (primeRecord in primeRecords) {
            val fixedAmount = primeRecord!!.getDecodedDataEntry("FixedAmount")
            if (fixedAmount != null && fixedAmount as Float == 0.0f) {
                // non-fixed primes are used to prime the tubing
                // fixed primes are used to prime the cannula
                // so skip the prime entry if it was not a fixed prime
                continue
            }
            if (primeRecord.atechDateTime!! > maxAllowedTimeInPast) {
                if (lastPrimeRecord < primeRecord.atechDateTime!!) {
                    lastPrimeRecord = primeRecord.atechDateTime!!
                }
            }
        }
        if (lastPrimeRecord != 0L) {
            val lastPrimeFromAAPS = sp.getLong(MedtronicConst.Statistics.LastPrime, 0L)
            if (lastPrimeRecord != lastPrimeFromAAPS) {
                uploadCareportalEvent(DateTimeUtil.toMillisFromATD(lastPrimeRecord), DetailedBolusInfo.EventType.CANNULA_CHANGE)
                sp.putLong(MedtronicConst.Statistics.LastPrime, lastPrimeRecord)
            }
        }
    }

    private fun processRewind(rewindRecords: List<PumpHistoryEntry?>) {
        val maxAllowedTimeInPast = DateTimeUtil.getATDWithAddedMinutes(GregorianCalendar(), -30)
        var lastRewindRecord = 0L
        for (rewindRecord in rewindRecords) {
            if (rewindRecord!!.atechDateTime!! > maxAllowedTimeInPast) {
                if (lastRewindRecord < rewindRecord.atechDateTime!!) {
                    lastRewindRecord = rewindRecord.atechDateTime!!
                }
            }
        }
        if (lastRewindRecord != 0L) {
            val lastRewindFromAAPS = sp.getLong(MedtronicConst.Statistics.LastRewind, 0L)
            if (lastRewindRecord != lastRewindFromAAPS) {
                uploadCareportalEvent(DateTimeUtil.toMillisFromATD(lastRewindRecord), DetailedBolusInfo.EventType.INSULIN_CHANGE)
                sp.putLong(MedtronicConst.Statistics.LastRewind, lastRewindRecord)
            }
        }
    }

    private fun uploadCareportalEvent(date: Long, event: DetailedBolusInfo.EventType) {
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, event, null, null,
            medtronicPumpStatus.pumpType, medtronicPumpStatus.serialNumber!!)
    }

    private fun processTDDs(tddsIn: MutableList<PumpHistoryEntry>) {
        val tdds = filterTDDs(tddsIn)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, """
     ${logPrefix}TDDs found: %d.
     %s
     """.trimIndent(), tdds.size, gson.toJson(tdds)))
        val tddsDb = databaseHelper.getTDDsForLastXDays(3)
        for (tdd in tdds) {
            val tddDbEntry = findTDD(tdd.atechDateTime!!, tddsDb)
            val totalsDTO = tdd.decodedData!!["Object"] as DailyTotalsDTO?

            //aapsLogger.debug(LTag.PUMP, "DailyTotals: {}", totalsDTO);
            if (tddDbEntry == null) {
                val tddNew = TDD()
                totalsDTO!!.setTDD(tddNew)
                aapsLogger.debug(LTag.PUMP, "TDD Add: $tddNew")
                databaseHelper.createOrUpdateTDD(tddNew)
            } else {
                if (!totalsDTO!!.doesEqual(tddDbEntry)) {
                    totalsDTO.setTDD(tddDbEntry)
                    aapsLogger.debug(LTag.PUMP, "TDD Edit: $tddDbEntry")
                    databaseHelper.createOrUpdateTDD(tddDbEntry)
                }
            }
        }
    }

    private enum class ProcessHistoryRecord(val description: String) {
        Bolus("Bolus"),
        TBR("TBR"),
        Suspend("Suspend");
    }

    private fun processBolusEntries(entryList: MutableList<PumpHistoryEntry>) {
        val oldestTimestamp = getOldestTimestamp(entryList)
        val entriesFromHistory = getDatabaseEntriesByLastTimestamp(oldestTimestamp, ProcessHistoryRecord.Bolus)
        if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: List (before filter): %s, FromDb=%s", gson.toJson(entryList),
            gsonCore.toJson(entriesFromHistory)))
        filterOutAlreadyAddedEntries(entryList, entriesFromHistory)
        if (entryList.isEmpty()) {
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: EntryList was filtered out.")
            return
        }
        filterOutNonInsulinEntries(entriesFromHistory)
        if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: List (after filter): %s, FromDb=%s", gson.toJson(entryList),
            gsonCore.toJson(entriesFromHistory)))
        if (isCollectionEmpty(entriesFromHistory)) {
            for (treatment in entryList) {
                aapsLogger.debug(LTag.PUMP, "Add Bolus (no db entry): $treatment")
                if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: Add Bolus: FromDb=null, Treatment=$treatment")
                addBolus(treatment, null)
            }
        } else {
            for (treatment in entryList) {
                val treatmentDb = findDbEntry(treatment, entriesFromHistory)
                aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "Add Bolus %s - (entryFromDb=%s) ", treatment, treatmentDb))
                if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: Add Bolus: FromDb=%s, Treatment=%s", treatmentDb, treatment))
                addBolus(treatment, treatmentDb as Treatment?)
            }
        }
    }

    private fun filterOutNonInsulinEntries(entriesFromHistory: MutableList<DbObjectBase>) {
        // when we try to pair PumpHistory with AAPS treatments, we need to ignore all non-insulin entries
        val removeList: MutableList<DbObjectBase> = mutableListOf()
        for (dbObjectBase in entriesFromHistory) {
            val treatment = dbObjectBase as Treatment
            if (Round.isSame(treatment.insulin, 0.0)) {
                removeList.add(dbObjectBase)
            }
        }
        entriesFromHistory.removeAll(removeList)
    }

    private fun processTBREntries(entryList: MutableList<PumpHistoryEntry>) {
        Collections.reverse(entryList)
        val tbr = entryList[0].getDecodedDataEntry("Object") as TempBasalPair?
        var readOldItem = false
        if (tbr!!.isCancelTBR) {
            val oneMoreEntryFromHistory = getOneMoreEntryFromHistory(PumpHistoryEntryType.TempBasalCombined)
            if (oneMoreEntryFromHistory != null) {
                entryList.add(0, oneMoreEntryFromHistory)
                readOldItem = true
            } else {
                entryList.removeAt(0)
            }
        }
        val oldestTimestamp = getOldestTimestamp(entryList)
        val entriesFromHistory = getDatabaseEntriesByLastTimestamp(oldestTimestamp, ProcessHistoryRecord.TBR)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, ProcessHistoryRecord.TBR.description + " List (before filter): %s, FromDb=%s", gson.toJson(entryList),
            gson.toJson(entriesFromHistory)))
        var processDTO: TempBasalProcessDTO? = null
        val processList: MutableList<TempBasalProcessDTO> = ArrayList()
        for (treatment in entryList) {
            val tbr2 = treatment!!.getDecodedDataEntry("Object") as TempBasalPair?
            if (tbr2!!.isCancelTBR) {
                if (processDTO != null) {
                    processDTO.itemTwo = treatment
                    if (readOldItem) {
                        processDTO.processOperation = TempBasalProcessDTO.Operation.Edit
                        readOldItem = false
                    }
                } else {
                    aapsLogger.error("processDTO was null - shouldn't happen. ItemTwo=$treatment")
                }
            } else {
                if (processDTO != null) {
                    processList.add(processDTO)
                }
                processDTO = TempBasalProcessDTO()
                processDTO.itemOne = treatment
                processDTO.processOperation = TempBasalProcessDTO.Operation.Add
            }
        }
        if (processDTO != null) {
            processList.add(processDTO)
        }
        if (isCollectionNotEmpty(processList)) {
            for (tempBasalProcessDTO in processList) {
                if (tempBasalProcessDTO.processOperation === TempBasalProcessDTO.Operation.Edit) {
                    // edit
                    val tempBasal = findTempBasalWithPumpId(tempBasalProcessDTO.itemOne!!.pumpId!!, entriesFromHistory)
                    if (tempBasal != null) {
                        tempBasal.durationInMinutes = tempBasalProcessDTO.duration
                        databaseHelper.createOrUpdate(tempBasal)
                        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "Edit " + ProcessHistoryRecord.TBR.description + " - (entryFromDb=%s) ", tempBasal))
                    } else {
                        aapsLogger.error(LTag.PUMP, "TempBasal not found. Item: " + tempBasalProcessDTO.itemOne)
                    }
                } else {
                    // add
                    val treatment = tempBasalProcessDTO.itemOne
                    val tbr2 = treatment!!.decodedData!!["Object"] as TempBasalPair?
                    tbr2!!.durationMinutes = tempBasalProcessDTO.duration
                    val tempBasal = findTempBasalWithPumpId(tempBasalProcessDTO.itemOne!!.pumpId!!, entriesFromHistory)
                    if (tempBasal == null) {
                        val treatmentDb = findDbEntry(treatment, entriesFromHistory)
                        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "Add " + ProcessHistoryRecord.TBR.description + " %s - (entryFromDb=%s) ", treatment, treatmentDb))
                        addTBR(treatment, treatmentDb as TemporaryBasal?)
                    } else {
                        // this shouldn't happen
                        if (tempBasal.durationInMinutes != tempBasalProcessDTO.duration) {
                            aapsLogger.debug(LTag.PUMP, "Found entry with wrong duration (shouldn't happen)... updating")
                            tempBasal.durationInMinutes = tempBasalProcessDTO.duration
                        }
                    }
                } // if
            } // for
        } // collection
    }

    private fun findTempBasalWithPumpId(pumpId: Long, entriesFromHistory: List<DbObjectBase>): TemporaryBasal? {
        for (dbObjectBase in entriesFromHistory) {
            val tbr = dbObjectBase as TemporaryBasal
            if (tbr.pumpId == pumpId) {
                return tbr
            }
        }
        return databaseHelper.findTempBasalByPumpId(pumpId)
    }

    /**
     * findDbEntry - finds Db entries in database, while theoretically this should have same dateTime they
     * don't. Entry on pump is few seconds before treatment in AAPS, and on manual boluses on pump there
     * is no treatment at all. For now we look fro tratment that was from 0s - 1m59s within pump entry.
     *
     * @param treatment          Pump Entry
     * @param entriesFromHistory entries from history
     * @return DbObject from AAPS (if found)
     */
    private fun findDbEntry(treatment: PumpHistoryEntry?, entriesFromHistory: List<DbObjectBase>): DbObjectBase? {
        val proposedTime = DateTimeUtil.toMillisFromATD(treatment!!.atechDateTime!!)

        //proposedTime += (this.pumpTime.timeDifference * 1000);
        if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: findDbEntry Treatment=%s, FromDb=%s", treatment, gson.toJson(entriesFromHistory)))
        if (entriesFromHistory.size == 0) {
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: findDbEntry Treatment=%s, FromDb=null", treatment))
            return null
        } else if (entriesFromHistory.size == 1) {
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: findDbEntry Treatment=%s, FromDb=%s. Type=SingleEntry", treatment, entriesFromHistory[0]))

            // TODO: Fix db code
            // if difference is bigger than 2 minutes we discard entry
            val maxMillisAllowed = DateTimeUtil.getMillisFromATDWithAddedMinutes(treatment.atechDateTime!!, 2)
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: findDbEntry maxMillisAllowed=%d, AtechDateTime=%d (add 2 minutes). ", maxMillisAllowed, treatment.atechDateTime))
            if (entriesFromHistory[0].getDate() > maxMillisAllowed) {
                if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry entry filtered out, returning null. ")
                return null
            }
            return entriesFromHistory[0]
        }
        var min = 0
        while (min < 2) {
            var sec = 0
            while (sec <= 50) {
                if (min == 1 && sec == 50) {
                    sec = 59
                }
                val diff = sec * 1000
                val outList: MutableList<DbObjectBase> = ArrayList()
                for (treatment1 in entriesFromHistory) {
                    if (treatment1.getDate() > proposedTime - diff && treatment1.getDate() < proposedTime + diff) {
                        outList.add(treatment1)
                    }
                }
                if (outList.size == 1) {
                    if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}. Type=EntrySelected, AtTimeMin={}, AtTimeSec={}", treatment, entriesFromHistory[0], min, sec))
                    return outList[0]
                }
                if (min == 0 && sec == 10 && outList.size > 1) {
                    aapsLogger.error(String.format(Locale.ENGLISH, "Too many entries (with too small diff): (timeDiff=[min=%d,sec=%d],count=%d,list=%s)",
                        min, sec, outList.size, gson.toJson(outList)))
                    if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: findDbEntry Error - Too many entries (with too small diff): (timeDiff=[min=%d,sec=%d],count=%d,list=%s)",
                        min, sec, outList.size, gson.toJson(outList)))
                }
                sec += 10
            }
            min += 1
        }
        return null
    }

    private fun getDatabaseEntriesByLastTimestamp(startTimestamp: Long, processHistoryRecord: ProcessHistoryRecord): MutableList<DbObjectBase> {
        var outList: MutableList<DbObjectBase> = mutableListOf()

        if (processHistoryRecord == ProcessHistoryRecord.Bolus) {
            // TODO pumpSync
            outList.addAll(activePlugin.activeTreatments.getTreatmentsFromHistoryAfterTimestamp(startTimestamp))
        } else {
            // TODO pumpSync
            outList.addAll(databaseHelper.getTemporaryBasalsDataFromTime(startTimestamp, true))
        }

        return outList
    }

    private fun filterOutAlreadyAddedEntries(entryList: MutableList<PumpHistoryEntry>, treatmentsFromHistory: MutableList<DbObjectBase>) {
        if (isCollectionEmpty(treatmentsFromHistory))
            return

        val removeTreatmentsFromHistory: MutableList<DbObjectBase> = ArrayList()
        val removeTreatmentsFromPH: MutableList<PumpHistoryEntry> = ArrayList()

        for (treatment in treatmentsFromHistory) {
            if (treatment.getPumpId() != 0L) {
                var selectedBolus: PumpHistoryEntry? = null
                for (bolus in entryList) {
                    if (bolus.pumpId == treatment.getPumpId()) {
                        selectedBolus = bolus
                        break
                    }
                }
                if (selectedBolus != null) {
                    entryList.remove(selectedBolus)
                    removeTreatmentsFromPH.add(selectedBolus)
                    removeTreatmentsFromHistory.add(treatment)
                }
            }
        }
        if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: filterOutAlreadyAddedEntries: PumpHistory=%s, Treatments=%s",
            gson.toJson(removeTreatmentsFromPH),
            gsonCore.toJson(removeTreatmentsFromHistory)))
        treatmentsFromHistory.removeAll(removeTreatmentsFromHistory)
    }

    private fun addBolus(bolus: PumpHistoryEntry?, treatment: Treatment?) {
        val bolusDTO = bolus!!.decodedData!!["Object"] as BolusDTO?
        if (treatment == null) {
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): Bolus=$bolusDTO")
            when (bolusDTO!!.bolusType) {
                PumpBolusType.Normal                        -> {
                    val detailedBolusInfo = DetailedBolusInfo()
                    detailedBolusInfo.bolusTimestamp = tryToGetByLocalTime(bolus.atechDateTime!!)
                    detailedBolusInfo.pumpType = medtronicPumpStatus.pumpType
                    detailedBolusInfo.pumpSerial = medtronicPumpStatus.serialNumber
                    detailedBolusInfo.bolusPumpId = bolus.pumpId
                    detailedBolusInfo.insulin = bolusDTO.deliveredAmount!!
                    addCarbsFromEstimate(detailedBolusInfo, bolus)
                    if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): DetailedBolusInfo=$detailedBolusInfo")
                    // TODO pumpSync
                    val newRecord = activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                    bolus.linkedObject = detailedBolusInfo
                    aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "addBolus - [date=%d,pumpId=%d, insulin=%.2f, newRecord=%b]", detailedBolusInfo.timestamp,
                        detailedBolusInfo.bolusPumpId, detailedBolusInfo.insulin, newRecord))
                }

                PumpBolusType.Audio, PumpBolusType.Extended -> {
                    val extendedBolus = ExtendedBolus(injector)
                    extendedBolus.date = tryToGetByLocalTime(bolus.atechDateTime!!)
                    extendedBolus.source = Source.PUMP
                    extendedBolus.insulin = bolusDTO.deliveredAmount!!
                    extendedBolus.pumpId = bolus.pumpId!!
                    extendedBolus.isValid = true
                    extendedBolus.durationInMinutes = bolusDTO.duration!!
                    bolus.linkedObject = extendedBolus
                    if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): ExtendedBolus=$extendedBolus")
                    // TODO pumpSync
                    activePlugin.activeTreatments.addToHistoryExtendedBolus(extendedBolus)
                    aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "addBolus - Extended [date=%d,pumpId=%d, insulin=%.3f, duration=%d]", extendedBolus.date,
                        extendedBolus.pumpId, extendedBolus.insulin, extendedBolus.durationInMinutes))
                }
            }
        } else {
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: addBolus(OldTreatment=%s): Bolus=%s", treatment, bolusDTO))
            treatment.source = Source.PUMP
            treatment.pumpId = bolus.pumpId!!
            treatment.insulin = bolusDTO!!.deliveredAmount!!
            // TODO pumpSync
            val updateReturn = activePlugin.activeTreatments.createOrUpdateMedtronic(treatment, false)
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: addBolus(tretament!=null): NewTreatment=%s, UpdateReturn=%s", treatment, updateReturn))
            aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "editBolus - [date=%d,pumpId=%d, insulin=%.3f, newRecord=%s]", treatment.date,
                treatment.pumpId, treatment.insulin, updateReturn.toString()))
            bolus.linkedObject = treatment
        }
    }

    private fun addCarbsFromEstimate(detailedBolusInfo: DetailedBolusInfo, bolus: PumpHistoryEntry?) {
        if (bolus!!.containsDecodedData("Estimate")) {
            val bolusWizard = bolus.decodedData!!["Estimate"] as BolusWizardDTO?
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: addCarbsFromEstimate: Bolus=%s, BolusWizardDTO=%s", bolus, bolusWizard))
            detailedBolusInfo.carbs = bolusWizard!!.carbs.toDouble()
        }
    }

    private fun addTBR(treatment: PumpHistoryEntry?, temporaryBasalDbInput: TemporaryBasal?) {
        val tbr = treatment!!.decodedData!!["Object"] as TempBasalPair?
        var temporaryBasalDb = temporaryBasalDbInput
        var operation = "editTBR"
        if (temporaryBasalDb == null) {
            temporaryBasalDb = TemporaryBasal(injector)
            temporaryBasalDb.date = tryToGetByLocalTime(treatment.atechDateTime!!)
            operation = "addTBR"
        }
        temporaryBasalDb.source = Source.PUMP
        temporaryBasalDb.pumpId = treatment.pumpId!!
        temporaryBasalDb.durationInMinutes = tbr!!.durationMinutes
        temporaryBasalDb.absoluteRate = tbr.insulinRate
        temporaryBasalDb.isAbsolute = !tbr.isPercent
        treatment.linkedObject = temporaryBasalDb
        databaseHelper.createOrUpdate(temporaryBasalDb)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "$operation - [date=%d,pumpId=%d, rate=%s %s, duration=%d]",  //
            temporaryBasalDb.getDate(),  //
            temporaryBasalDb.getPumpId(),  //
            if (temporaryBasalDb.isAbsolute) String.format(Locale.ENGLISH, "%.2f", temporaryBasalDb.absoluteRate) else String.format(Locale.ENGLISH, "%d", temporaryBasalDb.percentRate),  //
            if (temporaryBasalDb.isAbsolute) "U/h" else "%",  //
            temporaryBasalDb.durationInMinutes))
    }

    private fun processSuspends(tempBasalProcessList: List<TempBasalProcessDTO>) {
        for (tempBasalProcess in tempBasalProcessList) {
            var tempBasal = databaseHelper.findTempBasalByPumpId(tempBasalProcess.itemOne!!.pumpId!!)
            if (tempBasal == null) {
                // add
                tempBasal = TemporaryBasal(injector)
                tempBasal.date = tryToGetByLocalTime(tempBasalProcess.itemOne!!.atechDateTime!!)
                tempBasal.source = Source.PUMP
                tempBasal.pumpId = tempBasalProcess.itemOne!!.pumpId!!
                tempBasal.durationInMinutes = tempBasalProcess.duration
                tempBasal.absoluteRate = 0.0
                tempBasal.isAbsolute = true
                tempBasalProcess.itemOne!!.linkedObject = tempBasal
                tempBasalProcess.itemTwo!!.linkedObject = tempBasal
                databaseHelper.createOrUpdate(tempBasal)
            }
        }
    }

    // suspend/resume
    // no_delivery/prime & rewind/prime
    private fun getSuspendRecords(): MutableList<TempBasalProcessDTO>  {
            val outList: MutableList<TempBasalProcessDTO> = ArrayList()

            // suspend/resume
            outList.addAll(getSuspendResumeRecordsList())
            // no_delivery/prime & rewind/prime
            outList.addAll(getNoDeliveryRewindPrimeRecordsList())
            return outList
        }// remove last and have paired items// remove last (unpaired R)// get one more from history (R S R) -> ([S] R S R)// remove last (unpaired R)// not full suspends, need to retrive one more record and discard first one (R S R S) -> ([S] R S R [xS])// full resume suspends (S R S R)

    //
    //
    private fun getSuspendResumeRecordsList(): List<TempBasalProcessDTO> {
            val filteredItems = getFilteredItems(newHistory,  //
                setOf(PumpHistoryEntryType.SuspendPump, PumpHistoryEntryType.ResumePump))
            val outList: MutableList<TempBasalProcessDTO> = mutableListOf()
            if (filteredItems.size > 0) {
                val filtered2Items: MutableList<PumpHistoryEntry> = mutableListOf()
                if (filteredItems.size % 2 == 0 && filteredItems[0].entryType === PumpHistoryEntryType.ResumePump) {
                    // full resume suspends (S R S R)
                    filtered2Items.addAll(filteredItems)
                } else if (filteredItems.size % 2 == 0 && filteredItems[0].entryType === PumpHistoryEntryType.SuspendPump) {
                    // not full suspends, need to retrive one more record and discard first one (R S R S) -> ([S] R S R [xS])
                    filteredItems.removeAt(0)
                    val oneMoreEntryFromHistory = getOneMoreEntryFromHistory(PumpHistoryEntryType.SuspendPump)
                    if (oneMoreEntryFromHistory != null) {
                        filteredItems.add(oneMoreEntryFromHistory)
                    } else {
                        filteredItems.removeAt(filteredItems.size - 1) // remove last (unpaired R)
                    }
                    filtered2Items.addAll(filteredItems)
                } else {
                    if (filteredItems[0].entryType === PumpHistoryEntryType.ResumePump) {
                        // get one more from history (R S R) -> ([S] R S R)
                        val oneMoreEntryFromHistory = getOneMoreEntryFromHistory(PumpHistoryEntryType.SuspendPump)
                        if (oneMoreEntryFromHistory != null) {
                            filteredItems.add(oneMoreEntryFromHistory)
                        } else {
                            filteredItems.removeAt(filteredItems.size - 1) // remove last (unpaired R)
                        }
                        filtered2Items.addAll(filteredItems)
                    } else {
                        // remove last and have paired items
                        filteredItems.removeAt(0)
                        filtered2Items.addAll(filteredItems)
                    }
                }
                if (filtered2Items.size > 0) {
                    sort(filtered2Items)
                    Collections.reverse(filtered2Items)
                    var i = 0
                    while (i < filtered2Items.size) {
                        val dto = TempBasalProcessDTO()
                        dto.itemOne = filtered2Items[i]
                        dto.itemTwo = filtered2Items[i + 1]
                        dto.processOperation = TempBasalProcessDTO.Operation.Add
                        outList.add(dto)
                        i += 2
                    }
                }
            }
            return outList
        }//////////

    //
    private fun getNoDeliveryRewindPrimeRecordsList(): List<TempBasalProcessDTO>  {
        val primeItems: MutableList<PumpHistoryEntry> = getFilteredItems(newHistory,  //
            setOf(PumpHistoryEntryType.Prime))
        val outList: MutableList<TempBasalProcessDTO> = ArrayList()
        if (primeItems.size == 0) return outList
        val filteredItems: MutableList<PumpHistoryEntry> = getFilteredItems(newHistory,  //
            setOf(PumpHistoryEntryType.Prime,
            PumpHistoryEntryType.Rewind,
            PumpHistoryEntryType.NoDeliveryAlarm,
            PumpHistoryEntryType.Bolus,
            PumpHistoryEntryType.TempBasalCombined)
        )
        val tempData: MutableList<PumpHistoryEntry> = mutableListOf()
        var startedItems = false
        var finishedItems = false
        for (filteredItem in filteredItems) {
            if (filteredItem.entryType === PumpHistoryEntryType.Prime) {
                startedItems = true
            }
            if (startedItems) {
                if (filteredItem.entryType === PumpHistoryEntryType.Bolus ||
                    filteredItem.entryType === PumpHistoryEntryType.TempBasalCombined) {
                    finishedItems = true
                    break
                }
                tempData.add(filteredItem)
            }
        }
        if (!finishedItems) {
            val filteredItemsOld: MutableList<PumpHistoryEntry> = getFilteredItems(allHistory,  //
                setOf(PumpHistoryEntryType.Rewind,
                    PumpHistoryEntryType.NoDeliveryAlarm,
                    PumpHistoryEntryType.Bolus,
                    PumpHistoryEntryType.TempBasalCombined)
            )
            for (filteredItem in filteredItemsOld) {
                if (filteredItem.entryType === PumpHistoryEntryType.Bolus ||
                    filteredItem.entryType === PumpHistoryEntryType.TempBasalCombined) {
                    finishedItems = true
                    break
                }
                tempData.add(filteredItem)
            }
        }
        if (!finishedItems) {
            showLogs("NoDeliveryRewindPrimeRecords: Not finished Items: ", gson.toJson(tempData))
            return outList
        }
        showLogs("NoDeliveryRewindPrimeRecords: Records to evaluate: ", gson.toJson(tempData))
        var items: MutableList<PumpHistoryEntry> = getFilteredItems(tempData, PumpHistoryEntryType.Prime)
        val processDTO = TempBasalProcessDTO()
        processDTO.itemTwo = items[0]
        items = getFilteredItems(tempData, PumpHistoryEntryType.NoDeliveryAlarm)
        if (items.size > 0) {
            processDTO.itemOne = items[items.size - 1]
            processDTO.processOperation = TempBasalProcessDTO.Operation.Add
            outList.add(processDTO)
            return outList
        }
        items = getFilteredItems(tempData, PumpHistoryEntryType.Rewind)
        if (items.size > 0) {
            processDTO.itemOne = items[0]
            processDTO.processOperation = TempBasalProcessDTO.Operation.Add
            outList.add(processDTO)
            return outList
        }
        return outList
    }

    private fun getOneMoreEntryFromHistory(entryType: PumpHistoryEntryType): PumpHistoryEntry? {
        val filteredItems: List<PumpHistoryEntry?> = getFilteredItems(allHistory, entryType)
        return if (filteredItems.size == 0) null else filteredItems[0]
    }

    private fun filterTDDs(tdds: MutableList<PumpHistoryEntry>): MutableList<PumpHistoryEntry> {
        val tddsOut: MutableList<PumpHistoryEntry> = mutableListOf()
        for (tdd in tdds) {
            if (tdd.entryType !== PumpHistoryEntryType.EndResultTotals) {
                tddsOut.add(tdd)
            }
        }
        return if (tddsOut.size == 0) tdds else tddsOut
    }

    private fun findTDD(atechDateTime: Long, tddsDb: List<TDD>): TDD? {
        for (tdd in tddsDb) {
            if (DateTimeUtil.isSameDayATDAndMillis(atechDateTime, tdd.date)) {
                return tdd
            }
        }
        return null
    }

    private fun tryToGetByLocalTime(atechDateTime: Long): Long {
        return DateTimeUtil.toMillisFromATD(atechDateTime)
    }

//     private fun getOldestDateDifference(treatments: List<PumpHistoryEntry>): Int {
//         var dt = Long.MAX_VALUE
//         var currentTreatment: PumpHistoryEntry? = null
//         if (isCollectionEmpty(treatments)) {
//             return 8 // default return of 6 (5 for diif on history reading + 2 for max allowed difference) minutes
//         }
//         for (treatment in treatments) {
//             if (treatment.atechDateTime!! < dt) {
//                 dt = treatment.atechDateTime!!
//                 currentTreatment = treatment
//             }
//         }
//         var oldestEntryTime: LocalDateTime
//         try {
//             oldestEntryTime = DateTimeUtil.toLocalDateTime(dt)
//             oldestEntryTime = oldestEntryTime.minusMinutes(3)
//
// //            if (this.pumpTime.timeDifference < 0) {
// //                oldestEntryTime = oldestEntryTime.plusSeconds(this.pumpTime.timeDifference);
// //            }
//         } catch (ex: Exception) {
//             aapsLogger.error("Problem decoding date from last record: $currentTreatment")
//             return 8 // default return of 6 minutes
//         }
//         val now = LocalDateTime()
//         val minutes = Minutes.minutesBetween(oldestEntryTime, now)
//
//         // returns oldest time in history, with calculated time difference between pump and phone, minus 5 minutes
//         aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "Oldest entry: %d, pumpTimeDifference=%d, newDt=%s, currentTime=%s, differenceMin=%d", dt,
//             pumpTime!!.timeDifference, oldestEntryTime, now, minutes.minutes))
//         return minutes.minutes
//     }

    private fun getOldestTimestamp(treatments: List<PumpHistoryEntry?>): Long {
        var dt = Long.MAX_VALUE
        var currentTreatment: PumpHistoryEntry? = null
        for (treatment in treatments) {
            if (treatment!!.atechDateTime!! < dt) {
                dt = treatment.atechDateTime!!
                currentTreatment = treatment
            }
        }
        if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: getOldestTimestamp. Oldest entry found: time=%d, object=%s", dt, currentTreatment))
        return try {
            val oldestEntryTime = DateTimeUtil.toGregorianCalendar(dt)
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: getOldestTimestamp. oldestEntryTime: %s", DateTimeUtil.toString(oldestEntryTime)))
            oldestEntryTime.add(Calendar.MINUTE, -2)
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "DoubleBolusDebug: getOldestTimestamp. oldestEntryTime (-2m): %s, timeInMillis=%d", DateTimeUtil.toString(oldestEntryTime), oldestEntryTime.timeInMillis))
            oldestEntryTime.timeInMillis
        } catch (ex: Exception) {
            aapsLogger.error("Problem decoding date from last record: $currentTreatment")
            8 // default return of 6 minutes
        }
    }

    private val tDDType: PumpHistoryEntryType
        get() = if (medtronicUtil.medtronicPumpModel == null) {
            PumpHistoryEntryType.EndResultTotals
        } else when (medtronicUtil.medtronicPumpModel) {
            MedtronicDeviceType.Medtronic_515, MedtronicDeviceType.Medtronic_715   -> PumpHistoryEntryType.DailyTotals515
            MedtronicDeviceType.Medtronic_522, MedtronicDeviceType.Medtronic_722   -> PumpHistoryEntryType.DailyTotals522
            MedtronicDeviceType.Medtronic_523_Revel,
            MedtronicDeviceType.Medtronic_723_Revel,
            MedtronicDeviceType.Medtronic_554_Veo,
            MedtronicDeviceType.Medtronic_754_Veo                                  -> PumpHistoryEntryType.DailyTotals523
            else                                                                                                                                                             -> {
                PumpHistoryEntryType.EndResultTotals
            }
        }

    fun hasBasalProfileChanged(): Boolean {
        val filteredItems: List<PumpHistoryEntry?> = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile)
        aapsLogger.debug(LTag.PUMP, "hasBasalProfileChanged. Items: " + gson.toJson(filteredItems))
        return filteredItems.size > 0
    }

    fun processLastBasalProfileChange(pumpType: PumpType?, mdtPumpStatus: MedtronicPumpStatus) {
        val filteredItems: List<PumpHistoryEntry?> = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile)
        aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. Items: $filteredItems")
        var newProfile: PumpHistoryEntry? = null
        var lastDate: Long? = null
        if (filteredItems.size == 1) {
            newProfile = filteredItems[0]
        } else if (filteredItems.size > 1) {
            for (filteredItem in filteredItems) {
                if (lastDate == null || lastDate < filteredItem!!.atechDateTime!!) {
                    newProfile = filteredItem
                    lastDate = newProfile!!.atechDateTime
                }
            }
        }
        if (newProfile != null) {
            aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. item found, setting new basalProfileLocally: $newProfile")
            val basalProfile = newProfile.decodedData!!["Object"] as BasalProfile?
            mdtPumpStatus.basalsByHour = basalProfile!!.getProfilesByHour(pumpType!!)
        }
    }

    fun hasPumpTimeChanged(): Boolean {
        return getStateFromFilteredList(setOf(PumpHistoryEntryType.NewTimeSet,  //
            PumpHistoryEntryType.ChangeTime))
    }

    // fun setLastHistoryRecordTime(lastHistoryRecordTime: Long?) {
    //     // this.previousLastHistoryRecordTime = this.lastHistoryRecordTime;
    // }

    fun setIsInInit(init: Boolean) {
        isInit = init
    }

    // HELPER METHODS
    private fun sort(list: MutableList<PumpHistoryEntry>) {
        // if (list != null && !list.isEmpty()) {
        //     Collections.sort(list, PumpHistoryEntry.Comparator())
        // }
        list.sortWith(PumpHistoryEntry.Comparator())
    }

    private fun preProcessTBRs(TBRs_Input: MutableList<PumpHistoryEntry>): MutableList<PumpHistoryEntry> {
        val TBRs: MutableList<PumpHistoryEntry> = mutableListOf()
        val map: MutableMap<String?, PumpHistoryEntry?> = HashMap()
        for (pumpHistoryEntry in TBRs_Input) {
            if (map.containsKey(pumpHistoryEntry.DT)) {
                medtronicPumpHistoryDecoder.decodeTempBasal(map[pumpHistoryEntry.DT]!!, pumpHistoryEntry)
                pumpHistoryEntry.setEntryType(medtronicUtil.medtronicPumpModel!!, PumpHistoryEntryType.TempBasalCombined)
                TBRs.add(pumpHistoryEntry)
                map.remove(pumpHistoryEntry.DT)
            } else {
                map[pumpHistoryEntry.DT] = pumpHistoryEntry
            }
        }
        return TBRs
    }

    private fun getFilteredItems(entryTypes: Set<PumpHistoryEntryType>?): MutableList<PumpHistoryEntry> {
        return getFilteredItems(newHistory, entryTypes)
    }

    private fun getFilteredItems(entryType: PumpHistoryEntryType): MutableList<PumpHistoryEntry> {
        return getFilteredItems(newHistory, setOf(entryType))
    }

    private fun getStateFromFilteredList(entryTypes: Set<PumpHistoryEntryType>?): Boolean {
        return if (isInit) {
            false
        } else {
            val filteredItems: List<PumpHistoryEntry?> = getFilteredItems(entryTypes)
            aapsLogger.debug(LTag.PUMP, "Items: $filteredItems")
            filteredItems.size > 0
        }
    }

    private fun getFilteredItems(inList: MutableList<PumpHistoryEntry>?, entryType: PumpHistoryEntryType): MutableList<PumpHistoryEntry> {
        return getFilteredItems(inList, setOf(entryType))
    }

    private fun getFilteredItems(inList: MutableList<PumpHistoryEntry>?, entryTypes: Set<PumpHistoryEntryType>?): MutableList<PumpHistoryEntry> {

        // aapsLogger.debug(LTag.PUMP, "InList: " + inList.size());
        val outList: MutableList<PumpHistoryEntry> = mutableListOf()
        if (inList != null && inList.size > 0) {
            for (pumpHistoryEntry in inList) {
                if (!isEmpty(entryTypes)) {
                    if (entryTypes!!.contains(pumpHistoryEntry.entryType)) {
                        outList.add(pumpHistoryEntry)
                    }
                } else {
                    outList.add(pumpHistoryEntry)
                }
            }
        }

        // aapsLogger.debug(LTag.PUMP, "OutList: " + outList.size());
        return outList
    }

    private fun isEmpty(entryTypes: Set<PumpHistoryEntryType>?): Boolean {
        return entryTypes.isNullOrEmpty()
        //return entryTypes == null || entryTypes.size == 1 && entryTypes[0] == null
    }

    private val logPrefix: String
        get() = "MedtronicHistoryData::"

    companion object {
        /**
         * Double bolus debug. We seem to have small problem with double Boluses (or sometimes also missing boluses
         * from history. This flag turns on debugging for that (default is off=false)... Debugging is pretty detailed,
         * so log files will get bigger.
         * Note: June 2020. Since this seems to be fixed, I am disabling this per default. I will leave code inside
         * in case we need it again. Code that turns this on is commented out RileyLinkMedtronicService#verifyConfiguration()
         */
        const val doubleBolusDebug = false
    }

    init {
        //allHistory = ArrayList()
        //this.injector = injector
        //this.aapsLogger = aapsLogger
        // this.sp = sp
        // this.activePlugin = activePlugin
        // this.medtronicUtil = medtronicUtil
        // this.medtronicPumpHistoryDecoder = medtronicPumpHistoryDecoder
        // this.medtronicPumpStatus = medtronicPumpStatus
        // databaseHelper = databaseHelperInterface
        //this.pumpSync = pumpSync
    }
}