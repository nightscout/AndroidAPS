package info.nightscout.androidaps.plugins.PumpMedtronic.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import info.nightscout.androidaps.plugins.PumpCommon.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntryType;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryResult;

//import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;

/**
 * Created by andy on 10/12/18.
 */

public class MedtronicHistoryData {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicHistoryData.class);

    private List<PumpHistoryEntry> allHistory = null;
    private List<PumpHistoryEntry> newHistory = null;

    private LocalDateTime lastHistoryRecordTime;
    private boolean isInit = false;

    private static final int OLD_HISTORY_SIZE = 50;


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


    public void filterNewEntries() {

        List<PumpHistoryEntry> newHistory2 = new ArrayList<>();
        List<PumpHistoryEntry> TBRs = new ArrayList<>();
        LocalDateTime localDateTime = new LocalDateTime();

        for (PumpHistoryEntry pumpHistoryEntry : newHistory) {

            PumpHistoryEntryType type = pumpHistoryEntry.getEntryType();

            if (PumpHistoryEntryType.isAAPSRelevantEntry(type)) {

                if (type == PumpHistoryEntryType.TempBasalRate || type == PumpHistoryEntryType.TempBasalDuration) {
                    TBRs.add(pumpHistoryEntry);
                } else {

                    if (type == PumpHistoryEntryType.EndResultTotals) {
                        if (!DateTimeUtil.isSameDay(localDateTime, pumpHistoryEntry.getLocalDateTime())) {
                            newHistory2.add(pumpHistoryEntry);
                        }
                    } else {
                        newHistory2.add(pumpHistoryEntry);
                    }

                }
            }
        }

        TBRs = processTBRs(TBRs);

        newHistory2.addAll(TBRs);

        this.newHistory = newHistory2;

        sort(this.newHistory);

        LOG.debug("New History entries found: {}", this.newHistory.size());
        showLogs("List of history (after filtering): ", MedtronicPumpPlugin.gsonInstance.toJson(this.newHistory));

    }


    public void finalizeNewHistoryRecords() {

        List<PumpHistoryEntry> filteredListByLastRecord = getFilteredListByLastRecord((PumpHistoryEntryType)null);

        if (filteredListByLastRecord.size() == 0)
            return;

        List<PumpHistoryEntry> outList = new ArrayList<>();

        if (allHistory.size() > OLD_HISTORY_SIZE) {
            for (int i = 0; i < OLD_HISTORY_SIZE; i++) {
                outList.add(allHistory.get(i));
            }
        }

        outList.addAll(filteredListByLastRecord);

        this.allHistory.clear();

        this.allHistory.addAll(outList);

        this.sort(this.allHistory);

    }


    public boolean hasRelevantConfigurationChanged() {

        return getStateFromFilteredList( //
            PumpHistoryEntryType.SelectBasalProfile, //
            PumpHistoryEntryType.ClearSettings, //
            PumpHistoryEntryType.SaveSettings, //
            PumpHistoryEntryType.ChangeMaxBolus, //
            PumpHistoryEntryType.ChangeMaxBasal, //
            PumpHistoryEntryType.ChangeTempBasalType);

    }


    // TODO This logic might not be working correctly
    public boolean isPumpSuspended(Boolean wasPumpSuspended) {

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
     * entryType == PumpHistoryEntryType.SelectBasalProfile || // Settings
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

        return getStateFromFilteredList(PumpHistoryEntryType.ChangeBasalProfile_NewProfile);

    }


    public boolean hasPumpTimeChanged() {

        return getStateFromFilteredList(PumpHistoryEntryType.NewTimeSet, //
            PumpHistoryEntryType.ChangeTime);

    }


    public void setLastHistoryRecordTime(LocalDateTime lastHistoryRecordTime) {

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


    private List<PumpHistoryEntry> getFilteredListByLastRecord(PumpHistoryEntryType... entryTypes) {
        if (this.lastHistoryRecordTime == null) {
            return getFilteredItems(entryTypes);
        } else {
            return getFilteredItems(this.lastHistoryRecordTime, entryTypes);
        }
    }


    private boolean getStateFromFilteredList(PumpHistoryEntryType... entryTypes) {
        if (isInit) {
            return false;
        } else {
            List<PumpHistoryEntry> filteredItems = getFilteredItems(entryTypes);

            return filteredItems.size() > 0;
        }
    }


    private List<PumpHistoryEntry> getFilteredItems(LocalDateTime dateTime, PumpHistoryEntryType... entryTypes) {

        PumpHistoryResult phr = new PumpHistoryResult(null, dateTime);
        return getFilteredItems(phr.getValidEntries(), entryTypes);

    }


    private List<PumpHistoryEntry> getFilteredItems(List<PumpHistoryEntry> inList, PumpHistoryEntryType... entryTypes) {

        // LOG.debug("InList: " + inList.size());
        List<PumpHistoryEntry> outList = new ArrayList<>();

        if (inList != null && inList.size() > 0) {
            for (PumpHistoryEntry pumpHistoryEntry : inList) {

                if (entryTypes != null) {
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


    public List<PumpHistoryEntry> getNewHistoryEntries() {
        return this.newHistory;
    }
}
