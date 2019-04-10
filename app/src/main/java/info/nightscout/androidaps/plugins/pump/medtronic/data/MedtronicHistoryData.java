package info.nightscout.androidaps.plugins.pump.medtronic.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.gson.Gson;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.DailyTotalsDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;

//import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;

/**
 * Created by andy on 10/12/18.
 */

public class MedtronicHistoryData {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicHistoryData.class);

    private List<PumpHistoryEntry> allHistory = null;
    private List<PumpHistoryEntry> newHistory = null;

    // private LocalDateTime previousLastHistoryRecordTime;
    private Long lastHistoryRecordTime;
    private boolean isInit = false;

    private Gson gsonPretty;
    private List<PumpHistoryEntry> fakeTBRs;

    DatabaseHelper databaseHelper = MainApp.getDbHelper();


    public MedtronicHistoryData() {
        this.allHistory = new ArrayList<>();
        this.gsonPretty = MedtronicPumpPlugin.gsonInstancePretty;
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

        showLogs("List of history (before filtering): ", MedtronicPumpPlugin.gsonInstance.toJson(this.newHistory));
    }


    public static void showLogs(String header, String data) {

        if (header != null) {
            LOG.debug(header);
        }

        for (final String token : Splitter.fixedLength(3500).split(data)) {
            LOG.debug("{}", token);
        }
    }


    public List<PumpHistoryEntry> getAllHistory() {
        return this.allHistory;
    }


    public void filterNewEntries() {

        List<PumpHistoryEntry> newHistory2 = new ArrayList<>();
        List<PumpHistoryEntry> TBRs = new ArrayList<>();
        long atechDate = DateTimeUtil.toATechDate(new GregorianCalendar());

        for (PumpHistoryEntry pumpHistoryEntry : newHistory) {

            if (!this.allHistory.contains(pumpHistoryEntry)) {

                PumpHistoryEntryType type = pumpHistoryEntry.getEntryType();

                if (type == PumpHistoryEntryType.TempBasalRate || type == PumpHistoryEntryType.TempBasalDuration) {
                    TBRs.add(pumpHistoryEntry);
                } else {

                    if (type == PumpHistoryEntryType.EndResultTotals) {
                        if (!DateTimeUtil.isSameDay(atechDate, pumpHistoryEntry.atechDateTime)) {
                            newHistory2.add(pumpHistoryEntry);
                        }
                    } else {
                        newHistory2.add(pumpHistoryEntry);
                    }

                }
                // }
            }

        }

        TBRs = preProcessTBRs(TBRs);

        newHistory2.addAll(TBRs);

        this.newHistory = newHistory2;

        sort(this.newHistory);

        LOG.debug("New History entries found: {}", this.newHistory.size());
        showLogs("List of history (after filtering): ", MedtronicPumpPlugin.gsonInstance.toJson(this.newHistory));

    }


    public void finalizeNewHistoryRecords() {

        if ((newHistory == null) || (newHistory.size() == 0))
            return;

        PumpHistoryEntry pheLast = newHistory.get(0);

        for (PumpHistoryEntry pumpHistoryEntry : newHistory) {

            if (!this.allHistory.contains(pumpHistoryEntry)) {
                this.allHistory.add(pumpHistoryEntry);
            }

            if (pumpHistoryEntry.isAfter(pheLast.atechDateTime)) {
                pheLast = pumpHistoryEntry;
            }

        }

        this.setLastHistoryRecordTime(pheLast.atechDateTime);

        LocalDateTime dt = DateTimeUtil.toLocalDateTime(pheLast.atechDateTime);
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

        LOG.debug("All History records [afterFilterCount={}, removedItemsCount={}, newItemsCount={}]",
            allHistory.size(), removeList.size(), newHistory.size());

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


    public boolean isPumpSuspended() {

        List<PumpHistoryEntry> items = getDataForSuspends(false);

        showLogs("isPumpSuspended: ", MedtronicPumpPlugin.gsonInstancePretty.toJson(items));

        if (!items.isEmpty()) {

            PumpHistoryEntryType pumpHistoryEntryType = items.get(0).getEntryType();

            boolean isSuspended = !(pumpHistoryEntryType == PumpHistoryEntryType.TempBasalCombined || //
                pumpHistoryEntryType == PumpHistoryEntryType.BasalProfileStart || //
                pumpHistoryEntryType == PumpHistoryEntryType.Bolus || //
                pumpHistoryEntryType == PumpHistoryEntryType.PumpResume || //
            pumpHistoryEntryType == PumpHistoryEntryType.Prime);

            LOG.debug("isPumpSuspended. Last entry type={}, isSuspended={}", pumpHistoryEntryType, isSuspended);

            return isSuspended;
        } else
            return false;

    }


    private List<PumpHistoryEntry> getDataForSuspends(boolean forHistory) {

        List<PumpHistoryEntry> newAndAll = new ArrayList<>();

        if (!isCollectionEmpty(this.allHistory)) {

            if (forHistory) {
                // TODO we filter all history ang get last 2
            } else {
                newAndAll.addAll(this.allHistory);
            }
        }

        if (!isCollectionEmpty(this.newHistory)) {

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
            PumpHistoryEntryType.PumpSuspend, //
            PumpHistoryEntryType.PumpResume, //
            PumpHistoryEntryType.Rewind, //
            PumpHistoryEntryType.NoDeliveryAlarm, //
            PumpHistoryEntryType.BasalProfileStart);

        if (!forHistory) {
            newAndAll2 = filterPumpSuspend(newAndAll2, 10); // just last 10 (of relevant), for history we already
            // filtered
        }

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

        // TDD
        List<PumpHistoryEntry> tdds = getFilteredItems(PumpHistoryEntryType.EndResultTotals, getTDDType());

        LOG.debug("ProcessHistoryData: TDD [count={}, items={}]", tdds.size(), gsonPretty.toJson(tdds));

        if (!isCollectionEmpty(tdds)) {
            processTDDs(tdds);
        }

        pumpTime = MedtronicUtil.getPumpTime();

        // Bolus
        List<PumpHistoryEntry> treatments = getFilteredItems(PumpHistoryEntryType.Bolus);

        LOG.debug("ProcessHistoryData: Bolus [count={}, items={}]", treatments.size(), gsonPretty.toJson(treatments));

        if (treatments.size() > 0) {
            processBoluses(treatments);
        }

        // TBR
        List<PumpHistoryEntry> tbrs = getFilteredItems(PumpHistoryEntryType.TempBasalCombined);

        LOG.debug("ProcessHistoryData: TBRs [count={}, items={}]", tbrs.size(), gsonPretty.toJson(tbrs));

        if (tbrs.size() > 0) {
            // processTBRs(tbrs);
        }

        // Suspends (for suspends/resume, fakeTBR)
        List<PumpHistoryEntry> suspends = getSuspends();

        LOG.debug("ProcessHistoryData: FakeTBRs (suspend/resume) [count={}, items={}]", suspends.size(),
            gsonPretty.toJson(suspends));

        if (suspends.size() > 0) {
            // processSuspends(treatments);
        }
    }

    ClockDTO pumpTime;


    public void processTDDs(List<PumpHistoryEntry> tddsIn) {

        List<PumpHistoryEntry> tdds = filterTDDs(tddsIn);

        // /pumpTime = MedtronicUtil.getPumpTime();

        LOG.error(getLogPrefix() + "TDDs found: {}. Not processed.\n{}", tdds.size(), gsonPretty.toJson(tdds));

        // FIXME tdd
        List<TDD> tddsDb = MainApp.getDbHelper().getTDDs(); // .getTDDsForLastXDays(3);

        for (PumpHistoryEntry tdd : tdds) {

            TDD tddDbEntry = findTDD(tdd.atechDateTime, tddsDb);

            DailyTotalsDTO totalsDTO = (DailyTotalsDTO)tdd.getDecodedData().get("Object");

            LOG.debug("DailtyTotals: {}", totalsDTO);

            if (tddDbEntry == null) {
                TDD tddNew = new TDD();
                totalsDTO.setTDD(tddNew);

                LOG.debug("TDD-Add: {}", tddNew);

                MainApp.getDbHelper().createOrUpdateTDD(tddNew);

            } else {

                if (!totalsDTO.doesEqual(tddDbEntry)) {
                    totalsDTO.setTDD(tddDbEntry);

                    LOG.debug("TDD-Edit: {}", tddDbEntry);

                    MainApp.getDbHelper().createOrUpdateTDD(tddDbEntry);
                }
            }
        }

    }


    // TODO needs to be implemented
    public void processBoluses(List<PumpHistoryEntry> boluses) {

        int dateDifference = getOldestDateDifference(boluses);

        List<Treatment> treatmentsFromHistory = TreatmentsPlugin.getPlugin().getTreatmentsFromHistoryXMinutesAgo(
            dateDifference);

        LOG.debug("Boluses (before filter): {}, FromDb={}", gsonPretty.toJson(boluses),
            gsonPretty.toJson(treatmentsFromHistory));

        filterOutAlreadyAddedEntries(boluses, treatmentsFromHistory);

        if (boluses.isEmpty())
            return;

        LOG.debug("Boluses (after filter): {}, FromDb={}", gsonPretty.toJson(boluses),
            gsonPretty.toJson(treatmentsFromHistory));

        if (treatmentsFromHistory.isEmpty()) {
            for (PumpHistoryEntry treatment : boluses) {
                LOG.debug("Add Bolus (no treatments): " + treatment);
                addBolus(treatment, null);
            }
        } else {
            for (PumpHistoryEntry treatment : boluses) {
                tryToGetByLocalTime(treatment.atechDateTime);
                Treatment treatmentDb = findTreatment(treatment, treatmentsFromHistory, dateDifference);
                LOG.debug("Add Bolus {} - (treatmentFromDb={}) ", treatment, treatmentDb);

                addBolus(treatment, treatmentDb);
            }
        }
    }


    private void filterOutAlreadyAddedEntries(List<PumpHistoryEntry> boluses, List<Treatment> treatmentsFromHistory) {

        List<Treatment> removeTreatmentsFromHistory = new ArrayList<>();

        for (Treatment treatment : treatmentsFromHistory) {

            if (treatment.pumpId != 0) {

                PumpHistoryEntry selectedBolus = null;

                for (PumpHistoryEntry bolus : boluses) {
                    if (bolus.getPumpId() == treatment.pumpId) {
                        selectedBolus = bolus;
                        break;
                    }
                }

                if (selectedBolus != null) {
                    boluses.remove(selectedBolus);

                    removeTreatmentsFromHistory.add(treatment);
                }
            }
        }

        treatmentsFromHistory.removeAll(removeTreatmentsFromHistory);

    }


    private Treatment findTreatmentOld(PumpHistoryEntry treatment, List<Treatment> treatmentsFromHistory) {

        long proposedTime = DateTimeUtil.toMillisFromATD(treatment.atechDateTime);

        proposedTime += (this.pumpTime.timeDifference * 1000);

        treatment.phoneDateTime = proposedTime;

        List<Treatment> outList = new ArrayList<>();

        for (Treatment treatment1 : treatmentsFromHistory) {
            if ((treatment1.date > proposedTime - (5 * 60 * 1000))
                && (treatment1.date < proposedTime + (5 * 60 * 1000))) {
                outList.add(treatment1);
            }
        }

        if (outList.size() == 0) {
            return null;
        } else if (outList.size() == 1) {
            return outList.get(0);
        } else {
            LOG.error("TODO. Multiple options: {}", outList);

            Map<Treatment, Integer> data = new HashMap<>();

            for (Treatment treatment1 : outList) {
                int diff = Math.abs((int)(treatment1.date - proposedTime));
                data.put(treatment1, diff);
            }

            for (int i = 1; i < 5; i++) {

                List<Treatment> outList2 = new ArrayList<>();

                for (Treatment treatment1 : treatmentsFromHistory) {
                    if ((treatment1.date > proposedTime - (i * 60 * 1000))
                        && (treatment1.date < proposedTime + (i * 60 * 1000))) {
                        outList2.add(treatment1);
                    }
                }

                LOG.error("Treatment List: (timeDiff={},count={},list={})", (i * 60 * 1000), outList2.size(),
                    gsonPretty.toJson(outList2));

                if (outList2.size() == 1) {
                    return outList2.get(0);
                } else if (outList2.size() > 1) {

                    for (int j = 1; j < 6; j++) {

                        List<Treatment> outList3 = new ArrayList<>();

                        int ttt = (i * 60 * 1000) - (10 * j * 1000);

                        for (Treatment treatment1 : treatmentsFromHistory) {

                            if ((treatment1.date > proposedTime - ttt) && (treatment1.date < proposedTime + ttt)) {
                                outList3.add(treatment1);
                            }
                        }

                        LOG.error("Treatment List: (timeDiff={},count={},list={})", ttt, outList3.size(),
                            gsonPretty.toJson(outList3));

                        if (outList3.size() == 1) {
                            return outList3.get(0);
                        }
                    } // for

                } // outList2
            }

            // TODO
        } // outList

        // TODO
        return null;
    }


    private Treatment findTreatment(PumpHistoryEntry treatment, List<Treatment> treatmentsFromHistory,
            int dateDifference) {

        long proposedTime = DateTimeUtil.toMillisFromATD(treatment.atechDateTime);

        proposedTime += (this.pumpTime.timeDifference * 1000);

        // treatment.phoneDateTime = proposedTime;
        Date proposedTimeDD = new Date(proposedTime);

        if (treatmentsFromHistory.size() == 0) {
            return null;
        } else if (treatmentsFromHistory.size() == 1) {
            Treatment treatment1 = treatmentsFromHistory.get(0);
            LocalDateTime ldt = new LocalDateTime(treatment1.date);
            return treatmentsFromHistory.get(0);
        }

        for (int min = 0; min < 5; min++) {
            for (int sec = 0; sec < 60; sec += 10) {

                int diff = (min * 60 * 1000) + (sec * 1000);

                List<Treatment> outList = new ArrayList<>();

                for (Treatment treatment1 : treatmentsFromHistory) {

                    if ((treatment1.date > proposedTime - diff) && (treatment1.date < proposedTime + diff)) {
                        outList.add(treatment1);
                    }
                }

                LOG.error("Treatments: (timeDiff=[min={},sec={}],count={},list={})", min, sec, outList.size(),
                    gsonPretty.toJson(outList));

                if (outList.size() == 1) {
                    return outList.get(0);
                }

                if (min == 0 && sec == 10 && outList.size() > 1) {
                    LOG.error("Too many treatments (with too small diff): (timeDiff=[min={},sec={}],count={},list={})",
                        min, sec, outList.size(), gsonPretty.toJson(outList));

                }
            }
        }

        return null;

    }


    private void addBolus(PumpHistoryEntry bolus, Treatment treatment) {

        BolusDTO bolusDTO = (BolusDTO)bolus.getDecodedData().get("Object");

        if (treatment == null) {

            // treatment.carbs = detailedBolusInfo.carbs; // TODO later support BolusWizard ??

            switch (bolusDTO.getBolusType()) {
                case Normal: {
                    // DetailedBolusInfo normalBolus = new DetailedBolusInfo();

                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();

                    detailedBolusInfo.date = tryToGetByLocalTime(bolus.atechDateTime);
                    detailedBolusInfo.source = Source.PUMP;
                    detailedBolusInfo.pumpId = bolus.getPumpId();
                    detailedBolusInfo.insulin = bolusDTO.getDeliveredAmount();

                    boolean newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);

                    bolus.setLinkedObject(detailedBolusInfo);

                    // bolus.setLinkedObject(normalBolus);

                    // TreatmentsPlugin.getPlugin().addToHistoryTreatment(normalBolus, false);

                    if (L.isEnabled(L.PUMPCOMM))
                        LOG.debug("addBolus - [date={},pumpId={}, insulin={}, newRecord={}]", detailedBolusInfo.date,
                            detailedBolusInfo.pumpId, detailedBolusInfo.insulin, newRecord);
                }
                    break;

                case Audio:
                case Extended: {
                    ExtendedBolus extendedBolus = new ExtendedBolus();
                    extendedBolus.date = tryToGetByLocalTime(bolus.atechDateTime);
                    extendedBolus.source = Source.PUMP;
                    extendedBolus.insulin = bolusDTO.getDeliveredAmount();
                    extendedBolus.pumpId = bolus.getPumpId();
                    extendedBolus.isValid = true;
                    extendedBolus.durationInMinutes = bolusDTO.getDuration();

                    bolus.setLinkedObject(extendedBolus);

                    TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);

                    LOG.debug("addBolus - Extended [date={},pumpId={}, insulin={}, duration={}]", extendedBolus.date,
                        extendedBolus.pumpId, extendedBolus.insulin, extendedBolus.durationInMinutes);

                }
                    break;

            }

        } else {

            boolean old = false;

            // if (old) {
            // treatment.insulin = bolusDTO.getDeliveredAmount();
            // treatment.pumpId = bolus.getPumpId();
            // treatment.source = Source.PUMP;
            //
            // bolus.setLinkedObject(treatment);
            //
            // TreatmentsPlugin.getPlugin().getService().createOrUpdate(treatment);
            //
            // LOG.debug("editBolus - [date={},pumpId={}, insulin={}]", treatment.date, treatment.pumpId,
            // treatment.insulin);
            //
            // MainApp.bus().post(new EventTreatmentChange(treatment));
            // } else {

            DetailedBolusInfo detailedBolusInfo = DetailedBolusInfoStorage.findDetailedBolusInfo(treatment.date);
            if (detailedBolusInfo == null) {
                detailedBolusInfo = new DetailedBolusInfo();
            }

            detailedBolusInfo.date = treatment.date;
            detailedBolusInfo.source = Source.PUMP;
            detailedBolusInfo.pumpId = bolus.getPumpId();
            detailedBolusInfo.insulin = bolusDTO.getDeliveredAmount();

            boolean newRecord = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);

            bolus.setLinkedObject(detailedBolusInfo);

            if (L.isEnabled(L.PUMPCOMM))
                LOG.debug("editBolus - [date={},pumpId={}, insulin={}, newRecord={}]", detailedBolusInfo.date,
                    detailedBolusInfo.pumpId, detailedBolusInfo.insulin, newRecord);
            // }
        }

    }


    private DetailedBolusInfo createTreatment(long date, double amount, long pumpId, Boolean isSmb, Treatment treatment) {
        DetailedBolusInfo normalBolus = new DetailedBolusInfo();
        normalBolus.date = date;

        if (treatment != null) {
            normalBolus.carbs = treatment.carbs;
            normalBolus.date = treatment.date;
        }

        normalBolus.source = Source.PUMP;
        normalBolus.insulin = amount;
        normalBolus.pumpId = pumpId;
        normalBolus.isValid = true;
        normalBolus.isSMB = isSmb == null ? false : isSmb;

        return normalBolus;
    }


    // TODO needs to be implemented
    public void processTBRs(List<PumpHistoryEntry> treatments) {

        int dateDifference = getOldestDateDifference(treatments);

        // List<Treatment> treatmentsFromHistory = TreatmentsPlugin.getPlugin().getTreatmentsFromHistoryXMinutesAgo(
        // dateDifference);

        for (PumpHistoryEntry treatment : treatments) {

            LOG.debug("TOE. Treatment: " + treatment);
            long inLocalTime = tryToGetByLocalTime(treatment.atechDateTime);

        }

    }


    // TODO needs to be implemented
    public void processSuspends(List<PumpHistoryEntry> treatments) {

    }


    // TODO needs to be implemented
    public List<PumpHistoryEntry> getSuspends() {
        return new ArrayList<PumpHistoryEntry>();
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

        LocalDateTime ldt = DateTimeUtil.toLocalDateTime(atechDateTime);

        // LOG.debug("TOE. Time of Entry: " + atechDateTime);
        // LOG.debug("TOE.   Clock Pump: " + pumpTime.pumpTime.toString("HH:mm:ss"));
        // LOG.debug("TOE.   LocalTime: " + pumpTime.localDeviceTime.toString("HH:mm:ss"));
        // LOG.debug("TOE.   Difference(s): " + pumpTime.timeDifference);

        ldt = ldt.plusSeconds(pumpTime.timeDifference);
        ldt = ldt.millisOfSecond().setCopy(000);

        // LOG.debug("TOE. New Time Of Entry: " + ldt.toString("HH:mm:ss"));

        LOG.debug("tryToGetByLocalTime: [TimeOfEntry={}, ClockPump={}, LocalTime={}, DifferenceSec={}, "
            + "NewTimeOfEntry={}, time={}", atechDateTime, pumpTime.pumpTime.toString("HH:mm:ss"),
            pumpTime.localDeviceTime.toString("HH:mm:ss"), pumpTime.timeDifference, ldt.toString("HH:mm:ss"), ldt
                .toDate().getTime());

        return ldt.toDate().getTime();
    }


    private int getOldestDateDifference(List<PumpHistoryEntry> treatments) {

        long dt = Long.MAX_VALUE;

        for (PumpHistoryEntry treatment : treatments) {

            if (treatment.atechDateTime < dt) {
                dt = treatment.atechDateTime;
            }
        }

        // LOG.debug("Oldest entry: {}, pumpTimeDifference={}", dt, this.pumpTime.timeDifference);

        LocalDateTime oldestEntryTime = DateTimeUtil.toLocalDateTime(dt);
        oldestEntryTime = oldestEntryTime.minusMinutes(5);

        if (this.pumpTime.timeDifference < 0) {
            oldestEntryTime = oldestEntryTime.plusSeconds(this.pumpTime.timeDifference);
        }
        // } else {
        // d.minusSeconds(this.pumpTime.timeDifference);
        // }

        LocalDateTime now = new LocalDateTime();

        Minutes minutes = Minutes.minutesBetween(oldestEntryTime, now);

        // returns oldest time in history, with calculated time difference between pump and phone, minus 5 minutes
        LOG.debug("Oldest entry: {}, pumpTimeDifference={}, newDt={}, currentTime={}, differenceMin={}", dt,
            this.pumpTime.timeDifference, oldestEntryTime, now, minutes.getMinutes());

        return minutes.getMinutes();

    }


    // private void processTreatments(List<PumpHistoryEntry> treatments) {
    //
    // // FIXME bolus and tbr
    //
    // LOG.error(getLogPrefix() + "Treatments found: {}. Not processed.\n", treatments.size());
    //
    // //MainApp.getDbHelper().getTDDsForLastXDays()
    //
    // MedtronicHistoryData.showLogs(null, gsonInstancePretty.toJson(treatments));
    //
    // }

    private PumpHistoryEntryType getTDDType() {

        if (MedtronicUtil.getMedtronicPumpModel() == null) {
            return PumpHistoryEntryType.EndResultTotals;
        }

        switch (MedtronicUtil.getMedtronicPumpModel()) {

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


    /*
     * entryType == PumpHistoryEntryType.Bolus || // Treatments
     * entryType == PumpHistoryEntryType.TempBasalRate || //
     * entryType == PumpHistoryEntryType.TempBasalDuration || //
     * 
     * entryType == PumpHistoryEntryType.Prime || // Pump Status Change
     * entryType == PumpHistoryEntryType.PumpSuspend || //
     * entryType == PumpHistoryEntryType.PumpResume || //
     * entryType == PumpHistoryEntryType.Rewind || //
     * entryType == PumpHistoryEntryType.NoDeliveryAlarm || // no delivery
     * entryType == PumpHistoryEntryType.BasalProfileStart || //
     * 
     * entryType == PumpHistoryEntryType.ChangeTime || // Time Change
     * entryType == PumpHistoryEntryType.NewTimeSet || //
     * 
     * entryType == PumpHistoryEntryType.SelectBasalProfile || // Configuration
     * entryType == PumpHistoryEntryType.ClearSettings || //
     * entryType == PumpHistoryEntryType.SaveSettings || //
     * entryType == PumpHistoryEntryType.ChangeMaxBolus || //
     * entryType == PumpHistoryEntryType.ChangeMaxBasal || //
     * entryType == PumpHistoryEntryType.ChangeTempBasalType || //
     * 
     * entryType == PumpHistoryEntryType.ChangeBasalProfile_NewProfile || // Basal profile
     * 
     * entryType == PumpHistoryEntryType.DailyTotals512 || // Daily Totals
     * entryType == PumpHistoryEntryType.DailyTotals522 || //
     * entryType == PumpHistoryEntryType.DailyTotals523 || //
     * entryType == PumpHistoryEntryType.EndResultTotals
     */

    public boolean hasBasalProfileChanged() {

        List<PumpHistoryEntry> filteredItems = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile);

        LOG.debug("hasBasalProfileChanged. Items: " + filteredItems);

        return (filteredItems.size() > 0);

    }


    public void processLastBasalProfileChange(MedtronicPumpStatus mdtPumpStatus) {

        // FIXME

        List<PumpHistoryEntry> filteredItems = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile);

        LOG.debug("processLastBasalProfileChange. Items: " + filteredItems);

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
            LOG.debug("processLastBasalProfileChange. item found, setting new basalProfileLocally: " + newProfile);
            BasalProfile basalProfile = (BasalProfile)newProfile.getDecodedData().get("Object");
            mdtPumpStatus.basalsByHour = basalProfile.getProfilesByHour();
        }

        // boolean profileChanged = ((filteredItems.size() - basalProfileChangedInternally) > 0);

        // LOG.error("Profile changed:" + profileChanged);

        // this.basalProfileChangedInternally = 0;

        // return profileChanged;

    }


    public boolean hasPumpTimeChanged() {

        return getStateFromFilteredList(PumpHistoryEntryType.NewTimeSet, //
            PumpHistoryEntryType.ChangeTime);

    }


    public void setLastHistoryRecordTime(Long lastHistoryRecordTime) {

        // this.previousLastHistoryRecordTime = this.lastHistoryRecordTime;
        this.lastHistoryRecordTime = lastHistoryRecordTime;
    }


    public void setIsInInit(boolean init) {
        this.isInit = init;
    }


    // HELPER METHODS

    private void sort(List<PumpHistoryEntry> list) {
        Collections.sort(list, new PumpHistoryEntry.Comparator());
    }


    private List<PumpHistoryEntry> preProcessTBRs(List<PumpHistoryEntry> TBRs_Input) {
        List<PumpHistoryEntry> TBRs = new ArrayList<>();

        Map<String, PumpHistoryEntry> map = new HashMap<>();

        for (PumpHistoryEntry pumpHistoryEntry : TBRs_Input) {
            if (map.containsKey(pumpHistoryEntry.DT)) {
                MedtronicPumpHistoryDecoder.decodeTempBasal(map.get(pumpHistoryEntry.DT), pumpHistoryEntry);
                pumpHistoryEntry.setEntryType(PumpHistoryEntryType.TempBasalCombined);
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


    // private List<PumpHistoryEntry> getFilteredListByPreviousLastRecord(PumpHistoryEntryType... entryTypes) {
    // return getFilteredListByTime(this.previousLastHistoryRecordTime, entryTypes);
    // }

    private List<PumpHistoryEntry> getFilteredListByLastRecord(PumpHistoryEntryType... entryTypes) {
        return getFilteredListByTime(this.lastHistoryRecordTime, entryTypes);
    }


    private List<PumpHistoryEntry> getFilteredListByTime(Long lastRecordTime, PumpHistoryEntryType... entryTypes) {
        if (lastRecordTime == null) {
            return getFilteredItems(entryTypes);
        } else {
            return getFilteredItems(lastRecordTime, entryTypes);
        }
    }


    private boolean getStateFromFilteredList(PumpHistoryEntryType... entryTypes) {
        if (isInit) {
            return false;
        } else {
            List<PumpHistoryEntry> filteredItems = getFilteredItems(entryTypes);

            LOG.debug("Items: " + filteredItems);

            return filteredItems.size() > 0;
        }
    }


    private List<PumpHistoryEntry> getFilteredItems(Long dateTime, PumpHistoryEntryType... entryTypes) {

        PumpHistoryResult phr = new PumpHistoryResult(null, dateTime);
        return getFilteredItems(phr.getValidEntries(), entryTypes);

    }


    private List<PumpHistoryEntry> getFilteredItems(List<PumpHistoryEntry> inList, PumpHistoryEntryType... entryTypes) {

        // LOG.debug("InList: " + inList.size());
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

        // LOG.debug("OutList: " + outList.size());

        return outList;

    }


    private boolean isEmpty(PumpHistoryEntryType... entryTypes) {
        return (entryTypes == null || (entryTypes.length == 1 && entryTypes[0] == null));
    }


    // public List<PumpHistoryEntry> getNewHistoryEntries() {
    // return this.newHistory;
    // }

    // public void setBasalProfileChanged() {
    // this.basalProfileChangedInternally++;
    // }

    private String getLogPrefix() {
        return "MedtronicHistoryData::";
    }

}
