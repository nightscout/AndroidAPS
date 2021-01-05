package info.nightscout.androidaps.plugins.pump.medtronic.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DbObjectBase;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusWizardDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.DailyTotalsDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalProcessDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentUpdateReturn;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by andy on 10/12/18.
 */

// TODO: After release we need to refactor how data is retrieved from pump, each entry in history needs to be marked, and sorting
//  needs to happen according those markings, not on time stamp (since AAPS can change time anytime it drifts away). This
//  needs to include not returning any records if TZ goes into -x area. To fully support this AAPS would need to take note of
//  all times that time changed (TZ, DST, etc.). Data needs to be returned in batches (time_changed batches, so that we can
//  handle it. It would help to assign sort_ids to items (from oldest (1) to newest (x)

// All things marked with "TODO: Fix db code" needs to be updated in new 2.5 database code

@Singleton
public class MedtronicHistoryData {

    private final HasAndroidInjector injector;
    private final AAPSLogger aapsLogger;
    private final SP sp;
    private final ActivePluginProvider activePlugin;
    private final NSUpload nsUpload;
    private final MedtronicUtil medtronicUtil;
    private final MedtronicPumpHistoryDecoder medtronicPumpHistoryDecoder;
    private final DatabaseHelperInterface databaseHelper;

    private final List<PumpHistoryEntry> allHistory;
    private List<PumpHistoryEntry> newHistory = null;

    private boolean isInit = false;

    private Gson gson; // cannot be initialized in constructor because of injection
    private Gson gsonCore; // cannot be initialized in constructor because of injection

    private ClockDTO pumpTime;

    private long lastIdUsed = 0;

    /**
     * Double bolus debug. We seem to have small problem with double Boluses (or sometimes also missing boluses
     * from history. This flag turns on debugging for that (default is off=false)... Debuging is pretty detailed,
     * so log files will get bigger.
     * Note: June 2020. Since this seems to be fixed, I am disabling this per default. I will leave code inside
     * in case we need it again. Code that turns this on is commented out RileyLinkMedtronicService#verifyConfiguration()
     */
    public static final boolean doubleBolusDebug = false;

    @Inject
    public MedtronicHistoryData(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            SP sp,
            ActivePluginProvider activePlugin,
            NSUpload nsUpload,
            MedtronicUtil medtronicUtil,
            MedtronicPumpHistoryDecoder medtronicPumpHistoryDecoder,
            DatabaseHelperInterface databaseHelperInterface
    ) {
        this.allHistory = new ArrayList<>();

        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.sp = sp;
        this.activePlugin = activePlugin;
        this.nsUpload = nsUpload;
        this.medtronicUtil = medtronicUtil;
        this.medtronicPumpHistoryDecoder = medtronicPumpHistoryDecoder;
        this.databaseHelper = databaseHelperInterface;
    }

    private Gson gson() {
        if (gson == null) gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        return gson;
    }

    private Gson gsonCore() {
        if (gsonCore == null) gsonCore = new GsonBuilder().create();
        return gsonCore;
    }

    /**
     * Add New History entries
     *
     * @param result PumpHistoryResult instance
     */
    public void addNewHistory(PumpHistoryResult result) {

        List<PumpHistoryEntry> validEntries = result.getValidEntries();

        List<PumpHistoryEntry> newEntries = new ArrayList<>();

        for (PumpHistoryEntry validEntry : validEntries) {

            if (!this.allHistory.contains(validEntry)) {
                newEntries.add(validEntry);
            }
        }

        this.newHistory = newEntries;

        showLogs("List of history (before filtering): [" + this.newHistory.size() + "]", gson().toJson(this.newHistory));
    }


    private void showLogs(String header, String data) {
        if (header != null) {
            aapsLogger.debug(LTag.PUMP, header);
        }

        if (StringUtils.isNotBlank(data)) {
            for (final String token : StringUtil.splitString(data, 3500)) {
                aapsLogger.debug(LTag.PUMP, "{}", token);
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "No data.");
        }
    }


    public List<PumpHistoryEntry> getAllHistory() {
        return this.allHistory;
    }


    public void filterNewEntries() {

        List<PumpHistoryEntry> newHistory2 = new ArrayList<>();
        List<PumpHistoryEntry> TBRs = new ArrayList<>();
        List<PumpHistoryEntry> bolusEstimates = new ArrayList<>();
        long atechDate = DateTimeUtil.toATechDate(new GregorianCalendar());

        //aapsLogger.debug(LTag.PUMP, "Filter new entries: Before {}", newHistory);

        if (!isCollectionEmpty(newHistory)) {

            for (PumpHistoryEntry pumpHistoryEntry : newHistory) {

                if (!this.allHistory.contains(pumpHistoryEntry)) {

                    PumpHistoryEntryType type = pumpHistoryEntry.getEntryType();

                    if (type == PumpHistoryEntryType.TempBasalRate || type == PumpHistoryEntryType.TempBasalDuration) {
                        TBRs.add(pumpHistoryEntry);
                    } else if (type == PumpHistoryEntryType.BolusWizard || type == PumpHistoryEntryType.BolusWizard512) {
                        bolusEstimates.add(pumpHistoryEntry);
                        newHistory2.add(pumpHistoryEntry);
                    } else {

                        if (type == PumpHistoryEntryType.EndResultTotals) {
                            if (!DateTimeUtil.isSameDay(atechDate, pumpHistoryEntry.atechDateTime)) {
                                newHistory2.add(pumpHistoryEntry);
                            }
                        } else {
                            newHistory2.add(pumpHistoryEntry);
                        }
                    }
                }
            }

            TBRs = preProcessTBRs(TBRs);

            if (bolusEstimates.size() > 0) {
                extendBolusRecords(bolusEstimates, newHistory2);
            }

            newHistory2.addAll(TBRs);

            this.newHistory = newHistory2;

            sort(this.newHistory);
        }

        aapsLogger.debug(LTag.PUMP, "New History entries found: {}", this.newHistory.size());

        showLogs("List of history (after filtering): [" + this.newHistory.size() + "]", gson().toJson(this.newHistory));

    }

    private void extendBolusRecords(List<PumpHistoryEntry> bolusEstimates, List<PumpHistoryEntry> newHistory2) {

        List<PumpHistoryEntry> boluses = getFilteredItems(newHistory2, PumpHistoryEntryType.Bolus);

        for (PumpHistoryEntry bolusEstimate : bolusEstimates) {
            for (PumpHistoryEntry bolus : boluses) {
                if (bolusEstimate.atechDateTime.equals(bolus.atechDateTime)) {
                    bolus.addDecodedData("Estimate", bolusEstimate.getDecodedData().get("Object"));
                }
            }
        }
    }


    public void finalizeNewHistoryRecords() {

        if ((newHistory == null) || (newHistory.size() == 0))
            return;

        PumpHistoryEntry pheLast = newHistory.get(0);

        // find last entry
        for (PumpHistoryEntry pumpHistoryEntry : newHistory) {
            if (pumpHistoryEntry.atechDateTime != null && pumpHistoryEntry.isAfter(pheLast.atechDateTime)) {
                pheLast = pumpHistoryEntry;
            }
        }

        // add new entries
        Collections.reverse(newHistory);

        for (PumpHistoryEntry pumpHistoryEntry : newHistory) {

            if (!this.allHistory.contains(pumpHistoryEntry)) {
                lastIdUsed++;
                pumpHistoryEntry.id = lastIdUsed;
                this.allHistory.add(pumpHistoryEntry);
            }

        }


        if (pheLast == null) // if we don't have any valid record we don't do the filtering and setting
            return;

        this.setLastHistoryRecordTime(pheLast.atechDateTime);
        sp.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, pheLast.atechDateTime);

        LocalDateTime dt = null;

        try {
            dt = DateTimeUtil.toLocalDateTime(pheLast.atechDateTime);
        } catch (Exception ex) {
            aapsLogger.error("Problem decoding date from last record: {}" + pheLast);
        }

        if (dt != null) {

            dt = dt.minusDays(1); // we keep 24 hours

            long dtRemove = DateTimeUtil.toATechDate(dt);

            List<PumpHistoryEntry> removeList = new ArrayList<>();

            for (PumpHistoryEntry pumpHistoryEntry : allHistory) {

                if (!pumpHistoryEntry.isAfter(dtRemove)) {
                    removeList.add(pumpHistoryEntry);
                }
            }

            this.allHistory.removeAll(removeList);

            this.sort(this.allHistory);

            aapsLogger.debug(LTag.PUMP, "All History records [afterFilterCount={}, removedItemsCount={}, newItemsCount={}]",
                    allHistory.size(), removeList.size(), newHistory.size());
        } else {
            aapsLogger.error("Since we couldn't determine date, we don't clean full history. This is just workaround.");
        }

        this.newHistory.clear();
    }


    public boolean hasRelevantConfigurationChanged() {
        return getStateFromFilteredList( //
                PumpHistoryEntryType.ChangeBasalPattern, //
                PumpHistoryEntryType.ClearSettings, //
                PumpHistoryEntryType.SaveSettings, //
                PumpHistoryEntryType.ChangeMaxBolus, //
                PumpHistoryEntryType.ChangeMaxBasal, //
                PumpHistoryEntryType.ChangeTempBasalType);
    }


    private boolean isCollectionEmpty(List col) {
        return (col == null || col.isEmpty());
    }

    private boolean isCollectionNotEmpty(List col) {
        return (col != null && !col.isEmpty());
    }


    public boolean isPumpSuspended() {

        List<PumpHistoryEntry> items = getDataForPumpSuspends();

        showLogs("isPumpSuspended: ", gson().toJson(items));

        if (isCollectionNotEmpty(items)) {

            PumpHistoryEntryType pumpHistoryEntryType = items.get(0).getEntryType();

            boolean isSuspended = !(pumpHistoryEntryType == PumpHistoryEntryType.TempBasalCombined || //
                    pumpHistoryEntryType == PumpHistoryEntryType.BasalProfileStart || //
                    pumpHistoryEntryType == PumpHistoryEntryType.Bolus || //
                    pumpHistoryEntryType == PumpHistoryEntryType.Resume || //
                    pumpHistoryEntryType == PumpHistoryEntryType.BatteryChange || //
                    pumpHistoryEntryType == PumpHistoryEntryType.Prime);

            aapsLogger.debug(LTag.PUMP, "isPumpSuspended. Last entry type={}, isSuspended={}", pumpHistoryEntryType, isSuspended);

            return isSuspended;
        } else
            return false;

    }


    private List<PumpHistoryEntry> getDataForPumpSuspends() {

        List<PumpHistoryEntry> newAndAll = new ArrayList<>();

        if (isCollectionNotEmpty(this.allHistory)) {
            newAndAll.addAll(this.allHistory);
        }

        if (isCollectionNotEmpty(this.newHistory)) {

            for (PumpHistoryEntry pumpHistoryEntry : newHistory) {
                if (!newAndAll.contains(pumpHistoryEntry)) {
                    newAndAll.add(pumpHistoryEntry);
                }
            }
        }

        if (newAndAll.isEmpty())
            return newAndAll;

        this.sort(newAndAll);

        List<PumpHistoryEntry> newAndAll2 = getFilteredItems(newAndAll, //
                PumpHistoryEntryType.Bolus, //
                PumpHistoryEntryType.TempBasalCombined, //
                PumpHistoryEntryType.Prime, //
                PumpHistoryEntryType.Suspend, //
                PumpHistoryEntryType.Resume, //
                PumpHistoryEntryType.Rewind, //
                PumpHistoryEntryType.NoDeliveryAlarm, //
                PumpHistoryEntryType.BatteryChange, //
                PumpHistoryEntryType.BasalProfileStart);

        newAndAll2 = filterPumpSuspend(newAndAll2, 10);

        return newAndAll2;
    }


    private List<PumpHistoryEntry> filterPumpSuspend(List<PumpHistoryEntry> newAndAll, int filterCount) {

        if (newAndAll.size() <= filterCount) {
            return newAndAll;
        }

        List<PumpHistoryEntry> newAndAllOut = new ArrayList<>();

        for (int i = 0; i < filterCount; i++) {
            newAndAllOut.add(newAndAll.get(i));
        }

        return newAndAllOut;
    }


    /**
     * Process History Data: Boluses(Treatments), TDD, TBRs, Suspend-Resume (or other pump stops: battery, prime)
     */
    public void processNewHistoryData() {

        // TODO: Fix db code
        // Prime (for reseting autosense)
        List<PumpHistoryEntry> primeRecords = getFilteredItems(PumpHistoryEntryType.Prime);

        aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: Prime [count={}, items={}]", primeRecords.size(), gson().toJson(primeRecords));

        if (isCollectionNotEmpty(primeRecords)) {
            try {
                processPrime(primeRecords);
            } catch (Exception ex) {
                aapsLogger.error("ProcessHistoryData: Error processing Prime entries: " + ex.getMessage(), ex);
                throw ex;
            }
        }

        // Rewind (for marking insulin change)
        List<PumpHistoryEntry> rewindRecords = getFilteredItems(PumpHistoryEntryType.Rewind);

        aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: Rewind [count={}, items={}]", rewindRecords.size(), gson().toJson(rewindRecords));

        if (isCollectionNotEmpty(rewindRecords)) {
            try {
                processRewind(rewindRecords);
            } catch (Exception ex) {
                aapsLogger.error("ProcessHistoryData: Error processing Rewind entries: " + ex.getMessage(), ex);
                throw ex;
            }
        }

        // TDD
        List<PumpHistoryEntry> tdds = getFilteredItems(PumpHistoryEntryType.EndResultTotals, getTDDType());

        aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: TDD [count={}, items={}]", tdds.size(), gson().toJson(tdds));

        if (isCollectionNotEmpty(tdds)) {
            try {
                processTDDs(tdds);
            } catch (Exception ex) {
                aapsLogger.error("ProcessHistoryData: Error processing TDD entries: " + ex.getMessage(), ex);
                throw ex;
            }
        }

        pumpTime = medtronicUtil.getPumpTime();

        // Bolus
        List<PumpHistoryEntry> treatments = getFilteredItems(PumpHistoryEntryType.Bolus);

        aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: Bolus [count={}, items={}]", treatments.size(), gson().toJson(treatments));

        if (treatments.size() > 0) {
            try {
                processBolusEntries(treatments);
            } catch (Exception ex) {
                aapsLogger.error("ProcessHistoryData: Error processing Bolus entries: " + ex.getMessage(), ex);
                throw ex;
            }
        }

        // TBR
        List<PumpHistoryEntry> tbrs = getFilteredItems(PumpHistoryEntryType.TempBasalCombined);

        aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: TBRs Processed [count={}, items={}]", tbrs.size(), gson().toJson(tbrs));

        if (tbrs.size() > 0) {
            try {
                processTBREntries(tbrs);
            } catch (Exception ex) {
                aapsLogger.error("ProcessHistoryData: Error processing TBR entries: " + ex.getMessage(), ex);
                throw ex;
            }
        }

        // 'Delivery Suspend'
        List<TempBasalProcessDTO> suspends;

        try {
            suspends = getSuspends();
        } catch (Exception ex) {
            aapsLogger.error("ProcessHistoryData: Error getting Suspend entries: " + ex.getMessage(), ex);
            throw ex;
        }

        aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: 'Delivery Suspend' Processed [count={}, items={}]", suspends.size(),
                gson().toJson(suspends));

        if (isCollectionNotEmpty(suspends)) {
            try {
                processSuspends(suspends);
            } catch (Exception ex) {
                aapsLogger.error("ProcessHistoryData: Error processing Suspends entries: " + ex.getMessage(), ex);
                throw ex;
            }
        }
    }


    private void processPrime(List<PumpHistoryEntry> primeRecords) {

        long maxAllowedTimeInPast = DateTimeUtil.getATDWithAddedMinutes(new GregorianCalendar(), -30);

        long lastPrimeRecord = 0L;

        for (PumpHistoryEntry primeRecord : primeRecords) {
            Object fixedAmount = primeRecord.getDecodedDataEntry("FixedAmount");

            if (fixedAmount != null && ((float) fixedAmount) == 0.0f) {
                // non-fixed primes are used to prime the tubing
                // fixed primes are used to prime the cannula
                // so skip the prime entry if it was not a fixed prime
                continue;
            }

            if (primeRecord.atechDateTime > maxAllowedTimeInPast) {
                if (lastPrimeRecord < primeRecord.atechDateTime) {
                    lastPrimeRecord = primeRecord.atechDateTime;
                }
            }
        }

        if (lastPrimeRecord != 0L) {
            long lastPrimeFromAAPS = sp.getLong(MedtronicConst.Statistics.LastPrime, 0L);

            if (lastPrimeRecord != lastPrimeFromAAPS) {
                uploadCareportalEvent(DateTimeUtil.toMillisFromATD(lastPrimeRecord), CareportalEvent.SITECHANGE);

                sp.putLong(MedtronicConst.Statistics.LastPrime, lastPrimeRecord);
            }
        }
    }

    private void processRewind(List<PumpHistoryEntry> rewindRecords) {
        long maxAllowedTimeInPast = DateTimeUtil.getATDWithAddedMinutes(new GregorianCalendar(), -30);
        long lastRewindRecord = 0L;

        for (PumpHistoryEntry rewindRecord : rewindRecords) {
            if (rewindRecord.atechDateTime > maxAllowedTimeInPast) {
                if (lastRewindRecord < rewindRecord.atechDateTime) {
                    lastRewindRecord = rewindRecord.atechDateTime;
                }
            }
        }

        if (lastRewindRecord != 0L) {
            long lastRewindFromAAPS = sp.getLong(MedtronicConst.Statistics.LastRewind, 0L);

            if (lastRewindRecord != lastRewindFromAAPS) {
                uploadCareportalEvent(DateTimeUtil.toMillisFromATD(lastRewindRecord), CareportalEvent.INSULINCHANGE);

                sp.putLong(MedtronicConst.Statistics.LastRewind, lastRewindRecord);
            }
        }
    }


    private void uploadCareportalEvent(long date, String event) {
        if (databaseHelper.getCareportalEventFromTimestamp(date) != null)
            return;
        try {
            JSONObject data = new JSONObject();
            String enteredBy = sp.getString("careportal_enteredby", "");
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            data.put("created_at", DateUtil.toISOString(date));
            data.put("eventType", event);
            CareportalEvent careportalEvent = new CareportalEvent(injector);
            careportalEvent.date = date;
            careportalEvent.source = Source.USER;
            careportalEvent.eventType = event;
            careportalEvent.json = data.toString();
            databaseHelper.createOrUpdate(careportalEvent);
            nsUpload.uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }


    private void processTDDs(List<PumpHistoryEntry> tddsIn) {

        List<PumpHistoryEntry> tdds = filterTDDs(tddsIn);

        aapsLogger.debug(LTag.PUMP, getLogPrefix() + "TDDs found: {}.\n{}", tdds.size(), gson().toJson(tdds));

        List<TDD> tddsDb = databaseHelper.getTDDsForLastXDays(3);

        for (PumpHistoryEntry tdd : tdds) {

            TDD tddDbEntry = findTDD(tdd.atechDateTime, tddsDb);

            DailyTotalsDTO totalsDTO = (DailyTotalsDTO) tdd.getDecodedData().get("Object");

            //aapsLogger.debug(LTag.PUMP, "DailyTotals: {}", totalsDTO);

            if (tddDbEntry == null) {
                TDD tddNew = new TDD();
                totalsDTO.setTDD(tddNew);

                aapsLogger.debug(LTag.PUMP, "TDD Add: {}", tddNew);

                databaseHelper.createOrUpdateTDD(tddNew);

            } else {

                if (!totalsDTO.doesEqual(tddDbEntry)) {
                    totalsDTO.setTDD(tddDbEntry);

                    aapsLogger.debug(LTag.PUMP, "TDD Edit: {}", tddDbEntry);

                    databaseHelper.createOrUpdateTDD(tddDbEntry);
                }
            }
        }
    }


    private enum ProcessHistoryRecord {
        Bolus("Bolus"),
        TBR("TBR"),
        Suspend("Suspend");

        private final String description;

        ProcessHistoryRecord(String desc) {
            this.description = desc;
        }

        public String getDescription() {
            return this.description;
        }

    }


    private void processBolusEntries(List<PumpHistoryEntry> entryList) {

        long oldestTimestamp = getOldestTimestamp(entryList);

        List<? extends DbObjectBase> entriesFromHistory = getDatabaseEntriesByLastTimestamp(oldestTimestamp, ProcessHistoryRecord.Bolus);

        if (doubleBolusDebug)
            aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: List (before filter): {}, FromDb={}", gson().toJson(entryList),
                    gsonCore().toJson(entriesFromHistory));

        filterOutAlreadyAddedEntries(entryList, entriesFromHistory);

        if (entryList.isEmpty()) {
            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: EntryList was filtered out.");
            return;
        }

        filterOutNonInsulinEntries(entriesFromHistory);

        if (doubleBolusDebug)
            aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: List (after filter): {}, FromDb={}", gson().toJson(entryList),
                    gsonCore().toJson(entriesFromHistory));

        if (isCollectionEmpty(entriesFromHistory)) {
            for (PumpHistoryEntry treatment : entryList) {
                aapsLogger.debug(LTag.PUMP, "Add Bolus (no db entry): " + treatment);
                if (doubleBolusDebug)
                    aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: Add Bolus: FromDb=null, Treatment={}", treatment);

                addBolus(treatment, null);
            }
        } else {
            for (PumpHistoryEntry treatment : entryList) {
                DbObjectBase treatmentDb = findDbEntry(treatment, entriesFromHistory);
                aapsLogger.debug(LTag.PUMP, "Add Bolus {} - (entryFromDb={}) ", treatment, treatmentDb);
                if (doubleBolusDebug)
                    aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: Add Bolus: FromDb={}, Treatment={}", treatmentDb, treatment);

                addBolus(treatment, (Treatment) treatmentDb);
            }
        }
    }


    private void filterOutNonInsulinEntries(List<? extends DbObjectBase> entriesFromHistory) {
        // when we try to pair PumpHistory with AAPS treatments, we need to ignore all non-insulin entries
        List<DbObjectBase> removeList = new ArrayList<>();

        for (DbObjectBase dbObjectBase : entriesFromHistory) {

            Treatment treatment = (Treatment) dbObjectBase;

            if (Round.isSame(treatment.insulin, 0d)) {
                removeList.add(dbObjectBase);
            }
        }

        entriesFromHistory.removeAll(removeList);
    }


    private void processTBREntries(List<PumpHistoryEntry> entryList) {

        Collections.reverse(entryList);

        TempBasalPair tbr = (TempBasalPair) entryList.get(0).getDecodedDataEntry("Object");

        boolean readOldItem = false;

        if (tbr.isCancelTBR()) {
            PumpHistoryEntry oneMoreEntryFromHistory = getOneMoreEntryFromHistory(PumpHistoryEntryType.TempBasalCombined);

            if (oneMoreEntryFromHistory != null) {
                entryList.add(0, oneMoreEntryFromHistory);
                readOldItem = true;
            } else {
                entryList.remove(0);
            }
        }

        long oldestTimestamp = getOldestTimestamp(entryList);

        List<? extends DbObjectBase> entriesFromHistory = getDatabaseEntriesByLastTimestamp(oldestTimestamp, ProcessHistoryRecord.TBR);

        aapsLogger.debug(LTag.PUMP, ProcessHistoryRecord.TBR.getDescription() + " List (before filter): {}, FromDb={}", gson().toJson(entryList),
                gson().toJson(entriesFromHistory));


        TempBasalProcessDTO processDTO = null;
        List<TempBasalProcessDTO> processList = new ArrayList<>();

        for (PumpHistoryEntry treatment : entryList) {

            TempBasalPair tbr2 = (TempBasalPair) treatment.getDecodedDataEntry("Object");

            if (tbr2.isCancelTBR()) {

                if (processDTO != null) {
                    processDTO.itemTwo = treatment;

                    if (readOldItem) {
                        processDTO.processOperation = TempBasalProcessDTO.Operation.Edit;
                        readOldItem = false;
                    }
                } else {
                    aapsLogger.error("processDTO was null - shouldn't happen. ItemTwo={}", treatment);
                }
            } else {
                if (processDTO != null) {
                    processList.add(processDTO);
                }

                processDTO = new TempBasalProcessDTO();
                processDTO.itemOne = treatment;
                processDTO.processOperation = TempBasalProcessDTO.Operation.Add;
            }
        }

        if (processDTO != null) {
            processList.add(processDTO);
        }


        if (isCollectionNotEmpty(processList)) {

            for (TempBasalProcessDTO tempBasalProcessDTO : processList) {

                if (tempBasalProcessDTO.processOperation == TempBasalProcessDTO.Operation.Edit) {
                    // edit
                    TemporaryBasal tempBasal = findTempBasalWithPumpId(tempBasalProcessDTO.itemOne.getPumpId(), entriesFromHistory);

                    if (tempBasal != null) {

                        tempBasal.durationInMinutes = tempBasalProcessDTO.getDuration();

                        databaseHelper.createOrUpdate(tempBasal);

                        aapsLogger.debug(LTag.PUMP, "Edit " + ProcessHistoryRecord.TBR.getDescription() + " - (entryFromDb={}) ", tempBasal);
                    } else {
                        aapsLogger.error("TempBasal not found. Item: {}", tempBasalProcessDTO.itemOne);
                    }

                } else {
                    // add

                    PumpHistoryEntry treatment = tempBasalProcessDTO.itemOne;

                    TempBasalPair tbr2 = (TempBasalPair) treatment.getDecodedData().get("Object");
                    tbr2.setDurationMinutes(tempBasalProcessDTO.getDuration());

                    TemporaryBasal tempBasal = findTempBasalWithPumpId(tempBasalProcessDTO.itemOne.getPumpId(), entriesFromHistory);

                    if (tempBasal == null) {
                        DbObjectBase treatmentDb = findDbEntry(treatment, entriesFromHistory);

                        aapsLogger.debug(LTag.PUMP, "Add " + ProcessHistoryRecord.TBR.getDescription() + " {} - (entryFromDb={}) ", treatment, treatmentDb);

                        addTBR(treatment, (TemporaryBasal) treatmentDb);
                    } else {
                        // this shouldn't happen
                        if (tempBasal.durationInMinutes != tempBasalProcessDTO.getDuration()) {
                            aapsLogger.debug(LTag.PUMP, "Found entry with wrong duration (shouldn't happen)... updating");
                            tempBasal.durationInMinutes = tempBasalProcessDTO.getDuration();
                        }

                    }
                } // if
            } // for

        } // collection
    }


    private TemporaryBasal findTempBasalWithPumpId(long pumpId, List<? extends DbObjectBase> entriesFromHistory) {

        for (DbObjectBase dbObjectBase : entriesFromHistory) {
            TemporaryBasal tbr = (TemporaryBasal) dbObjectBase;

            if (tbr.pumpId == pumpId) {
                return tbr;
            }
        }

        TemporaryBasal tempBasal = databaseHelper.findTempBasalByPumpId(pumpId);
        return tempBasal;
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
    private DbObjectBase findDbEntry(PumpHistoryEntry treatment, List<? extends DbObjectBase> entriesFromHistory) {

        long proposedTime = DateTimeUtil.toMillisFromATD(treatment.atechDateTime);

        //proposedTime += (this.pumpTime.timeDifference * 1000);

        if (doubleBolusDebug)
            aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}", treatment, gson().toJson(entriesFromHistory));

        if (entriesFromHistory.size() == 0) {
            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb=null", treatment);
            return null;
        } else if (entriesFromHistory.size() == 1) {
            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}. Type=SingleEntry", treatment, entriesFromHistory.get(0));

            // TODO: Fix db code
            // if difference is bigger than 2 minutes we discard entry
            long maxMillisAllowed = DateTimeUtil.getMillisFromATDWithAddedMinutes(treatment.atechDateTime, 2);

            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry maxMillisAllowed={}, AtechDateTime={} (add 2 minutes). ", maxMillisAllowed, treatment.atechDateTime);

            if (entriesFromHistory.get(0).getDate() > maxMillisAllowed) {
                if (doubleBolusDebug)
                    aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry entry filtered out, returning null. ");
                return null;
            }

            return entriesFromHistory.get(0);
        }

        for (int min = 0; min < 2; min += 1) {

            for (int sec = 0; sec <= 50; sec += 10) {

                if (min == 1 && sec == 50) {
                    sec = 59;
                }

                int diff = (sec * 1000);

                List<DbObjectBase> outList = new ArrayList<>();

                for (DbObjectBase treatment1 : entriesFromHistory) {

                    if ((treatment1.getDate() > proposedTime - diff) && (treatment1.getDate() < proposedTime + diff)) {
                        outList.add(treatment1);
                    }
                }

                if (outList.size() == 1) {
                    if (doubleBolusDebug)
                        aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}. Type=EntrySelected, AtTimeMin={}, AtTimeSec={}", treatment, entriesFromHistory.get(0), min, sec);

                    return outList.get(0);
                }

                if (min == 0 && sec == 10 && outList.size() > 1) {
                    aapsLogger.error("Too many entries (with too small diff): (timeDiff=[min={},sec={}],count={},list={})",
                            min, sec, outList.size(), gson().toJson(outList));
                    if (doubleBolusDebug)
                        aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Error - Too many entries (with too small diff): (timeDiff=[min={},sec={}],count={},list={})",
                                min, sec, outList.size(), gson().toJson(outList));
                }
            }
        }

        return null;
    }


    private List<? extends DbObjectBase> getDatabaseEntriesByLastTimestamp(long startTimestamp, ProcessHistoryRecord processHistoryRecord) {
        if (processHistoryRecord == ProcessHistoryRecord.Bolus) {
            return activePlugin.getActiveTreatments().getTreatmentsFromHistoryAfterTimestamp(startTimestamp);
        } else {
            return databaseHelper.getTemporaryBasalsDataFromTime(startTimestamp, true);
        }
    }


    private void filterOutAlreadyAddedEntries(List<PumpHistoryEntry> entryList, List<? extends DbObjectBase> treatmentsFromHistory) {

        if (isCollectionEmpty(treatmentsFromHistory))
            return;

        List<DbObjectBase> removeTreatmentsFromHistory = new ArrayList<>();
        List<PumpHistoryEntry> removeTreatmentsFromPH = new ArrayList<>();

        for (DbObjectBase treatment : treatmentsFromHistory) {

            if (treatment.getPumpId() != 0) {

                PumpHistoryEntry selectedBolus = null;

                for (PumpHistoryEntry bolus : entryList) {
                    if (bolus.getPumpId() == treatment.getPumpId()) {
                        selectedBolus = bolus;
                        break;
                    }
                }

                if (selectedBolus != null) {
                    entryList.remove(selectedBolus);

                    removeTreatmentsFromPH.add(selectedBolus);
                    removeTreatmentsFromHistory.add(treatment);
                }
            }
        }

        if (doubleBolusDebug)
            aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: filterOutAlreadyAddedEntries: PumpHistory={}, Treatments={}",
                    gson().toJson(removeTreatmentsFromPH),
                    gsonCore().toJson(removeTreatmentsFromHistory));

        treatmentsFromHistory.removeAll(removeTreatmentsFromHistory);
    }


    private void addBolus(PumpHistoryEntry bolus, Treatment treatment) {

        BolusDTO bolusDTO = (BolusDTO) bolus.getDecodedData().get("Object");

        if (treatment == null) {
            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): Bolus={}", bolusDTO);

            switch (bolusDTO.getBolusType()) {
                case Normal: {
                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();

                    detailedBolusInfo.date = tryToGetByLocalTime(bolus.atechDateTime);
                    detailedBolusInfo.source = Source.PUMP;
                    detailedBolusInfo.pumpId = bolus.getPumpId();
                    detailedBolusInfo.insulin = bolusDTO.getDeliveredAmount();

                    addCarbsFromEstimate(detailedBolusInfo, bolus);

                    if (doubleBolusDebug)
                        aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): DetailedBolusInfo={}", detailedBolusInfo);

                    boolean newRecord = activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, false);

                    bolus.setLinkedObject(detailedBolusInfo);

                    aapsLogger.debug(LTag.PUMP, "addBolus - [date={},pumpId={}, insulin={}, newRecord={}]", detailedBolusInfo.date,
                            detailedBolusInfo.pumpId, detailedBolusInfo.insulin, newRecord);
                }
                break;

                case Audio:
                case Extended: {
                    ExtendedBolus extendedBolus = new ExtendedBolus(injector);
                    extendedBolus.date = tryToGetByLocalTime(bolus.atechDateTime);
                    extendedBolus.source = Source.PUMP;
                    extendedBolus.insulin = bolusDTO.getDeliveredAmount();
                    extendedBolus.pumpId = bolus.getPumpId();
                    extendedBolus.isValid = true;
                    extendedBolus.durationInMinutes = bolusDTO.getDuration();

                    bolus.setLinkedObject(extendedBolus);

                    if (doubleBolusDebug)
                        aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): ExtendedBolus={}", extendedBolus);

                    activePlugin.getActiveTreatments().addToHistoryExtendedBolus(extendedBolus);

                    aapsLogger.debug(LTag.PUMP, "addBolus - Extended [date={},pumpId={}, insulin={}, duration={}]", extendedBolus.date,
                            extendedBolus.pumpId, extendedBolus.insulin, extendedBolus.durationInMinutes);

                }
                break;
            }

        } else {

            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(OldTreatment={}): Bolus={}", treatment, bolusDTO);

            treatment.source = Source.PUMP;
            treatment.pumpId = bolus.getPumpId();
            treatment.insulin = bolusDTO.getDeliveredAmount();

            TreatmentUpdateReturn updateReturn = activePlugin.getActiveTreatments().createOrUpdateMedtronic(treatment, false);

            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament!=null): NewTreatment={}, UpdateReturn={}", treatment, updateReturn);

            aapsLogger.debug(LTag.PUMP, "editBolus - [date={},pumpId={}, insulin={}, newRecord={}]", treatment.date,
                    treatment.pumpId, treatment.insulin, updateReturn.toString());

            bolus.setLinkedObject(treatment);

        }
    }


    private void addCarbsFromEstimate(DetailedBolusInfo detailedBolusInfo, PumpHistoryEntry bolus) {

        if (bolus.containsDecodedData("Estimate")) {

            BolusWizardDTO bolusWizard = (BolusWizardDTO) bolus.getDecodedData().get("Estimate");

            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addCarbsFromEstimate: Bolus={}, BolusWizardDTO={}", bolus, bolusWizard);

            detailedBolusInfo.carbs = bolusWizard.carbs;
        }
    }


    private void addTBR(PumpHistoryEntry treatment, TemporaryBasal temporaryBasalDbInput) {

        TempBasalPair tbr = (TempBasalPair) treatment.getDecodedData().get("Object");

        TemporaryBasal temporaryBasalDb = temporaryBasalDbInput;
        String operation = "editTBR";

        if (temporaryBasalDb == null) {
            temporaryBasalDb = new TemporaryBasal(injector);
            temporaryBasalDb.date = tryToGetByLocalTime(treatment.atechDateTime);

            operation = "addTBR";
        }

        temporaryBasalDb.source = Source.PUMP;
        temporaryBasalDb.pumpId = treatment.getPumpId();
        temporaryBasalDb.durationInMinutes = tbr.getDurationMinutes();
        temporaryBasalDb.absoluteRate = tbr.getInsulinRate();
        temporaryBasalDb.isAbsolute = !tbr.isPercent();

        treatment.setLinkedObject(temporaryBasalDb);

        databaseHelper.createOrUpdate(temporaryBasalDb);

        aapsLogger.debug(LTag.PUMP, operation + " - [date={},pumpId={}, rate={} {}, duration={}]", //
                temporaryBasalDb.date, //
                temporaryBasalDb.pumpId, //
                temporaryBasalDb.isAbsolute ? String.format(Locale.ENGLISH, "%.2f", temporaryBasalDb.absoluteRate) :
                        String.format(Locale.ENGLISH, "%d", temporaryBasalDb.percentRate), //
                temporaryBasalDb.isAbsolute ? "U/h" : "%", //
                temporaryBasalDb.durationInMinutes);
    }


    private void processSuspends(List<TempBasalProcessDTO> tempBasalProcessList) {

        for (TempBasalProcessDTO tempBasalProcess : tempBasalProcessList) {

            TemporaryBasal tempBasal = databaseHelper.findTempBasalByPumpId(tempBasalProcess.itemOne.getPumpId());

            if (tempBasal == null) {
                // add
                tempBasal = new TemporaryBasal(injector);
                tempBasal.date = tryToGetByLocalTime(tempBasalProcess.itemOne.atechDateTime);

                tempBasal.source = Source.PUMP;
                tempBasal.pumpId = tempBasalProcess.itemOne.getPumpId();
                tempBasal.durationInMinutes = tempBasalProcess.getDuration();
                tempBasal.absoluteRate = 0.0d;
                tempBasal.isAbsolute = true;

                tempBasalProcess.itemOne.setLinkedObject(tempBasal);
                tempBasalProcess.itemTwo.setLinkedObject(tempBasal);

                databaseHelper.createOrUpdate(tempBasal);

            }
        }

    }


    private List<TempBasalProcessDTO> getSuspends() {

        List<TempBasalProcessDTO> outList = new ArrayList<>();

        // suspend/resume
        outList.addAll(getSuspendResumeRecords());
        // no_delivery/prime & rewind/prime
        outList.addAll(getNoDeliveryRewindPrimeRecords());

        return outList;
    }

    private List<TempBasalProcessDTO> getSuspendResumeRecords() {
        List<PumpHistoryEntry> filteredItems = getFilteredItems(this.newHistory, //
                PumpHistoryEntryType.Suspend, //
                PumpHistoryEntryType.Resume);

        List<TempBasalProcessDTO> outList = new ArrayList<>();

        if (filteredItems.size() > 0) {

            List<PumpHistoryEntry> filtered2Items = new ArrayList<>();

            if ((filteredItems.size() % 2 == 0) && (filteredItems.get(0).getEntryType() == PumpHistoryEntryType.Resume)) {
                // full resume suspends (S R S R)
                filtered2Items.addAll(filteredItems);
            } else if ((filteredItems.size() % 2 == 0) && (filteredItems.get(0).getEntryType() == PumpHistoryEntryType.Suspend)) {
                // not full suspends, need to retrive one more record and discard first one (R S R S) -> ([S] R S R [xS])
                filteredItems.remove(0);

                PumpHistoryEntry oneMoreEntryFromHistory = getOneMoreEntryFromHistory(PumpHistoryEntryType.Suspend);
                if (oneMoreEntryFromHistory != null) {
                    filteredItems.add(oneMoreEntryFromHistory);
                } else {
                    filteredItems.remove(filteredItems.size() - 1); // remove last (unpaired R)
                }

                filtered2Items.addAll(filteredItems);
            } else {
                if (filteredItems.get(0).getEntryType() == PumpHistoryEntryType.Resume) {
                    // get one more from history (R S R) -> ([S] R S R)

                    PumpHistoryEntry oneMoreEntryFromHistory = getOneMoreEntryFromHistory(PumpHistoryEntryType.Suspend);
                    if (oneMoreEntryFromHistory != null) {
                        filteredItems.add(oneMoreEntryFromHistory);
                    } else {
                        filteredItems.remove(filteredItems.size() - 1); // remove last (unpaired R)
                    }

                    filtered2Items.addAll(filteredItems);
                } else {
                    // remove last and have paired items
                    filteredItems.remove(0);
                    filtered2Items.addAll(filteredItems);
                }
            }

            if (filtered2Items.size() > 0) {
                sort(filtered2Items);
                Collections.reverse(filtered2Items);

                for (int i = 0; i < filtered2Items.size(); i += 2) {
                    TempBasalProcessDTO dto = new TempBasalProcessDTO();

                    dto.itemOne = filtered2Items.get(i);
                    dto.itemTwo = filtered2Items.get(i + 1);

                    dto.processOperation = TempBasalProcessDTO.Operation.Add;

                    outList.add(dto);
                }
            }
        }

        return outList;
    }


    private List<TempBasalProcessDTO> getNoDeliveryRewindPrimeRecords() {
        List<PumpHistoryEntry> primeItems = getFilteredItems(this.newHistory, //
                PumpHistoryEntryType.Prime);

        List<TempBasalProcessDTO> outList = new ArrayList<>();

        if (primeItems.size() == 0)
            return outList;

        List<PumpHistoryEntry> filteredItems = getFilteredItems(this.newHistory, //
                PumpHistoryEntryType.Prime,
                PumpHistoryEntryType.Rewind,
                PumpHistoryEntryType.NoDeliveryAlarm,
                PumpHistoryEntryType.Bolus,
                PumpHistoryEntryType.TempBasalCombined
        );

        List<PumpHistoryEntry> tempData = new ArrayList<>();
        boolean startedItems = false;
        boolean finishedItems = false;

        for (PumpHistoryEntry filteredItem : filteredItems) {
            if (filteredItem.getEntryType() == PumpHistoryEntryType.Prime) {
                startedItems = true;
            }

            if (startedItems) {
                if (filteredItem.getEntryType() == PumpHistoryEntryType.Bolus ||
                        filteredItem.getEntryType() == PumpHistoryEntryType.TempBasalCombined) {
                    finishedItems = true;
                    break;
                }

                tempData.add(filteredItem);
            }
        }


        if (!finishedItems) {

            List<PumpHistoryEntry> filteredItemsOld = getFilteredItems(this.allHistory, //
                    PumpHistoryEntryType.Rewind,
                    PumpHistoryEntryType.NoDeliveryAlarm,
                    PumpHistoryEntryType.Bolus,
                    PumpHistoryEntryType.TempBasalCombined
            );

            for (PumpHistoryEntry filteredItem : filteredItemsOld) {

                if (filteredItem.getEntryType() == PumpHistoryEntryType.Bolus ||
                        filteredItem.getEntryType() == PumpHistoryEntryType.TempBasalCombined) {
                    finishedItems = true;
                    break;
                }

                tempData.add(filteredItem);
            }
        }


        if (!finishedItems) {
            showLogs("NoDeliveryRewindPrimeRecords: Not finished Items: ", gson().toJson(tempData));
            return outList;
        }

        showLogs("NoDeliveryRewindPrimeRecords: Records to evaluate: ", gson().toJson(tempData));

        List<PumpHistoryEntry> items = getFilteredItems(tempData, //
                PumpHistoryEntryType.Prime
        );


        TempBasalProcessDTO processDTO = new TempBasalProcessDTO();

        processDTO.itemTwo = items.get(0);

        items = getFilteredItems(tempData, //
                PumpHistoryEntryType.NoDeliveryAlarm
        );

        if (items.size() > 0) {

            processDTO.itemOne = items.get(items.size() - 1);
            processDTO.processOperation = TempBasalProcessDTO.Operation.Add;

            outList.add(processDTO);
            return outList;
        }


        items = getFilteredItems(tempData, //
                PumpHistoryEntryType.Rewind
        );

        if (items.size() > 0) {

            processDTO.itemOne = items.get(0);
            processDTO.processOperation = TempBasalProcessDTO.Operation.Add;

            outList.add(processDTO);
            return outList;
        }

        return outList;
    }


    private PumpHistoryEntry getOneMoreEntryFromHistory(PumpHistoryEntryType entryType) {
        List<PumpHistoryEntry> filteredItems = getFilteredItems(this.allHistory, entryType);

        return filteredItems.size() == 0 ? null : filteredItems.get(0);
    }


    private List<PumpHistoryEntry> filterTDDs(List<PumpHistoryEntry> tdds) {
        List<PumpHistoryEntry> tddsOut = new ArrayList<>();

        for (PumpHistoryEntry tdd : tdds) {
            if (tdd.getEntryType() != PumpHistoryEntryType.EndResultTotals) {
                tddsOut.add(tdd);
            }
        }

        return tddsOut.size() == 0 ? tdds : tddsOut;
    }


    private TDD findTDD(long atechDateTime, List<TDD> tddsDb) {

        for (TDD tdd : tddsDb) {

            if (DateTimeUtil.isSameDayATDAndMillis(atechDateTime, tdd.date)) {
                return tdd;
            }
        }

        return null;
    }

    private long tryToGetByLocalTime(long atechDateTime) {
        return DateTimeUtil.toMillisFromATD(atechDateTime);
    }


    private int getOldestDateDifference(List<PumpHistoryEntry> treatments) {

        long dt = Long.MAX_VALUE;
        PumpHistoryEntry currentTreatment = null;

        if (isCollectionEmpty(treatments)) {
            return 8; // default return of 6 (5 for diif on history reading + 2 for max allowed difference) minutes
        }

        for (PumpHistoryEntry treatment : treatments) {

            if (treatment.atechDateTime < dt) {
                dt = treatment.atechDateTime;
                currentTreatment = treatment;
            }
        }

        LocalDateTime oldestEntryTime;

        try {

            oldestEntryTime = DateTimeUtil.toLocalDateTime(dt);
            oldestEntryTime = oldestEntryTime.minusMinutes(3);

//            if (this.pumpTime.timeDifference < 0) {
//                oldestEntryTime = oldestEntryTime.plusSeconds(this.pumpTime.timeDifference);
//            }
        } catch (Exception ex) {
            aapsLogger.error("Problem decoding date from last record: {}" + currentTreatment);
            return 8; // default return of 6 minutes
        }

        LocalDateTime now = new LocalDateTime();

        Minutes minutes = Minutes.minutesBetween(oldestEntryTime, now);

        // returns oldest time in history, with calculated time difference between pump and phone, minus 5 minutes
        aapsLogger.debug(LTag.PUMP, "Oldest entry: {}, pumpTimeDifference={}, newDt={}, currentTime={}, differenceMin={}", dt,
                this.pumpTime.timeDifference, oldestEntryTime, now, minutes.getMinutes());

        return minutes.getMinutes();
    }


    private long getOldestTimestamp(List<PumpHistoryEntry> treatments) {

        long dt = Long.MAX_VALUE;
        PumpHistoryEntry currentTreatment = null;

        for (PumpHistoryEntry treatment : treatments) {

            if (treatment.atechDateTime < dt) {
                dt = treatment.atechDateTime;
                currentTreatment = treatment;
            }
        }

        if (doubleBolusDebug)
            aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: getOldestTimestamp. Oldest entry found: time={}, object={}", dt, currentTreatment);

        try {

            GregorianCalendar oldestEntryTime = DateTimeUtil.toGregorianCalendar(dt);
            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: getOldestTimestamp. oldestEntryTime: {}", DateTimeUtil.toString(oldestEntryTime));
            oldestEntryTime.add(Calendar.MINUTE, -2);

            if (doubleBolusDebug)
                aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: getOldestTimestamp. oldestEntryTime (-2m): {}, timeInMillis={}", DateTimeUtil.toString(oldestEntryTime), oldestEntryTime.getTimeInMillis());

            return oldestEntryTime.getTimeInMillis();

        } catch (Exception ex) {
            aapsLogger.error("Problem decoding date from last record: {}", currentTreatment);
            return 8; // default return of 6 minutes
        }

    }


    private PumpHistoryEntryType getTDDType() {

        if (medtronicUtil.getMedtronicPumpModel() == null) {
            return PumpHistoryEntryType.EndResultTotals;
        }

        switch (medtronicUtil.getMedtronicPumpModel()) {

            case Medtronic_515:
            case Medtronic_715:
                return PumpHistoryEntryType.DailyTotals515;

            case Medtronic_522:
            case Medtronic_722:
                return PumpHistoryEntryType.DailyTotals522;

            case Medtronic_523_Revel:
            case Medtronic_723_Revel:
            case Medtronic_554_Veo:
            case Medtronic_754_Veo:
                return PumpHistoryEntryType.DailyTotals523;

            default: {
                return PumpHistoryEntryType.EndResultTotals;
            }
        }
    }


    public boolean hasBasalProfileChanged() {

        List<PumpHistoryEntry> filteredItems = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile);

        aapsLogger.debug(LTag.PUMP, "hasBasalProfileChanged. Items: " + gson().toJson(filteredItems));

        return (filteredItems.size() > 0);
    }


    public void processLastBasalProfileChange(PumpType pumpType, MedtronicPumpStatus mdtPumpStatus) {

        List<PumpHistoryEntry> filteredItems = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile);

        aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. Items: " + filteredItems);

        PumpHistoryEntry newProfile = null;
        Long lastDate = null;

        if (filteredItems.size() == 1) {
            newProfile = filteredItems.get(0);
        } else if (filteredItems.size() > 1) {

            for (PumpHistoryEntry filteredItem : filteredItems) {

                if (lastDate == null || lastDate < filteredItem.atechDateTime) {
                    newProfile = filteredItem;
                    lastDate = newProfile.atechDateTime;
                }
            }
        }

        if (newProfile != null) {
            aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. item found, setting new basalProfileLocally: " + newProfile);
            BasalProfile basalProfile = (BasalProfile) newProfile.getDecodedData().get("Object");

            mdtPumpStatus.basalsByHour = basalProfile.getProfilesByHour(pumpType);
        }
    }


    public boolean hasPumpTimeChanged() {
        return getStateFromFilteredList(PumpHistoryEntryType.NewTimeSet, //
                PumpHistoryEntryType.ChangeTime);
    }


    public void setLastHistoryRecordTime(Long lastHistoryRecordTime) {

        // this.previousLastHistoryRecordTime = this.lastHistoryRecordTime;
    }


    public void setIsInInit(boolean init) {
        this.isInit = init;
    }


    // HELPER METHODS

    private void sort(List<PumpHistoryEntry> list) {
        if (list!=null && !list.isEmpty()) {
            Collections.sort(list, new PumpHistoryEntry.Comparator());
        }
    }


    private List<PumpHistoryEntry> preProcessTBRs(List<PumpHistoryEntry> TBRs_Input) {
        List<PumpHistoryEntry> TBRs = new ArrayList<>();

        Map<String, PumpHistoryEntry> map = new HashMap<>();

        for (PumpHistoryEntry pumpHistoryEntry : TBRs_Input) {
            if (map.containsKey(pumpHistoryEntry.DT)) {
                medtronicPumpHistoryDecoder.decodeTempBasal(map.get(pumpHistoryEntry.DT), pumpHistoryEntry);
                pumpHistoryEntry.setEntryType(medtronicUtil.getMedtronicPumpModel(), PumpHistoryEntryType.TempBasalCombined);
                TBRs.add(pumpHistoryEntry);
                map.remove(pumpHistoryEntry.DT);
            } else {
                map.put(pumpHistoryEntry.DT, pumpHistoryEntry);
            }
        }

        return TBRs;
    }


    private List<PumpHistoryEntry> getFilteredItems(PumpHistoryEntryType... entryTypes) {
        return getFilteredItems(this.newHistory, entryTypes);
    }


    private boolean getStateFromFilteredList(PumpHistoryEntryType... entryTypes) {
        if (isInit) {
            return false;
        } else {
            List<PumpHistoryEntry> filteredItems = getFilteredItems(entryTypes);

            aapsLogger.debug(LTag.PUMP, "Items: " + filteredItems);

            return filteredItems.size() > 0;
        }
    }


    private List<PumpHistoryEntry> getFilteredItems(List<PumpHistoryEntry> inList, PumpHistoryEntryType... entryTypes) {

        // aapsLogger.debug(LTag.PUMP, "InList: " + inList.size());
        List<PumpHistoryEntry> outList = new ArrayList<>();

        if (inList != null && inList.size() > 0) {
            for (PumpHistoryEntry pumpHistoryEntry : inList) {

                if (!isEmpty(entryTypes)) {
                    for (PumpHistoryEntryType pumpHistoryEntryType : entryTypes) {

                        if (pumpHistoryEntry.getEntryType() == pumpHistoryEntryType) {
                            outList.add(pumpHistoryEntry);
                            break;
                        }
                    }
                } else {
                    outList.add(pumpHistoryEntry);
                }
            }
        }

        // aapsLogger.debug(LTag.PUMP, "OutList: " + outList.size());

        return outList;
    }


    private boolean isEmpty(PumpHistoryEntryType... entryTypes) {
        return (entryTypes == null || (entryTypes.length == 1 && entryTypes[0] == null));
    }


    private String getLogPrefix() {
        return "MedtronicHistoryData::";
    }

}
