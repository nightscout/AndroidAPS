package info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.PumpCommon.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;

/**
 * Created by andy on 9/23/18.
 */

/**
 * History page contains data, sorted from newest to oldest (0=newest..n=oldest)
 */
public class PumpHistoryResult {

    private static final Logger LOG = LoggerFactory.getLogger(PumpHistoryResult.class);

    private boolean searchFinished = false;
    private PumpHistoryEntry searchEntry = null;
    private Long searchDate = null;
    private SearchType searchType = SearchType.None;
    private List<PumpHistoryEntry> unprocessedEntries;
    public List<PumpHistoryEntry> validEntries;


    // private Object validValues;

    public PumpHistoryResult(PumpHistoryEntry searchEntry, Long targetDate) {
        if (searchEntry != null) {
            this.searchEntry = searchEntry;
            this.searchType = SearchType.LastEntry;
            LOG.debug("PumpHistoryResult. Search parameters: Last Entry: " + searchEntry.atechDateTime + " type="
                + searchEntry.getEntryType().name());
        } else if (targetDate != null) {
            this.searchDate = targetDate;
            this.searchType = SearchType.Date;
            LOG.debug("PumpHistoryResult. Search parameters: Date: " + targetDate);
        }

        // this.unprocessedEntries = new ArrayList<>();
        this.validEntries = new ArrayList<>();
    }


    public void addHistoryEntries(List<PumpHistoryEntry> entries) {
        this.unprocessedEntries = entries;
        LOG.debug("PumpHistoryResult. Unprocessed entries: {}", MedtronicPumpPlugin.gsonInstance.toJson(entries));
        processEntries();
    }


    public void processEntries() {
        int olderEntries = 0;

        switch (searchType) {
            case None:
                this.validEntries.addAll(this.unprocessedEntries);
                // this.unprocessedEntries = null;
                break;

            case LastEntry: {
                if (this.validEntries == null)
                    this.validEntries = new ArrayList<>();

                Collections.sort(this.unprocessedEntries, new PumpHistoryEntry.Comparator());

                LOG.debug("PumpHistoryResult. Search entry date: " + searchEntry.atechDateTime);

                for (PumpHistoryEntry unprocessedEntry : unprocessedEntries) {

                    if (unprocessedEntry.equals(searchEntry)) {
                        searchFinished = true;
                        break;
                    }

                    this.validEntries.add(unprocessedEntry);
                }
            }
                break;
            case Date: {
                if (this.validEntries == null)
                    this.validEntries = new ArrayList<>();

                for (PumpHistoryEntry unprocessedEntry : unprocessedEntries) {
                    if (unprocessedEntry.isAfter(this.searchDate)) {
                        this.validEntries.add(unprocessedEntry);
                    } else {
                        if (DateTimeUtil.getYear(unprocessedEntry.atechDateTime) != 2000)
                            olderEntries++;
                    }
                }

                if (olderEntries > 0) {
                    Collections.sort(this.validEntries, new PumpHistoryEntry.Comparator());

                    searchFinished = true;
                }
            }
                break;

        } // switch

    }


    /**
     * Return latest entry (entry with highest date time)
     * 
     * @return
     */
    public PumpHistoryEntry getLatestEntry() {
        if (this.validEntries == null || this.validEntries.size() == 0)
            return null;
        else {
            return this.validEntries.get(0);
            // PumpHistoryEntry pumpHistoryEntry = this.validEntries.get(0);
            //
            // if (pumpHistoryEntry.getEntryType() == PumpHistoryEntryType.EndResultTotals)
            // return pumpHistoryEntry;
            // else
            // return this.validEntries.get(1);
        }
    }


    public boolean isSearchRequired() {
        return searchType != SearchType.None;
    }


    public boolean isSearchFinished() {
        return searchFinished;
    }


    public List<PumpHistoryEntry> getValidEntries() {
        return validEntries;
    }

    enum SearchType {
        None, //
        LastEntry, //
        Date
    }

}
