package info.nightscout.interfaces.iob

/** All COB up to now, including carbs not yet processed by IobCob calculation.  */
class CobInfo(val timestamp: Long, val displayCob: Double?, val futureCarbs: Double)