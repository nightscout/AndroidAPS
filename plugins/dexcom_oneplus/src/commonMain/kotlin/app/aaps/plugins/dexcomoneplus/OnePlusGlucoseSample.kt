package app.aaps.plugins.dexcomoneplus

/**
 * Parsed glucose sample from the native One+ session.
 */
data class OnePlusGlucoseSample(
    val mgdl: Double,
    val timestampMs: Long,
    /**
     * Rate of change the sensor reports, in mg/dL per minute (EGV trend byte / 10), or null when the
     * sensor sends the invalid marker.
     *
     * The unit is in the name on purpose. This used to be a `String?` called `trendArrowRaw`, which
     * read like an arrow name — so the AAPS side fed it to `TrendArrow.fromString`, never matched a
     * label, and silently stored `TrendArrow.NONE` on every single ONE+ reading. Turning the slope
     * into an arrow is the ingest layer's job, see `DexcomOnePlusIngest.trendArrowFor`.
     */
    val trendSlopeMgdlPerMin: Double? = null,
    val sequence: Long? = null,
)
