package info.nightscout.androidaps.plugins.PumpMedtronic.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import info.nightscout.androidaps.plugins.PumpCommon.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntryType;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;

//import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;

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


    public MedtronicHistoryData() {
        this.allHistory = new ArrayList<>();
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


    // TODO This logic might not be working correctly
    public boolean isPumpSuspended(Boolean wasPumpSuspended) {

        if (true)
            return false;

        List<PumpHistoryEntry> newAndAll = new ArrayList<>();
        newAndAll.addAll(this.allHistory);
        newAndAll.addAll(this.newHistory);

        this.sort(newAndAll);

        if (wasPumpSuspended == null) { // suspension status not known

            List<PumpHistoryEntry> items = getFilteredItems(PumpHistoryEntryType.Bolus, //
                PumpHistoryEntryType.TempBasalCombined, //
                PumpHistoryEntryType.Prime, //
                PumpHistoryEntryType.PumpSuspend, //
                PumpHistoryEntryType.PumpResume, //
                PumpHistoryEntryType.Rewind, //
                PumpHistoryEntryType.NoDeliveryAlarm, //
                PumpHistoryEntryType.BasalProfileStart);

            if (items.size() == 0)
                return wasPumpSuspended == null ? false : wasPumpSuspended;

            PumpHistoryEntry pumpHistoryEntry = items.get(0);

            return !(pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.TempBasalCombined || //
                pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.BasalProfileStart || //
                pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.Bolus || //
            pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.PumpResume);

        } else {

            List<PumpHistoryEntry> items = getFilteredItems(PumpHistoryEntryType.Bolus, //
                PumpHistoryEntryType.TempBasalCombined, //
                PumpHistoryEntryType.Prime, //
                PumpHistoryEntryType.PumpSuspend, //
                PumpHistoryEntryType.PumpResume, //
                PumpHistoryEntryType.Rewind, //
                PumpHistoryEntryType.NoDeliveryAlarm, //
                PumpHistoryEntryType.BasalProfileStart);

            if (wasPumpSuspended) {

                if (items.size() == 0)
                    return wasPumpSuspended == null ? false : wasPumpSuspended;

                PumpHistoryEntry pumpHistoryEntry = items.get(0);

                if (pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.TempBasalCombined || //
                    pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.BasalProfileStart || //
                    pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.Bolus || //
                    pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.PumpResume)
                    return false;
                else
                    return true;

            } else {

                if (items.size() == 0)
                    return wasPumpSuspended == null ? false : wasPumpSuspended;

                PumpHistoryEntry pumpHistoryEntry = items.get(0);

                if (pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.NoDeliveryAlarm || //
                    pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.PumpSuspend || //
                    pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.Prime)
                    return true;

            }

        }

        // FIXME
        return false;

    }


    public List<PumpHistoryEntry> getTDDs() {

        return getFilteredListByLastRecord(PumpHistoryEntryType.EndResultTotals);

    }


    // FIXME remove
    public List<PumpHistoryEntry> getTDDs2() {

        return getFilteredListByLastRecord(PumpHistoryEntryType.DailyTotals515, PumpHistoryEntryType.DailyTotals522,
            PumpHistoryEntryType.DailyTotals523);

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
}
