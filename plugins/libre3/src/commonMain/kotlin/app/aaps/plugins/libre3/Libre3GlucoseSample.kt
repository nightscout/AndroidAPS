package app.aaps.plugins.libre3

/**
 * One real time glucose reading from the native session.
 *
 * [lifeCount] is the sensor life counter in minutes. It is the sensor's own clock, so it is used
 * for de-duplication together with the sample time.
 */
data class Libre3GlucoseSample(
    val mgdl: Double,
    val timestampMs: Long,
    val lifeCount: Int,
    val trendArrowRaw: String? = null,
    val rateOfChangeMgdlPerMin: Double? = null,
)
