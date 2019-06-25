package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;

/**
 * Created by andy on 9/23/18.
 */

/**
 * History page contains data, sorted from newest to oldest (0=newest..n=oldest)
 */
public class PumpHistoryResult {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    private boolean searchFinished = false;
    private PumpHistoryEntry searchEntry = null;
    private Long searchDate = null;
    private SearchType searchType = SearchType.None;
    public List<PumpHistoryEntry> unprocessedEntries;
    public List<PumpHistoryEntry> validEntries;


    // private Object validValues;

    public PumpHistoryResult(PumpHistoryEntry searchEntry, Long targetDate) {
        if (searchEntry != null) {
            /*
             * this.searchEntry = searchEntry;
             * this.searchType = SearchType.LastEntry;
             * LOG.debug("PumpHistoryResult. Search parameters: Last Entry: " + searchEntry.atechDateTime + " type="
             * + searchEntry.getEntryType().name());
             */
            this.searchDate = searchEntry.atechDateTime;
            this.searchType = SearchType.Date;
            if (isLogEnabled())
                LOG.debug("PumpHistoryResult. Search parameters: Date(with searchEntry): " + targetDate);
        } else if (targetDate != null) {
            this.searchDate = targetDate;
            this.searchType = SearchType.Date;
            if (isLogEnabled())
                LOG.debug("PumpHistoryResult. Search parameters: Date: " + targetDate);
        }

        // this.unprocessedEntries = new ArrayList<>();
        this.validEntries = new ArrayList<>();
    }


    public void addHistoryEntries(List<PumpHistoryEntry> entries, int page) {
        this.unprocessedEntries = entries;
        //LOG.debug("PumpHistoryResult. Unprocessed entries: {}", MedtronicUtil.getGsonInstance().toJson(entries));
        processEntries();
    }

    // TODO Bug #145 need to check if we had timeChange that went -1, that situation needs to be evaluated separately
    public void processEntries() {
        int olderEntries = 0;

        Collections.reverse(this.unprocessedEntries);

        switch (searchType) {
            case None:
                //LOG.debug("PE. None search");
                this.validEntries.addAll(this.unprocessedEntries);
                break;

            case LastEntry: {
                LOG.debug("PE. Last entry search");

                //Collections.sort(this.unprocessedEntries, new PumpHistoryEntry.Comparator());

                LOG.debug("PE. PumpHistoryResult. Search entry date: " + searchEntry.atechDateTime);

                Long date = searchEntry.atechDateTime;

                for (PumpHistoryEntry unprocessedEntry : unprocessedEntries) {

                    if (unprocessedEntry.equals(searchEntry)) {
                        //LOG.debug("PE. Item found {}.", unprocessedEntry);
                        searchFinished = true;
                        break;
                    }

                    //LOG.debug("PE. Entry {} added.", unprocessedEntry);
                    this.validEntries.add(unprocessedEntry);
                }
            }
            break;
            case Date: {
                LOG.debug("PE. Date search: Search date: {}", this.searchDate);


                for (PumpHistoryEntry unprocessedEntry : unprocessedEntries) {

                    if (unprocessedEntry.atechDateTime == null || unprocessedEntry.atechDateTime == 0) {
                        LOG.debug("PE. PumpHistoryResult. Search entry date: Entry with no date: {}", unprocessedEntry);
                        continue;
                    }

                    if (unprocessedEntry.isAfter(this.searchDate)) {
                        this.validEntries.add(unprocessedEntry);
                    } else {
                        LOG.debug("PE. PumpHistoryResult. Not after.. Unprocessed Entry [year={},entry={}]",
                                DateTimeUtil.getYear(unprocessedEntry.atechDateTime), unprocessedEntry);

                        if (DateTimeUtil.getYear(unprocessedEntry.atechDateTime) > 2015)
                            olderEntries++;
                    }
                }

                if (olderEntries > 0) {
                    //Collections.sort(this.validEntries, new PumpHistoryEntry.Comparator());

                    searchFinished = true;
                }
            }
            break;

        } // switch

        //LOG.debug("PE. Valid Entries: {}", validEntries);
    }


    private void clearOrPrepareList() {
        if (this.validEntries == null)
            this.validEntries = new ArrayList<>();
        else
            this.validEntries.clear();
    }


    public String toString() {
        return "PumpHistoryResult [unprocessed=" + (unprocessedEntries != null ? "" + unprocessedEntries.size() : "0") + //
                ", valid=" + (validEntries != null ? "" + validEntries.size() : "0") + //
                ", searchEntry=" + searchEntry + //
                ", searchDate=" + searchDate + //
                ", searchType=" + searchType + //
                ", searchFinished=" + searchFinished + //
                "]";

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


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMPCOMM);
    }


}
