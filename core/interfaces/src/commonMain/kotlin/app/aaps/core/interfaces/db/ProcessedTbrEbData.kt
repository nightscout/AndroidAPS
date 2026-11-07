package app.aaps.core.interfaces.db

import app.aaps.core.data.model.TB

interface ProcessedTbrEbData {

    /**
     * Get running temporary basal at time
     *
     *  @return     running temporary basal or null if no tbr is running
     *              If pump is faking extended boluses as temporary basals
     *              return extended converted to temporary basal with type == FAKE_EXTENDED
     */
    suspend fun getTempBasalIncludingConvertedExtended(timestamp: Long): TB?

    /**
     * [getTempBasalIncludingConvertedExtended] for many times between [from] and [to], from one read
     * of the database. For code that walks a range step by step, like a graph: one query per step was
     * the cost. Asking for a time outside [from]..[to] is not supported.
     */
    suspend fun getTempBasalsIncludingConvertedExtended(from: Long, to: Long): TempBasalsInRange

    /** The temporary basals of a range, see [getTempBasalsIncludingConvertedExtended]. */
    interface TempBasalsInRange {

        /** The same answer as [getTempBasalIncludingConvertedExtended] for [timestamp]. */
        suspend fun at(timestamp: Long): TB?
    }
}