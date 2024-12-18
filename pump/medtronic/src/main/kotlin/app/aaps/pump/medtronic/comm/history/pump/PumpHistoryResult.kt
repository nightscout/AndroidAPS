package app.aaps.pump.medtronic.comm.history.pump

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.DateTimeUtil

/**
 * History page contains data, sorted from newest to oldest (0=newest..n=oldest)
 *
 *
 * Created by andy on 9/23/18.
 */
class PumpHistoryResult(private val aapsLogger: AAPSLogger, searchEntry: PumpHistoryEntry?, targetDate: Long?) {

    var isSearchFinished = false
        private set
    private val searchEntry: PumpHistoryEntry? = null
    private var searchDate: Long? = null
    private var searchType = SearchType.None
    var unprocessedEntries: MutableList<PumpHistoryEntry> = ArrayList()
    var validEntries: MutableList<PumpHistoryEntry> = ArrayList()

    fun addHistoryEntries(entries: MutableList<PumpHistoryEntry> /*, page: Int*/) {
        unprocessedEntries = entries
        //aapsLogger.debug(LTag.PUMPCOMM,"PumpHistoryResult. Unprocessed entries: {}", MedtronicUtil.getGsonInstance().toJson(entries));
        processEntries()
    }

    // TODO Bug #145 need to check if we had timeChange that went -1, that situation needs to be evaluated separately
    private fun processEntries() {
        var olderEntries = 0
        unprocessedEntries.reverse()
        when (searchType) {
            SearchType.None      ->                 //aapsLogger.debug(LTag.PUMPCOMM,"PE. None search");
                validEntries.addAll(unprocessedEntries)

            SearchType.LastEntry -> {
                aapsLogger.debug(LTag.PUMPCOMM, "PE. Last entry search")

                //Collections.sort(this.unprocessedEntries, new PumpHistoryEntry.Comparator());
                aapsLogger.debug(LTag.PUMPCOMM, "PE. PumpHistoryResult. Search entry date: " + searchEntry!!.atechDateTime)
                //val date = searchEntry.atechDateTime
                for (unprocessedEntry in unprocessedEntries) {
                    if (unprocessedEntry == searchEntry) {
                        //aapsLogger.debug(LTag.PUMPCOMM,"PE. Item found {}.", unprocessedEntry);
                        isSearchFinished = true
                        break
                    }

                    //aapsLogger.debug(LTag.PUMPCOMM,"PE. Entry {} added.", unprocessedEntry);

                    validEntries.add(unprocessedEntry)
                }
                // TODO 5minutes back
            }

            SearchType.Date      -> {
                aapsLogger.debug(LTag.PUMPCOMM, "PE. Date search: Search date: $searchDate")
                for (unprocessedEntry in unprocessedEntries) {
                    if (unprocessedEntry.atechDateTime == 0L) {
                        aapsLogger.debug(LTag.PUMPCOMM, "PE. PumpHistoryResult. Search entry date: Entry with no date: $unprocessedEntry")
                        continue
                    }
                    if (unprocessedEntry.isAfter(searchDate!!)) {
                        validEntries.add(unprocessedEntry)
                    } else {
//                        aapsLogger.debug(LTag.PUMPCOMM,"PE. PumpHistoryResult. Not after.. Unprocessed Entry [year={},entry={}]",
//                                DateTimeUtil.getYear(unprocessedEntry.atechDateTime), unprocessedEntry);
                        if (DateTimeUtil.getYear(unprocessedEntry.atechDateTime) > 2015) olderEntries++
                    }
                }
                if (olderEntries > 0) {
                    //Collections.sort(this.validEntries, new PumpHistoryEntry.Comparator());
                    isSearchFinished = true
                }
            }
        }

        //aapsLogger.debug(LTag.PUMPCOMM,"PE. Valid Entries: {}", validEntries);
    }

    override fun toString(): String {
        return "PumpHistoryResult [unprocessed=" + unprocessedEntries.size +  //
            ", valid=" + validEntries.size +  //
            ", searchEntry=" + searchEntry +  //
            ", searchDate=" + searchDate +  //
            ", searchType=" + searchType +  //
            ", searchFinished=" + isSearchFinished +  //
            "]"
    }

    /**
     * Return latest entry (entry with highest date time)
     * @return
     */
    val latestEntry: PumpHistoryEntry?
        get() = if (validEntries.isEmpty()) null else validEntries[0]

    // val isSearchRequired: Boolean
    //     get() = searchType != SearchType.None

    internal enum class SearchType {
        None,  //
        LastEntry,  //
        Date
    }

    init {
        if (searchEntry != null) {
            /*
             * this.searchEntry = searchEntry;
             * this.searchType = SearchType.LastEntry;
             * aapsLogger.debug(LTag.PUMPCOMM,"PumpHistoryResult. Search parameters: Last Entry: " + searchEntry.atechDateTime + " type="
             * + searchEntry.getEntryType().name());
             */
            searchDate = searchEntry.atechDateTime
            searchType = SearchType.Date
            aapsLogger.debug(LTag.PUMPCOMM, "PumpHistoryResult. Search parameters: Date(with searchEntry): $targetDate")
        } else if (targetDate != null) {
            searchDate = targetDate
            searchType = SearchType.Date
            aapsLogger.debug(LTag.PUMPCOMM, "PumpHistoryResult. Search parameters: Date: $targetDate")
        }

        // this.unprocessedEntries = new ArrayList<>();
        //validEntries = ArrayList()
    }
}