package app.aaps.pump.medtronic.data

import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.DateTimeUtil
import app.aaps.core.utils.StringUtil
import app.aaps.pump.common.sync.PumpDbEntry
import app.aaps.pump.common.sync.PumpDbEntryBolus
import app.aaps.pump.common.sync.PumpDbEntryCarbs
import app.aaps.pump.common.sync.PumpDbEntryTBR
import app.aaps.pump.common.sync.PumpSyncStorage
import app.aaps.pump.medtronic.R
import app.aaps.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import app.aaps.pump.medtronic.data.dto.BasalProfile
import app.aaps.pump.medtronic.data.dto.BolusDTO
import app.aaps.pump.medtronic.data.dto.BolusWizardDTO
import app.aaps.pump.medtronic.data.dto.ClockDTO
import app.aaps.pump.medtronic.data.dto.DailyTotalsDTO
import app.aaps.pump.medtronic.data.dto.TempBasalPair
import app.aaps.pump.medtronic.data.dto.TempBasalProcessDTO
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.defs.PumpBolusType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.keys.MedtronicLongNonKey
import app.aaps.pump.medtronic.util.MedtronicUtil
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.commons.lang3.StringUtils
import org.joda.time.LocalDateTime
import java.util.GregorianCalendar
import java.util.Locale
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
//
@Singleton
class MedtronicHistoryData @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val medtronicUtil: MedtronicUtil,
    private val medtronicPumpHistoryDecoder: MedtronicPumpHistoryDecoder,
    private val medtronicPumpStatus: MedtronicPumpStatus,
    private val pumpSync: PumpSync,
    private val pumpSyncStorage: PumpSyncStorage,
    private val uiInteraction: UiInteraction,
    private val profileUtil: ProfileUtil
) {

    val allHistory: MutableList<PumpHistoryEntry> = mutableListOf()
    private var allPumpIds: MutableSet<Long> = mutableSetOf()
    private var newHistory: MutableList<PumpHistoryEntry> = mutableListOf()
    private var isInit = false

    private var pumpTime: ClockDTO? = null
    private var lastIdUsed: Long = 0
    private var gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    /**
     * Add New History entries
     *
     * @param result PumpHistoryResult instance
     */
    fun addNewHistory(result: PumpHistoryResult) {
        val validEntries: List<PumpHistoryEntry> = result.validEntries
        val newEntries: MutableList<PumpHistoryEntry> = mutableListOf()
        for (validEntry in validEntries) {
            if (!allPumpIds.contains(validEntry.pumpId)) {
                newEntries.add(validEntry)
            } else {
                val entryByPumpId = getEntryByPumpId(validEntry.pumpId)

                if (entryByPumpId != null && entryByPumpId.hasBolusChanged(validEntry)) {
                    newEntries.add(validEntry)
                    allHistory.remove(entryByPumpId)
                    allPumpIds.remove(validEntry.pumpId)
                }
            }
        }
        newHistory = newEntries
        showLogs("List of history (before filtering): [" + newHistory.size + "]", gson.toJson(newHistory))
    }

    private fun getEntryByPumpId(pumpId: Long): PumpHistoryEntry? {
        val findFirst = this.allHistory.stream()
            .filter { f -> f.pumpId == pumpId }
            .findFirst()

        return if (findFirst.isPresent) findFirst.get() else null
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

    fun filterNewEntries() {
        val newHistory2: MutableList<PumpHistoryEntry> = mutableListOf()
        var tbrs: MutableList<PumpHistoryEntry> = mutableListOf()
        val bolusEstimates: MutableList<PumpHistoryEntry> = mutableListOf()
        val aTechDate = DateTimeUtil.toATechDate(GregorianCalendar())

        if (!isCollectionEmpty(newHistory)) {
            for (pumpHistoryEntry in newHistory) {
                if (!allPumpIds.contains(pumpHistoryEntry.pumpId)) {
                    val type = pumpHistoryEntry.entryType
                    if (type === PumpHistoryEntryType.TempBasalRate || type === PumpHistoryEntryType.TempBasalDuration) {
                        tbrs.add(pumpHistoryEntry)
                    } else if (type === PumpHistoryEntryType.BolusWizard || type === PumpHistoryEntryType.BolusWizard512) {
                        bolusEstimates.add(pumpHistoryEntry)
                        newHistory2.add(pumpHistoryEntry)
                    } else {
                        if (type === PumpHistoryEntryType.EndResultTotals) {
                            if (!DateTimeUtil.isSameDay(aTechDate, pumpHistoryEntry.atechDateTime)) {
                                newHistory2.add(pumpHistoryEntry)
                            }
                        } else {
                            newHistory2.add(pumpHistoryEntry)
                        }
                    }
                }
            }
            tbrs = preProcessTBRs(tbrs)
            if (bolusEstimates.isNotEmpty()) {
                extendBolusRecords(bolusEstimates, newHistory2)
            }
            newHistory2.addAll(tbrs)

            val newHistory3: MutableList<PumpHistoryEntry> = mutableListOf()

            for (pumpHistoryEntry in newHistory2) {
                if (!allPumpIds.contains(pumpHistoryEntry.pumpId)) {
                    newHistory3.add(pumpHistoryEntry)
                }
            }

            newHistory = newHistory3
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
                    bolus.addDecodedData("Estimate", bolusEstimate.decodedData.getValue("Object"))
                }
            }
        }
    }

    fun finalizeNewHistoryRecords() {
        if (newHistory.isEmpty()) return
        var pheLast = newHistory[0]

        // find last entry
        for (pumpHistoryEntry in newHistory) {
            if (pumpHistoryEntry.atechDateTime != 0L && pumpHistoryEntry.isAfter(pheLast.atechDateTime)) {
                pheLast = pumpHistoryEntry
            }
        }

        // add new entries
        newHistory.reverse()
        for (pumpHistoryEntry in newHistory) {
            if (!allPumpIds.contains(pumpHistoryEntry.pumpId)) {
                lastIdUsed++
                pumpHistoryEntry.id = lastIdUsed
                allHistory.add(pumpHistoryEntry)
                allPumpIds.add(pumpHistoryEntry.pumpId)
            }
        }

        preferences.put(MedtronicLongNonKey.LastPumpHistoryEntry, pheLast.atechDateTime)
        var dt: LocalDateTime? = null
        try {
            dt = DateTimeUtil.toLocalDateTime(pheLast.atechDateTime)
        } catch (_: Exception) {
            aapsLogger.error("Problem decoding date from last record: $pheLast")
        }
        if (dt != null) {
            dt = dt.minusDays(1) // we keep 24 hours
            val dtRemove = DateTimeUtil.toATechDate(dt)
            val removeList: MutableList<PumpHistoryEntry?> = ArrayList()
            for (pumpHistoryEntry in allHistory) {
                if (!pumpHistoryEntry.isAfter(dtRemove)) {
                    removeList.add(pumpHistoryEntry)
                    allPumpIds.remove(pumpHistoryEntry.pumpId)
                }
            }
            allHistory.removeAll(removeList.toSet())
            this.sort(allHistory)
            aapsLogger.debug(
                LTag.PUMP, String.format(
                    Locale.ENGLISH, "All History records [afterFilterCount=%d, removedItemsCount=%d, newItemsCount=%d]",
                    allHistory.size, removeList.size, newHistory.size
                )
            )
        } else {
            aapsLogger.error("Since we couldn't determine date, we don't clean full history. This is just workaround.")
        }
        newHistory.clear()
    }

    fun hasRelevantConfigurationChanged(): Boolean {
        return getStateFromFilteredList( //
            setOf(
                PumpHistoryEntryType.ChangeBasalPattern,  //
                PumpHistoryEntryType.ClearSettings,  //
                PumpHistoryEntryType.SaveSettings,  //
                PumpHistoryEntryType.ChangeMaxBolus,  //
                PumpHistoryEntryType.ChangeMaxBasal,  //
                PumpHistoryEntryType.ChangeTempBasalType
            )
        )
    }

    private fun isCollectionEmpty(col: List<*>?): Boolean {
        return col == null || col.isEmpty()
    }

    private fun isCollectionNotEmpty(col: List<*>?): Boolean {
        return col != null && col.isNotEmpty()
    }

    fun isPumpSuspended(): Boolean {
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
            for (pumpHistoryEntry in newHistory) {
                if (!newAndAll.contains(pumpHistoryEntry)) {
                    newAndAll.add(pumpHistoryEntry)
                }
            }
        }
        if (newAndAll.isEmpty()) return newAndAll
        this.sort(newAndAll)
        var newAndAll2: MutableList<PumpHistoryEntry> = getFilteredItems(
            newAndAll,  //
            setOf(
                PumpHistoryEntryType.Bolus,  //
                PumpHistoryEntryType.TempBasalCombined,  //
                PumpHistoryEntryType.Prime,  //
                PumpHistoryEntryType.SuspendPump,  //
                PumpHistoryEntryType.ResumePump,  //
                PumpHistoryEntryType.Rewind,  //
                PumpHistoryEntryType.NoDeliveryAlarm,  //
                PumpHistoryEntryType.BatteryChange,  //
                PumpHistoryEntryType.BasalProfileStart
            )
        )
        newAndAll2 = filterPumpSuspend(newAndAll2, 10)
        return newAndAll2
    }

    @Suppress("SameParameterValue")
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
        // Finger BG (for adding entry to careportal)
        val bgRecords: MutableList<PumpHistoryEntry> = getFilteredItems(setOf(PumpHistoryEntryType.BGReceived, PumpHistoryEntryType.BGReceived512))
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: BGReceived [count=%d, items=%s]", bgRecords.size, gson.toJson(bgRecords)))
        if (isCollectionNotEmpty(bgRecords)) {
            try {
                processBgReceived(bgRecords)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing BGReceived entries: " + ex.message, ex)
                throw ex
            }
        }

        // Prime (for resetting autosense)
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

        // BatteryChange
        val batteryChangeRecords: MutableList<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.BatteryChange)
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: BatteryChange [count=%d, items=%s]", batteryChangeRecords.size, gson.toJson(batteryChangeRecords)))
        if (isCollectionNotEmpty(batteryChangeRecords)) {
            try {
                processBatteryChange(batteryChangeRecords)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing BatteryChange entries: " + ex.message, ex)
                throw ex
            }
        }

        // TDD
        val tdds: MutableList<PumpHistoryEntry> = getFilteredItems(setOf(PumpHistoryEntryType.EndResultTotals, getTDDType()))
        aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "ProcessHistoryData: TDD [count=%d, items=%s]", tdds.size, gson.toJson(tdds)))
        if (tdds.isNotEmpty()) {
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
        if (treatments.isNotEmpty()) {
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
        if (tbrs.isNotEmpty()) {
            try {
                processTBREntries(tbrs)
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing TBR entries: " + ex.message, ex)
                throw ex
            }
        }

        // 'Delivery Suspend'
        val suspends: MutableList<TempBasalProcessDTO> = try {
            getSuspendRecords()
        } catch (ex: Exception) {
            aapsLogger.error("ProcessHistoryData: Error getting Suspend entries: " + ex.message, ex)
            throw ex
        }
        aapsLogger.debug(
            LTag.PUMP, String.format(
                Locale.ENGLISH, "ProcessHistoryData: 'Delivery Suspend' Processed [count=%d, items=%s]", suspends.size,
                gson.toJson(suspends)
            )
        )
        if (suspends.isNotEmpty()) {
            try {
                processSuspends(suspends)  // TODO not tested yet
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMP, "ProcessHistoryData: Error processing Suspends entries: " + ex.message, ex)
                throw ex
            }
        }
    }

    fun processBgReceived(bgRecords: List<PumpHistoryEntry>) {
        for (bgRecord in bgRecords) {
            val glucoseMgdl = bgRecord.getDecodedDataEntry("GlucoseMgdl")
            if (glucoseMgdl == null || glucoseMgdl as Int == 0) {
                continue
            }

            val glucose = profileUtil.fromMgdlToUnits(glucoseMgdl.toDouble())
            val glucoseUnit = profileUtil.units

            val result = pumpSync.insertFingerBgIfNewWithTimestamp(
                DateTimeUtil.toMillisFromATD(bgRecord.atechDateTime),
                glucose, glucoseUnit, null,
                bgRecord.pumpId,
                medtronicPumpStatus.pumpType,
                medtronicPumpStatus.serialNumber
            )

            aapsLogger.debug(
                LTag.PUMP, String.format(
                    Locale.ROOT, "insertFingerBgIfNewWithTimestamp [date=%d, glucose=%f, glucoseUnit=%s, pumpId=%d, pumpSerial=%s] - Result: %b",
                    bgRecord.atechDateTime, glucose, glucoseUnit, bgRecord.pumpId,
                    medtronicPumpStatus.serialNumber, result
                )
            )
        }
    }

    private fun processPrime(primeRecords: List<PumpHistoryEntry>) {
        val maxAllowedTimeInPast = DateTimeUtil.getATDWithAddedMinutes(GregorianCalendar(), -30)
        var lastPrimeRecordTime = 0L
        var lastPrimeRecord: PumpHistoryEntry? = null
        for (primeRecord in primeRecords) {
            val fixedAmount = primeRecord.getDecodedDataEntry("FixedAmount")
            if (fixedAmount != null && fixedAmount as Float == 0.0f) {
                // non-fixed primes are used to prime the tubing
                // fixed primes are used to prime the cannula
                // so skip the prime entry if it was not a fixed prime
                continue
            }
            if (primeRecord.atechDateTime > maxAllowedTimeInPast) {
                if (lastPrimeRecordTime < primeRecord.atechDateTime) {
                    lastPrimeRecordTime = primeRecord.atechDateTime
                    lastPrimeRecord = primeRecord
                }
            }
        }
        if (lastPrimeRecord != null) {
            uploadCareportalEventIfFoundInHistory(
                lastPrimeRecord,
                MedtronicLongNonKey.LastPrime,
                TE.Type.CANNULA_CHANGE
            )
        }
    }

    private fun processRewind(rewindRecords: List<PumpHistoryEntry>) {
        val maxAllowedTimeInPast = DateTimeUtil.getATDWithAddedMinutes(GregorianCalendar(), -30)
        var lastRewindRecordTime = 0L
        var lastRewindRecord: PumpHistoryEntry? = null
        for (rewindRecord in rewindRecords) {
            if (rewindRecord.atechDateTime > maxAllowedTimeInPast) {
                if (lastRewindRecordTime < rewindRecord.atechDateTime) {
                    lastRewindRecordTime = rewindRecord.atechDateTime
                    lastRewindRecord = rewindRecord
                }
            }
        }
        if (lastRewindRecord != null) {
            uploadCareportalEventIfFoundInHistory(
                lastRewindRecord,
                MedtronicLongNonKey.LastRewind,
                TE.Type.INSULIN_CHANGE
            )
        }
    }

    private fun processBatteryChange(batteryChangeRecords: List<PumpHistoryEntry>) {
        val maxAllowedTimeInPast = DateTimeUtil.getATDWithAddedMinutes(GregorianCalendar(), -120)
        var lastBatteryChangeRecordTime = 0L
        var lastBatteryChangeRecord: PumpHistoryEntry? = null
        for (batteryChangeRecord in batteryChangeRecords) {
            val isRemoved = batteryChangeRecord.getDecodedDataEntry("isRemoved")

            if (isRemoved != null && isRemoved as Boolean) {
                // we're interested in battery replacements, not battery removals
                continue
            }

            if (batteryChangeRecord.atechDateTime > maxAllowedTimeInPast) {
                if (lastBatteryChangeRecordTime < batteryChangeRecord.atechDateTime) {
                    lastBatteryChangeRecordTime = batteryChangeRecord.atechDateTime
                    lastBatteryChangeRecord = batteryChangeRecord
                }
            }
        }
        if (lastBatteryChangeRecord != null) {
            uploadCareportalEventIfFoundInHistory(
                lastBatteryChangeRecord,
                MedtronicLongNonKey.LastBatteryChange,
                TE.Type.PUMP_BATTERY_CHANGE
            )
        }
    }

    private fun uploadCareportalEventIfFoundInHistory(historyRecord: PumpHistoryEntry, eventSP: LongNonPreferenceKey, eventType: TE.Type) {
        val lastPrimeFromAAPS = preferences.get(eventSP)
        if (historyRecord.atechDateTime != lastPrimeFromAAPS) {
            val result = pumpSync.insertTherapyEventIfNewWithTimestamp(
                DateTimeUtil.toMillisFromATD(historyRecord.atechDateTime),
                eventType, null,
                historyRecord.pumpId,
                medtronicPumpStatus.pumpType,
                medtronicPumpStatus.serialNumber
            )

            aapsLogger.debug(
                LTag.PUMP, String.format(
                    Locale.ROOT, "insertTherapyEventIfNewWithTimestamp [date=%d, eventType=%s, pumpId=%d, pumpSerial=%s] - Result: %b",
                    historyRecord.atechDateTime, eventType, historyRecord.pumpId,
                    medtronicPumpStatus.serialNumber, result
                )
            )

            preferences.put(eventSP, historyRecord.atechDateTime)
        }
    }

    private fun processTDDs(tddsIn: MutableList<PumpHistoryEntry>) {
        val tdds = filterTDDs(tddsIn)

        aapsLogger.debug(
            LTag.PUMP, String.format(
                Locale.ENGLISH, logPrefix + "TDDs found: %d.\n%s",
                tdds.size, gson.toJson(tdds)
            )
        )

        for (tdd in tdds) {
            val totalsDTO = tdd.decodedData["Object"] as DailyTotalsDTO

            pumpSync.createOrUpdateTotalDailyDose(
                DateTimeUtil.toMillisFromATD(tdd.atechDateTime),
                totalsDTO.insulinBolus,
                totalsDTO.insulinBasal,
                totalsDTO.insulinTotal,
                tdd.pumpId,
                medtronicPumpStatus.pumpType,
                medtronicPumpStatus.serialNumber
            )
        }
    }

    @Suppress("unused")
    private enum class ProcessHistoryRecord(val description: String) {

        Bolus("Bolus"),
        TBR("TBR"),
        Suspend("Suspend");
    }

    private fun processBolusEntries(entryList: MutableList<PumpHistoryEntry>) {

        val boluses = pumpSyncStorage.getBoluses()

        for (bolus in entryList) {

            val bolusDTO = bolus.decodedData["Object"] as BolusDTO
            //var type: BS.Type = BS.Type.NORMAL
            var multiWave = false

            if (bolusDTO.bolusType == PumpBolusType.Extended) {
                addExtendedBolus(bolus, bolusDTO, multiWave)
                continue
            } else if (bolusDTO.bolusType == PumpBolusType.Multiwave) {
                multiWave = true
                aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "Multiwave bolus from pump, extended bolus and normal bolus will be added."))
                addExtendedBolus(bolus, bolusDTO, multiWave)
            }

            val deliveredAmount: Double = if (multiWave) bolusDTO.immediateAmount!! else bolusDTO.deliveredAmount

            var temporaryId: Long? = null

            if (!multiWave) {
                @Suppress("Unchecked_Cast")
                val entryWithTempId = findDbEntry(bolus, boluses as MutableList<PumpDbEntry>) as PumpDbEntryBolus?

                aapsLogger.debug(LTag.PUMP, "DD: entryWithTempId=$entryWithTempId")

                if (entryWithTempId != null) {
                    //aapsLogger.debug(LTag.PUMP, String.format("DD: entryWithTempId.bolusData=%s", if (entryWithTempId.bolusData == null) "null" else entryWithTempId.bolusData))

                    temporaryId = entryWithTempId.temporaryId
                    pumpSyncStorage.removeBolusWithTemporaryId(temporaryId)
                    boluses.remove(entryWithTempId)
                    //type = entryWithTempId.bolusType
                }
            }

            if (temporaryId != null) {
                val result = pumpSync.syncBolusWithTempId(
                    timestamp = tryToGetByLocalTime(bolus.atechDateTime),
                    amount = deliveredAmount,
                    temporaryId = temporaryId,
                    type = null,
                    pumpId = bolus.pumpId,
                    pumpType = medtronicPumpStatus.pumpType,
                    pumpSerial = medtronicPumpStatus.serialNumber
                )

                aapsLogger.debug(
                    LTag.PUMP, String.format(
                        Locale.ENGLISH, "syncBolusWithTempId [date=%d, temporaryId=%d, pumpId=%d, insulin=%.2f, pumpSerial=%s] - Result: %b",
                        bolus.atechDateTime, temporaryId, bolus.pumpId, deliveredAmount,
                        medtronicPumpStatus.serialNumber, result
                    )
                )
            } else {
                val result = pumpSync.syncBolusWithPumpId(
                    timestamp = tryToGetByLocalTime(bolus.atechDateTime),
                    amount = deliveredAmount,
                    type = null,
                    pumpId = bolus.pumpId,
                    pumpType = medtronicPumpStatus.pumpType,
                    pumpSerial = medtronicPumpStatus.serialNumber
                )

                aapsLogger.debug(
                    LTag.PUMP, String.format(
                        Locale.ENGLISH, "syncBolusWithPumpId [date=%d, pumpId=%d, insulin=%.2f, pumpSerial=%s] - Result: %b",
                        bolus.atechDateTime, bolus.pumpId, deliveredAmount,
                        medtronicPumpStatus.serialNumber, result
                    )
                )
            }

            addCarbs(bolus)
        }
    }

    private fun addExtendedBolus(bolus: PumpHistoryEntry, bolusDTO: BolusDTO, isMultiwave: Boolean) {
        val durationMs: Long = bolusDTO.duration * 60L * 1000L

        val result = pumpSync.syncExtendedBolusWithPumpId(
            tryToGetByLocalTime(bolus.atechDateTime),
            bolusDTO.deliveredAmount,
            durationMs,
            false,
            bolus.pumpId,
            medtronicPumpStatus.pumpType,
            medtronicPumpStatus.serialNumber
        )

        aapsLogger.debug(
            LTag.PUMP, String.format(
                Locale.ENGLISH, "syncExtendedBolusWithPumpId [date=%d, amount=%.2f, duration=%d, pumpId=%d, pumpSerial=%s, multiwave=%b] - Result: %b",
                bolus.atechDateTime, bolusDTO.deliveredAmount, bolusDTO.duration, bolus.pumpId,
                medtronicPumpStatus.serialNumber, isMultiwave, result
            )
        )
    }

    private fun addCarbs(bolus: PumpHistoryEntry) {
        if (bolus.containsDecodedData("Estimate")) {
            val bolusWizard = bolus.decodedData["Estimate"] as BolusWizardDTO

            pumpSyncStorage.addCarbs(
                PumpDbEntryCarbs(
                    tryToGetByLocalTime(bolus.atechDateTime),
                    bolusWizard.carbs.toDouble(),
                    medtronicPumpStatus.pumpType,
                    medtronicPumpStatus.serialNumber,
                    bolus.pumpId
                )
            )
        }
    }

    private fun processTBREntries(entryList: MutableList<PumpHistoryEntry>) {
        entryList.reverse()
        val tbr = entryList[0].getDecodedDataEntry("Object") as TempBasalPair
//        var readOldItem = false

        val oneMoreEntryFromHistory = getOneMoreEntryFromHistory(PumpHistoryEntryType.TempBasalCombined)

        if (tbr.isCancelTBR) { // if we have cancel we need to limit previous TBR with this cancel
            if (oneMoreEntryFromHistory != null) {
                entryList.add(0, oneMoreEntryFromHistory)
            } else {
                entryList.removeAt(0)
            }
        } else {
            if (oneMoreEntryFromHistory != null) {
                val tbrPrev = oneMoreEntryFromHistory.getDecodedDataEntry("Object") as TempBasalPair
                if (tbrPrev.isZeroTBR) {  // if we had Zero TBR in last previous TBR, then we need to limit it, so we need to process it too
                    entryList.add(0, oneMoreEntryFromHistory)
                }
            }
        }

        val tbrRecords = pumpSyncStorage.getTBRs()

        val processList: MutableList<TempBasalProcessDTO> = createTBRProcessList(entryList)

        if (processList.isNotEmpty()) {
            for (tempBasalProcessDTO in processList) {

                aapsLogger.debug(LTag.PUMP, "DD: tempBasalProcessDTO: " + tempBasalProcessDTO.toTreatmentString())
                //aapsLogger.debug(LTag.PUMP, "DD: tempBasalProcessDTO.itemOne: " + gson.toJson(tempBasalProcessDTO.itemOne))
                //aapsLogger.debug(LTag.PUMP, "DD: tempBasalProcessDTO.itemTwo: " + (if (tempBasalProcessDTO.itemTwo == null) "null" else gson.toJson(tempBasalProcessDTO.itemTwo!!)))

                @Suppress("Unchecked_Cast")
                val entryWithTempId = findDbEntry(tempBasalProcessDTO.itemOne, tbrRecords as MutableList<PumpDbEntry>) as PumpDbEntryTBR?

                aapsLogger.debug(LTag.PUMP, "DD: entryWithTempId: " + (entryWithTempId?.toString() ?: "null"))

                val tbrEntry = tempBasalProcessDTO.itemOneTbr

                aapsLogger.debug(LTag.PUMP, String.format("DD: tbrEntry=%s, tempBasalProcessDTO=%s", gson.toJson(tbrEntry), gson.toJson(tempBasalProcessDTO)))

                if (entryWithTempId != null) {

                    if (tbrEntry != null) {
                        aapsLogger.debug(
                            LTag.PUMP, "DD: tempIdEntry=${entryWithTempId}, tbrEntry=${tbrEntry}, " +
                                "tempBasalProcessDTO=${tempBasalProcessDTO}, " +
                                "pumpType=${medtronicPumpStatus.pumpType}, serial=${medtronicPumpStatus.serialNumber}"
                        )

                        aapsLogger.debug(
                            LTag.PUMP, "syncTemporaryBasalWithTempId " +
                                "[date=${tempBasalProcessDTO.atechDateTime}, dateProcess=${tryToGetByLocalTime(tempBasalProcessDTO.atechDateTime)},  " +
                                "tbrEntry.insulinRate=${tbrEntry.insulinRate}, " +
                                "duration=${tempBasalProcessDTO.durationAsSeconds} s, " +
                                "isAbsolute=${!tbrEntry.isPercent}, temporaryId=${entryWithTempId.temporaryId}, " +
                                "pumpId=${tempBasalProcessDTO.pumpId}, pumpType=${medtronicPumpStatus.pumpType}, " +
                                "pumpSerial=${medtronicPumpStatus.serialNumber}]"
                        )

                        if (tempBasalProcessDTO.durationAsSeconds <= 0) {
                            uiInteraction.addNotification(Notification.MDT_INVALID_HISTORY_DATA, rh.gs(R.string.invalid_history_data), Notification.URGENT)
                            aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithPumpId - Skipped")
                        } else {
                            val result = pumpSync.syncTemporaryBasalWithTempId(
                                tryToGetByLocalTime(tempBasalProcessDTO.atechDateTime),
                                tbrEntry.insulinRate,
                                tempBasalProcessDTO.durationAsSeconds * 1000L,
                                isAbsolute = !tbrEntry.isPercent,
                                entryWithTempId.temporaryId,
                                PumpSync.TemporaryBasalType.NORMAL,
                                tempBasalProcessDTO.pumpId,
                                medtronicPumpStatus.pumpType,
                                medtronicPumpStatus.serialNumber
                            )

                            aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithTempId - Result: $result")
                        }

                        pumpSyncStorage.removeTemporaryBasalWithTemporaryId(entryWithTempId.temporaryId)
                        tbrRecords.remove(entryWithTempId)

                        entryWithTempId.pumpId = tempBasalProcessDTO.pumpId
                        entryWithTempId.date = tryToGetByLocalTime(tempBasalProcessDTO.atechDateTime)

                        if (isTBRActive(entryWithTempId)) {
                            medtronicPumpStatus.runningTBR = entryWithTempId
                        }
                    } else {
                        aapsLogger.warn(LTag.PUMP, "tbrEntry (itemOne) is null, shouldn't be.")
                    }

                } else {

                    if (tbrEntry != null) {

                        aapsLogger.debug(
                            LTag.PUMP, "syncTemporaryBasalWithPumpId [date=${tempBasalProcessDTO.atechDateTime}, " +
                                "pumpId=${tempBasalProcessDTO.pumpId}, rate=${tbrEntry.insulinRate} U, " +
                                "duration=${tempBasalProcessDTO.durationAsSeconds} s, pumpSerial=${medtronicPumpStatus.serialNumber}]"
                        )

                        if (tempBasalProcessDTO.durationAsSeconds <= 0) {
                            uiInteraction.addNotification(Notification.MDT_INVALID_HISTORY_DATA, rh.gs(R.string.invalid_history_data), Notification.URGENT)
                            aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithPumpId - Skipped")
                        } else {
                            val result = pumpSync.syncTemporaryBasalWithPumpId(
                                tryToGetByLocalTime(tempBasalProcessDTO.atechDateTime),
                                tbrEntry.insulinRate,
                                tempBasalProcessDTO.durationAsSeconds * 1000L,
                                !tbrEntry.isPercent,
                                PumpSync.TemporaryBasalType.NORMAL,
                                tempBasalProcessDTO.pumpId,
                                medtronicPumpStatus.pumpType,
                                medtronicPumpStatus.serialNumber
                            )

                            aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithPumpId - Result: $result")
                        }

                        if (medtronicPumpStatus.runningTBR != null) {
                            if (!isTBRActive(medtronicPumpStatus.runningTBR!!)) {
                                medtronicPumpStatus.runningTBR = null
                            }
                        }

                        if (isTBRActive(
                                startTimestamp = tryToGetByLocalTime(tempBasalProcessDTO.atechDateTime),
                                durationSeconds = tempBasalProcessDTO.durationAsSeconds
                            )
                        ) {
                            if (medtronicPumpStatus.runningTBR == null) {
                                medtronicPumpStatus.runningTBR = PumpDbEntryTBR(
                                    temporaryId = 0L,
                                    date = tryToGetByLocalTime(tempBasalProcessDTO.atechDateTime),
                                    pumpType = medtronicPumpStatus.pumpType,
                                    serialNumber = medtronicPumpStatus.serialNumber,
                                    rate = tbrEntry.insulinRate,
                                    isAbsolute = !tbrEntry.isPercent,
                                    durationInSeconds = tempBasalProcessDTO.durationAsSeconds,
                                    tbrType = PumpSync.TemporaryBasalType.NORMAL,
                                    pumpId = tempBasalProcessDTO.pumpId
                                )
                            }
                        }
                    } else {
                        aapsLogger.warn(LTag.PUMP, "tbrEntry (itemOne) is null, shouldn't be.")
                    }
                }
            } // for
        } // collection
    }

    fun createTBRProcessList(entryList: MutableList<PumpHistoryEntry>): MutableList<TempBasalProcessDTO> {

        aapsLogger.debug(LTag.PUMP, "${ProcessHistoryRecord.TBR.description}  List (before filter): ${gson.toJson(entryList)}")

        var processDTO: TempBasalProcessDTO? = null
        val processList: MutableList<TempBasalProcessDTO> = mutableListOf()
        for (treatment in entryList) {
            val tbr2 = treatment.getDecodedDataEntry("Object") as TempBasalPair
            if (tbr2.isCancelTBR) {
                if (processDTO != null) {
                    processDTO.itemTwo = treatment
                } else {
                    aapsLogger.warn(LTag.PUMP, "processDTO was null - shouldn't happen, ignoring item. ItemTwo=$treatment")
                }
            } else {
                if (processDTO != null) {
                    processList.add(processDTO)
                }
                processDTO = TempBasalProcessDTO(
                    itemOne = treatment,
                    aapsLogger = aapsLogger,
                    objectType = TempBasalProcessDTO.ObjectType.TemporaryBasal
                )
            }
        }
        if (processDTO != null) {
            processList.add(processDTO)
        }

        var previousItem: TempBasalProcessDTO? = null
        val removalList: MutableList<TempBasalProcessDTO> = arrayListOf()

        // fix for Zero TBRs
        for (tempBasalProcessDTO in processList) {
            if (previousItem != null) {

                val pheEnd = PumpHistoryEntry()
                pheEnd.atechDateTime = DateTimeUtil.getATDWithAddedSeconds(tempBasalProcessDTO.itemOne.atechDateTime, -2)
                pheEnd.addDecodedData("Object", TempBasalPair(0.0, false, 0))

                val initialDuration = previousItem.durationAsSeconds

                previousItem.itemTwo = pheEnd

                if (previousItem.durationAsSeconds <= 0) {
                    // if we have duration of 0 or less, then we have invalid entry which needs to be removed
                    removalList.add(previousItem)
                } else if (previousItem.durationAsSeconds > initialDuration) {
                    // if duration with last item is longer than planned TBR duration we remove previous item and leave original duration
                    previousItem.itemTwo = null
                }

                previousItem = null
            }
            if (tempBasalProcessDTO.itemOneTbr!!.isZeroTBR && tempBasalProcessDTO.itemTwo == null) {
                previousItem = tempBasalProcessDTO
            }
        }

        // removing previously tagged item
        if (removalList.isNotEmpty()) {
            for (tempBasalProcessDTO in removalList) {
                processList.remove(tempBasalProcessDTO)
            }
        }

        // TODO this solution needs to be overworked, commenting out for now
        // val suspendList = getFilteredItems(newHistory,  //
        //                                      setOf(PumpHistoryEntryType.SuspendPump))
        //
        // val stopList : MutableList<PumpHistoryEntry> = mutableListOf()
        // stopList.addAll(suspendList);
        // stopList.addAll(rewindList);
        //
        // // TODO remove see if rewind items, need to fix any of current tempBasalProcessDTO items (bug 1724)
        // if (rewindList.isNotEmpty()) {
        //     for (rewindEntry in rewindList) {
        //         for (tempBasalProcessDTO in processList) {
        //             if (tempBasalProcessDTO.itemTwo==null) {
        //                 val endTime: Long = DateTimeUtil.getATDWithAddedMinutes(tempBasalProcessDTO.itemOne.atechDateTime, tempBasalProcessDTO.itemOneTbr!!.durationMinutes)
        //
        //                 if ((rewindEntry.atechDateTime > tempBasalProcessDTO.itemOne.atechDateTime) &&
        //                     (rewindEntry.atechDateTime < endTime)) {
        //                     tempBasalProcessDTO.itemTwo = rewindEntry
        //                     continue
        //                 }
        //             }
        //         }
        //     }
        // }
        //
        // // see if have rewind/stop items that need to fix any of current tempBasalProcessDTO items (bug 1724)
        // if (stopList.isNotEmpty()) {
        //     for (tempBasalProcessDTO in processList) {
        //         if (tempBasalProcessDTO.itemTwo==null) {
        //             val endTime: Long = DateTimeUtil.getATDWithAddedMinutes(tempBasalProcessDTO.itemOne.atechDateTime, tempBasalProcessDTO.itemOneTbr!!.durationMinutes)
        //
        //             val findNearestEntry = findNearestEntry(tempBasalProcessDTO.itemOne.atechDateTime, endTime, stopList);
        //
        //             if (findNearestEntry!=null) {
        //                 tempBasalProcessDTO.itemTwo = findNearestEntry
        //                 stopList.remove(findNearestEntry)
        //             }
        //         }
        //     }
        // }

        return processList
    }

    fun isTBRActive(dbEntry: PumpDbEntryTBR): Boolean {
        return isTBRActive(
            startTimestamp = dbEntry.date,
            durationSeconds = dbEntry.durationInSeconds
        )
    }

    private fun isTBRActive(startTimestamp: Long, durationSeconds: Int): Boolean {
        val endDate = startTimestamp + (durationSeconds * 1000)

        return (endDate > System.currentTimeMillis())
    }

    /**
     * findDbEntry - finds Db entries in database, while theoretically this should have same dateTime they
     * don't. Entry on pump is few seconds before treatment in AAPS, and on manual boluses on pump there
     * is no treatment at all. For now we look fro treatment that was from 0s - 1m59s within pump entry.
     *
     * @param treatment          Pump Entry
     * @param temporaryEntries entries from history
     * @return DbObject from AAPS (if found)
     *
     * Looks at all boluses that have temporaryId and find one that is correct for us (if such entry exists)
     */
    private fun findDbEntry(treatment: PumpHistoryEntry, temporaryEntries: MutableList<PumpDbEntry>): PumpDbEntry? {

        if (temporaryEntries.isEmpty()) {
            return null
        }

        var proposedTime = DateTimeUtil.toMillisFromATD(treatment.atechDateTime)

        // pumpTime should never be null, but it can theoretically happen if reading of time from pump fails
        this.pumpTime?.let { proposedTime += (it.timeDifference * 1000) }

        val proposedTimeDiff: LongArray = longArrayOf(proposedTime - (2 * 60 * 1000), proposedTime + (2L * 60L * 1000L))
        val tempEntriesList: MutableList<PumpDbEntry> = mutableListOf()

        for (temporaryEntry in temporaryEntries) {
            if (temporaryEntry.date > proposedTimeDiff[0] && temporaryEntry.date < proposedTimeDiff[1]) {
                tempEntriesList.add(temporaryEntry)
            }
        }

        if (tempEntriesList.isEmpty()) {
            return null
        } else if (tempEntriesList.size == 1) {
            return tempEntriesList[0]
        }

        var min = 0
        while (min < 2) {
            var sec = 0
            while (sec <= 50) {
                if (min == 1 && sec == 50) {
                    sec = 59
                }
                val diff = sec * 1000
                val outList = mutableListOf<PumpDbEntry>()
                for (treatment1 in tempEntriesList) {
                    if (treatment1.date > proposedTime - diff && treatment1.date < proposedTime + diff) {
                        outList.add(treatment1)
                    }
                }
                if (outList.size == 1) {
                    if (doubleBolusDebug) aapsLogger.debug(
                        LTag.PUMP,
                        String.format(
                            Locale.ENGLISH,
                            "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}. Type=EntrySelected, AtTimeMin={}, AtTimeSec={}",
                            treatment,
                            outList[0],
                            min,
                            sec
                        )
                    )
                    return outList[0]
                }
                if (min == 0 && sec == 10 && outList.size > 1) {
                    aapsLogger.error(
                        String.format(
                            Locale.ENGLISH, "Too many entries (with too small diff): (timeDiff=[min=%d,sec=%d],count=%d,list=%s)",
                            min, sec, outList.size, gson.toJson(outList)
                        )
                    )
                    if (doubleBolusDebug) aapsLogger.debug(
                        LTag.PUMP, String.format(
                            Locale.ENGLISH, "DoubleBolusDebug: findDbEntry Error - Too many entries (with too small diff): (timeDiff=[min=%d,sec=%d],count=%d,list=%s)",
                            min, sec, outList.size, gson.toJson(outList)
                        )
                    )
                }
                sec += 10
            }
            min += 1
        }
        return null
    }

    private fun processSuspends(tempBasalProcessList: List<TempBasalProcessDTO>) {
        for (tempBasalProcess in tempBasalProcessList) {

            aapsLogger.debug(
                LTag.PUMP, "processSuspends::syncTemporaryBasalWithPumpId [date=${tempBasalProcess.itemOne.atechDateTime}, " +
                    "rate=0.0, duration=${tempBasalProcess.durationAsSeconds} s, type=${PumpSync.TemporaryBasalType.PUMP_SUSPEND}, " +
                    "pumpId=${tempBasalProcess.itemOne.pumpId}, " +
                    "pumpSerial=${medtronicPumpStatus.serialNumber}]"
            )

            if (tempBasalProcess.durationAsSeconds <= 0) {
                uiInteraction.addNotification(Notification.MDT_INVALID_HISTORY_DATA, rh.gs(R.string.invalid_history_data), Notification.URGENT)
                aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithPumpId - Skipped")
            } else {
                val result = pumpSync.syncTemporaryBasalWithPumpId(
                    tryToGetByLocalTime(tempBasalProcess.itemOne.atechDateTime),
                    0.0,
                    tempBasalProcess.durationAsSeconds * 1000L,
                    true,
                    PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                    tempBasalProcess.itemOne.pumpId,
                    medtronicPumpStatus.pumpType,
                    medtronicPumpStatus.serialNumber
                )

                aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithPumpId: Result: $result")
            }
        }
    }

    // suspend/resume
    // no_delivery/prime & rewind/prime
    private fun getSuspendRecords(): MutableList<TempBasalProcessDTO> {
        val outList: MutableList<TempBasalProcessDTO> = mutableListOf()

        // suspend/resume
        outList.addAll(getSuspendResumeRecordsList())
        // no_delivery/prime & rewind/prime
        outList.addAll(getNoDeliveryRewindPrimeRecordsList())
        return outList
    }

    private fun getSuspendResumeRecordsList(): List<TempBasalProcessDTO> {
        val filteredItems = getFilteredItems(
            newHistory,  //
            setOf(PumpHistoryEntryType.SuspendPump, PumpHistoryEntryType.ResumePump)
        )

        aapsLogger.debug(LTag.PUMP, "SuspendResume Records: $filteredItems")

        val outList: MutableList<TempBasalProcessDTO> = mutableListOf()
        if (filteredItems.isNotEmpty()) {
            val filtered2Items: MutableList<PumpHistoryEntry> = mutableListOf()
            if (filteredItems.size % 2 == 0 && filteredItems[0].entryType === PumpHistoryEntryType.ResumePump) {
                // full resume suspends (S R S R)
                filtered2Items.addAll(filteredItems)
            } else if (filteredItems.size % 2 == 0 && filteredItems[0].entryType === PumpHistoryEntryType.SuspendPump) {
                // not full suspends, need to retrieve one more record and discard first one (R S R S) -> ([S] R S R [xS])
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
            if (filtered2Items.isNotEmpty()) {
                sort(filtered2Items)
                filtered2Items.reverse()
                var i = 0
                while (i < filtered2Items.size) {
                    val tbrProcess = TempBasalProcessDTO(
                        itemOne = filtered2Items[i],
                        aapsLogger = aapsLogger,
                        objectType = TempBasalProcessDTO.ObjectType.Suspend
                    )

                    tbrProcess.itemTwo = filtered2Items[i + 1]

                    if (tbrProcess.itemTwo != null)
                        outList.add(tbrProcess)

                    i += 2
                }
            }
        }
        return outList
    }

    private fun getNoDeliveryRewindPrimeRecordsList(): List<TempBasalProcessDTO> {
        val primeItems: MutableList<PumpHistoryEntry> = getFilteredItems(
            newHistory,  //
            setOf(PumpHistoryEntryType.Prime)
        )

        aapsLogger.debug(LTag.PUMP, "Prime Records: $primeItems")

        val outList: MutableList<TempBasalProcessDTO> = ArrayList()
        if (primeItems.isEmpty()) return outList
        val filteredItems: MutableList<PumpHistoryEntry> = getFilteredItems(
            newHistory,  //
            setOf(
                PumpHistoryEntryType.Prime,
                PumpHistoryEntryType.Rewind,
                PumpHistoryEntryType.NoDeliveryAlarm,
                PumpHistoryEntryType.Bolus,
                PumpHistoryEntryType.TempBasalCombined
            )
        )

        aapsLogger.debug(LTag.PUMP, "Filtered Records: $filteredItems")

        val tempData: MutableList<PumpHistoryEntry> = mutableListOf()
        var startedItems = false
        var finishedItems = false
        for (filteredItem in filteredItems) {
            if (filteredItem.entryType === PumpHistoryEntryType.Prime) {
                startedItems = true
            }
            if (startedItems) {
                if (filteredItem.entryType === PumpHistoryEntryType.Bolus ||
                    filteredItem.entryType === PumpHistoryEntryType.TempBasalCombined
                ) {
                    finishedItems = true
                    break
                }
                tempData.add(filteredItem)
            }
        }
        if (!finishedItems) {
            val filteredItemsOld: MutableList<PumpHistoryEntry> = getFilteredItems(
                allHistory,  //
                setOf(
                    PumpHistoryEntryType.Rewind,
                    PumpHistoryEntryType.NoDeliveryAlarm,
                    PumpHistoryEntryType.Bolus,
                    PumpHistoryEntryType.TempBasalCombined
                )
            )
            for (filteredItem in filteredItemsOld) {
                if (filteredItem.entryType === PumpHistoryEntryType.Bolus ||
                    filteredItem.entryType === PumpHistoryEntryType.TempBasalCombined
                ) {
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
        val itemTwo = items[0]

        items = getFilteredItems(tempData, PumpHistoryEntryType.NoDeliveryAlarm)
        if (items.isNotEmpty()) {
            val tbrProcess = TempBasalProcessDTO(
                itemOne = items[items.size - 1],
                aapsLogger = aapsLogger,
                objectType = TempBasalProcessDTO.ObjectType.Suspend
            )

            tbrProcess.itemTwo = itemTwo

            if (tbrProcess.itemTwo != null)
                outList.add(tbrProcess)

            return outList
        }

        items = getFilteredItems(tempData, PumpHistoryEntryType.Rewind)
        if (items.isNotEmpty()) {
            val tbrProcess = TempBasalProcessDTO(
                itemOne = items[0],
                aapsLogger = aapsLogger,
                objectType = TempBasalProcessDTO.ObjectType.Suspend
            )

            tbrProcess.itemTwo = itemTwo

            if (tbrProcess.itemTwo != null)
                outList.add(tbrProcess)

            return outList
        }

        return outList
    }

    private fun getOneMoreEntryFromHistory(entryType: PumpHistoryEntryType): PumpHistoryEntry? {
        val filteredItems: List<PumpHistoryEntry?> = getFilteredItems(allHistory, entryType)
        return if (filteredItems.isEmpty()) null else filteredItems[0]
    }

    private fun filterTDDs(tdds: MutableList<PumpHistoryEntry>): MutableList<PumpHistoryEntry> {
        val tddsOut: MutableList<PumpHistoryEntry> = mutableListOf()
        for (tdd in tdds) {
            if (tdd.entryType !== PumpHistoryEntryType.EndResultTotals) {
                tddsOut.add(tdd)
            }
        }
        return if (tddsOut.isEmpty()) tdds else tddsOut
    }

    private fun tryToGetByLocalTime(aTechDateTime: Long): Long {
        return DateTimeUtil.toMillisFromATD(aTechDateTime)
    }

    private fun getTDDType(): PumpHistoryEntryType {
        return if (!medtronicUtil.isModelSet) {
            PumpHistoryEntryType.EndResultTotals
        } else when (medtronicUtil.medtronicPumpModel) {
            MedtronicDeviceType.Medtronic_515,
            MedtronicDeviceType.Medtronic_715     -> PumpHistoryEntryType.DailyTotals515

            MedtronicDeviceType.Medtronic_522,
            MedtronicDeviceType.Medtronic_722     -> PumpHistoryEntryType.DailyTotals522

            MedtronicDeviceType.Medtronic_523_Revel,
            MedtronicDeviceType.Medtronic_723_Revel,
            MedtronicDeviceType.Medtronic_554_Veo,
            MedtronicDeviceType.Medtronic_754_Veo -> PumpHistoryEntryType.DailyTotals523

            else                                  -> {
                PumpHistoryEntryType.EndResultTotals
            }
        }
    }

    fun hasBasalProfileChanged(): Boolean {
        val filteredItems: List<PumpHistoryEntry?> = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile)
        aapsLogger.debug(LTag.PUMP, "hasBasalProfileChanged. Items: " + gson.toJson(filteredItems))
        return filteredItems.isNotEmpty()
    }

    fun processLastBasalProfileChange(pumpType: PumpType, mdtPumpStatus: MedtronicPumpStatus) {
        val filteredItems: List<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile)
        aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. Items: $filteredItems")
        var newProfile: PumpHistoryEntry? = null
        var lastDate: Long? = null
        if (filteredItems.size == 1) {
            newProfile = filteredItems[0]
        } else if (filteredItems.size > 1) {
            for (filteredItem in filteredItems) {
                if (lastDate == null || lastDate < filteredItem.atechDateTime) {
                    newProfile = filteredItem
                    lastDate = newProfile.atechDateTime
                }
            }
        }
        if (newProfile != null) {
            aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. item found, setting new basalProfileLocally: $newProfile")
            val basalProfile = newProfile.decodedData["Object"] as BasalProfile
            mdtPumpStatus.basalsByHour = basalProfile.getProfilesByHour(pumpType)
        }
    }

    fun hasPumpTimeChanged(): Boolean {
        return getStateFromFilteredList(
            setOf(
                PumpHistoryEntryType.NewTimeSet,  //
                PumpHistoryEntryType.ChangeTime
            )
        )
    }

    fun setIsInInit(init: Boolean) {
        isInit = init
    }

    // HELPER METHODS
    private fun sort(list: MutableList<PumpHistoryEntry>) {
        list.sortWith(PumpHistoryEntry.Comparator())
    }

    private fun preProcessTBRs(tbrsInput: MutableList<PumpHistoryEntry>): MutableList<PumpHistoryEntry> {
        val tbrs: MutableList<PumpHistoryEntry> = mutableListOf()
        val map: MutableMap<String, PumpHistoryEntry> = HashMap()
        for (pumpHistoryEntry in tbrsInput) {
            if (map.containsKey(pumpHistoryEntry.dt)) {
                medtronicPumpHistoryDecoder.decodeTempBasal(map.getValue(pumpHistoryEntry.dateTimeString), pumpHistoryEntry)
                pumpHistoryEntry.setEntryType(medtronicUtil.medtronicPumpModel, PumpHistoryEntryType.TempBasalCombined)
                tbrs.add(pumpHistoryEntry)
                map.remove(pumpHistoryEntry.dt)
            } else {
                map[pumpHistoryEntry.dateTimeString] = pumpHistoryEntry
            }
        }
        return tbrs
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
            filteredItems.isNotEmpty()
        }
    }

    fun getFilteredItems(inList: MutableList<PumpHistoryEntry>?, entryType: PumpHistoryEntryType): MutableList<PumpHistoryEntry> {
        return getFilteredItems(inList, setOf(entryType))
    }

    private fun getFilteredItems(inList: MutableList<PumpHistoryEntry>?, entryTypes: Set<PumpHistoryEntryType>?): MutableList<PumpHistoryEntry> {
        val outList: MutableList<PumpHistoryEntry> = mutableListOf()
        if (!inList.isNullOrEmpty()) {
            for (pumpHistoryEntry in inList) {
                if (entryTypes.isNullOrEmpty()) {
                    outList.add(pumpHistoryEntry)
                } else {
                    if (entryTypes.contains(pumpHistoryEntry.entryType)) {
                        outList.add(pumpHistoryEntry)
                    }
                }
            }
        }
        return outList
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

}
