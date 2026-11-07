package app.aaps.core.interfaces.smoothing

import app.aaps.core.data.iob.InMemoryGlucoseValue

interface Smoothing {

    /**
     * Smooth values in List
     *
     * @param data  input glucose values ([0] to be the most recent one)
     *
     * @return new List with smoothed values (smoothed values are stored in [InMemoryGlucoseValue.smoothed])
     */
    fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue>
}