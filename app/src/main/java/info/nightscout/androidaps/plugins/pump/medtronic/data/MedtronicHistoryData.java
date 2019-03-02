package info.nightscout.androidaps.plugins.pump.medtronic.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.gson.Gson;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.DailyTotalsDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
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

    private static final int OLD_HISTORY_SIZE = 50;

    private int basalProfileChangedInternally = 0;

    private Gson gsonPretty;


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

        this.newHistory = result.getValidEntries();

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
        // LocalDateTime localDateTime = new LocalDateTime();
        long atechDate = DateTimeUtil.toATechDate(new GregorianCalendar());

        for (PumpHistoryEntry pumpHistoryEntry : newHistory) {

            if (!this.allHistory.contains(pumpHistoryEntry)) {

                PumpHistoryEntryType type = pumpHistoryEntry.getEntryType();

                // if (PumpHistoryEntryType.isAAPSRelevantEntry(type)) {

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

        TBRs = processTBRs(TBRs);

        newHistory2.addAll(TBRs);

        this.newHistory = newHistory2;

        sort(this.newHistory);

        LOG.debug("New History entries found: {}", this.newHistory.size());
        showLogs("List of history (after filtering): ", MedtronicPumpPlugin.gsonInstance.toJson(this.newHistory));

    }


    // FIXME not just 50 records, last 24 hours
    public void finalizeNewHistoryRecords() {

        List<PumpHistoryEntry> filteredListByLastRecord = getFilteredListByLastRecord((PumpHistoryEntryType)null);

        LOG.debug("New records: " + filteredListByLastRecord.size());

        if (filteredListByLastRecord.size() == 0)
            return;

        // List<PumpHistoryEntry> outList = new ArrayList<>();

        // if (allHistory.size() > OLD_HISTORY_SIZE) {
        // for (int i = 0; i < OLD_HISTORY_SIZE; i++) {
        // outList.add(allHistory.get(i));
        // }
        // } else {
        //
        // }

        // FIXME keep 24h only

        LOG.debug("All History records (before): " + allHistory.size());

        for (PumpHistoryEntry pumpHistoryEntry : filteredListByLastRecord) {

            if (!this.allHistory.contains(pumpHistoryEntry)) {
                this.allHistory.add(pumpHistoryEntry);
            }
        }

        // outList.addAll(this.allHistory);
        // outList.addAll(filteredListByLastRecord);
        //
        // this.allHistory.clear();
        //
        // this.allHistory.addAll(outList);
        //
        // this.sort(this.allHistory);

        LOG.debug("All History records (after): " + allHistory.size());

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

        if (col == null)
            return true;

        return col.isEmpty();
    }


    // TODO This logic might not be working correctly
    public boolean isPumpSuspended(Boolean wasPumpSuspended) {

        // if (true)
        // return false;

        List<PumpHistoryEntry> newAndAll = new ArrayList<>();

        if (!isCollectionEmpty(this.allHistory)) {
            newAndAll.addAll(this.allHistory);
        }

        if (!isCollectionEmpty(this.newHistory)) {
            newAndAll.addAll(this.newHistory);
        }

        if (newAndAll.isEmpty())
            return false;

        this.sort(newAndAll);

        List<PumpHistoryEntry> items = getFilteredItems(newAndAll, //
            PumpHistoryEntryType.Bolus, //
            PumpHistoryEntryType.TempBasalCombined, //
            PumpHistoryEntryType.Prime, //
            PumpHistoryEntryType.PumpSuspend, //
            PumpHistoryEntryType.PumpResume, //
            PumpHistoryEntryType.Rewind, //
            PumpHistoryEntryType.NoDeliveryAlarm, //
            PumpHistoryEntryType.BasalProfileStart);

        showLogs("isPumpSuspendCheck: ", MedtronicPumpPlugin.gsonInstancePretty.toJson(items));

        PumpHistoryEntryType pumpHistoryEntryType = items.get(0).getEntryType();

        LOG.debug("Last entry type: {}", pumpHistoryEntryType);

        return !(pumpHistoryEntryType == PumpHistoryEntryType.TempBasalCombined || //
            pumpHistoryEntryType == PumpHistoryEntryType.BasalProfileStart || //
            pumpHistoryEntryType == PumpHistoryEntryType.Bolus || //
            pumpHistoryEntryType == PumpHistoryEntryType.PumpResume || //
        pumpHistoryEntryType == PumpHistoryEntryType.Prime);

        // if (wasPumpSuspended == null) { // suspension status not known
        //
        // List<PumpHistoryEntry> items = getFilteredItems(PumpHistoryEntryType.Bolus, //
        // PumpHistoryEntryType.TempBasalCombined, //
        // PumpHistoryEntryType.Prime, //
        // PumpHistoryEntryType.PumpSuspend, //
        // PumpHistoryEntryType.PumpResume, //
        // PumpHistoryEntryType.Rewind, //
        // PumpHistoryEntryType.NoDeliveryAlarm, //
        // PumpHistoryEntryType.BasalProfileStart);
        //
        // if (items.size() == 0)
        // return wasPumpSuspended == null ? false : wasPumpSuspended;
        //
        // PumpHistoryEntry pumpHistoryEntry = items.get(0);
        //
        // return !(pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.TempBasalCombined || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.BasalProfileStart || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.Bolus || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.PumpResume);
        //
        // } else {
        //
        // List<PumpHistoryEntry> items = getFilteredItems(PumpHistoryEntryType.Bolus, //
        // PumpHistoryEntryType.TempBasalCombined, //
        // PumpHistoryEntryType.Prime, //
        // PumpHistoryEntryType.PumpSuspend, //
        // PumpHistoryEntryType.PumpResume, //
        // PumpHistoryEntryType.Rewind, //
        // PumpHistoryEntryType.NoDeliveryAlarm, //
        // PumpHistoryEntryType.BasalProfileStart);
        //
        // if (wasPumpSuspended) {
        //
        // if (items.size() == 0)
        // return wasPumpSuspended == null ? false : wasPumpSuspended;
        //
        // PumpHistoryEntry pumpHistoryEntry = items.get(0);
        //
        // if (pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.TempBasalCombined || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.BasalProfileStart || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.Bolus || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.PumpResume)
        // return false;
        // else
        // return true;
        //
        // } else {
        //
        // if (items.size() == 0)
        // return wasPumpSuspended == null ? false : wasPumpSuspended;
        //
        // PumpHistoryEntry pumpHistoryEntry = items.get(0);
        //
        // if (pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.NoDeliveryAlarm || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.PumpSuspend || //
        // pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.Prime)
        // return true;
        //
        // }
        //
        // }

        // FIXME
        // return false;

    }


    /**
     * Process History Data: Boluses(Treatments), TDD, TBRs, Suspend-Resume (or other pump stops: battery, prime)
     */

    public void processNewHistoryData() {

        // TDD
        List<PumpHistoryEntry> tdds = getFilteredListByLastRecord(getTDDType());

        LOG.debug("ProcessHistoryData: TDD [count={}, items={}]", tdds.size(), gsonPretty.toJson(tdds));

        if (!isCollectionEmpty(tdds)) {
            processTDDs(tdds);
        }

        // Bolus
        List<PumpHistoryEntry> treatments = getFilteredListByLastRecord(PumpHistoryEntryType.Bolus);

        LOG.debug("ProcessHistoryData: Bolus [count={}, items=", treatments.size());
        showLogs(null, gsonPretty.toJson(treatments));

        if (treatments.size() > 0) {
            // processTreatments(treatments);
        }

        // TBR
        List<PumpHistoryEntry> tbrs = getFilteredListByLastRecord(PumpHistoryEntryType.TempBasalCombined);

        LOG.debug("ProcessHistoryData: TBRs [count={}, items=", tbrs.size());
        showLogs(null, gsonPretty.toJson(tbrs));

        if (tbrs.size() > 0) {
            // processTreatments(treatments);
        }

        // Fake TBR
    }


    public void processTDDs(List<PumpHistoryEntry> tdds) {

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


    private TDD findTDD(long atechDateTime, List<TDD> tddsDb) {

        for (TDD tdd : tddsDb) {

            if (DateTimeUtil.isSameDayATDAndMillis(atechDateTime, tdd.date)) {
                return tdd;
            }
        }

        return null;
    }


    private void processTreatments(List<PumpHistoryEntry> treatments) {

        TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();

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

    // public List<PumpHistoryEntry> getTDDs() {
    //
    // return getFilteredListByLastRecord(getTDDType());
    //
    // }

    private PumpHistoryEntryType getTDDType() {

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


    public List<PumpHistoryEntry> getTreatments() {

        return getFilteredListByLastRecord( //
            PumpHistoryEntryType.Bolus, //
            PumpHistoryEntryType.TempBasalCombined);

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
    @Deprecated
    public boolean hasBasalProfileChanged_Old() {

        List<PumpHistoryEntry> filteredItems = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile);

        LOG.debug("Items: " + filteredItems);

        boolean profileChanged = ((filteredItems.size() - basalProfileChangedInternally) > 0);

        LOG.error("Profile changed:" + profileChanged);

        this.basalProfileChangedInternally = 0;

        return profileChanged;

    }


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


    private List<PumpHistoryEntry> processTBRs(List<PumpHistoryEntry> TBRs_Input) {
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


    public List<PumpHistoryEntry> getNewHistoryEntries() {
        return this.newHistory;
    }


    public void setBasalProfileChanged() {
        this.basalProfileChangedInternally++;
    }


    private String getLogPrefix() {
        return "MedtronicHistoryData::";
    }

}
