package info.nightscout.interfaces.smoothing

import info.nightscout.interfaces.iob.InMemoryGlucoseValue

interface Smoothing {

    /**
     * Smooth values in List
     *
     * @param data  input glucose values ([0] to be the most recent one)
     * @param updateWindow  amount of values to the past to smooth
     *
     * @return new List with smoothed values (smoothed values are stored in [InMemoryGlucoseValue.smoothed])
     */
    fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue>
}