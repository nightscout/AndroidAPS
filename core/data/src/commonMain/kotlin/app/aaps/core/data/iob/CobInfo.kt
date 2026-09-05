package app.aaps.core.data.iob

/** All COB up to now, including carbs not yet processed by IobCob calculation.  */
data class CobInfo(val timestamp: Long, val displayCob: Double?, val futureCarbs: Double)