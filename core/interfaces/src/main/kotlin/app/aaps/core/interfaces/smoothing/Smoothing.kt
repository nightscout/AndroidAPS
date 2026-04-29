package app.aaps.core.interfaces.smoothing

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualitySnapshot

interface Smoothing {

    /**
     * Smooth values in List
     *
     * @param data  input glucose values ([0] to be the most recent one)
     *
     * @return new List with smoothed values (smoothed values are stored in [InMemoryGlucoseValue.smoothed])
     */
    fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue>

    /**
     * Optional: last adaptive-smoothing quality snapshot (non-null only for plugins that support it).
     * Updated synchronously during [smooth] on the worker thread.
     */
    fun lastAdaptiveSmoothingQualitySnapshot(): AdaptiveSmoothingQualitySnapshot? = null

    /**
     * When true, overview / dashboard headline glucose should prefer [app.aaps.core.interfaces.aps.GlucoseStatus.glucose]
     * (bucket head / APS pipeline) over [app.aaps.core.data.iob.InMemoryGlucoseValue.recalculated] from [app.aaps.core.interfaces.overview.LastBgData]
     * when the latter can temporarily reflect a newer raw DB reading before smoothing is applied.
     */
    fun preferDashboardGlucoseFromGlucoseStatus(): Boolean = false
}