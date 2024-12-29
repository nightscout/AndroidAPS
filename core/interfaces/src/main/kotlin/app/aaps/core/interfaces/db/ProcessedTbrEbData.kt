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
    fun getTempBasalIncludingConvertedExtended(timestamp: Long): TB?

    /**
     * Get running temporary basals for given time range, sliced by calculationStep.
     * For each step between given range it calculates equivalent of getTempBasalIncludingConvertedExtended
     *
     *  @param startTime start of calculated period, timestamp
     *  @param endTime end of calculated period, timestamp
     *  @param calculationStep calculation step, in millisecond
     *  @return map where for each step, its timestamp is a key and calculated optional temporary basal is a value
     */
    fun getTempBasalIncludingConvertedExtendedForRange(startTime: Long, endTime: Long, calculationStep: Long): Map<Long, TB?>

}