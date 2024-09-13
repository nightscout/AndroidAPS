package app.aaps.core.interfaces.aps

import app.aaps.core.interfaces.configuration.ConfigExportImport

interface APS : ConfigExportImport {

    /**
     * Algorithm used
     */
    val algorithm: APSResult.Algorithm

    /**
     * Result of last invocation
     */
    val lastAPSResult: APSResult?

    /**
     * Timestamp of last invocation
     */
    val lastAPSRun: Long

    /**
     * Is APS providing variable ISF calculation?
     * @return true if yes
     */
    fun supportsDynamicIsf(): Boolean = false

    /**
     * Is APS providing variable IC calculation?
     * @return true if yes
     */
    fun supportsDynamicIc(): Boolean = false

    /**
     * Calculate current ISF
     * @param multiplier Multiplier from profile switch. Algorithm can take it into a count or ignore
     * @param timeShift Time shift from profile switch. Algorithm can take it into a count or ignore
     * @return isf or null if not available
     *
     * Remember calculation must be as fast as possible. It's called very often
     */
    fun getIsfMgdl(multiplier: Double, timeShift: Int, caller: String): Double? = error("Not implemented")

    /**
     * Calculate ISF to specified timestamp
     * @param timestamp time
     * @return isf or null if not available
     *
     * Remember calculation must be as fast as possible. It's called very often
     */
    fun getAverageIsfMgdl(timestamp: Long, caller: String): Double? = error("Not implemented")

    /**
     * Calculate current IC
     * @param multiplier Multiplier from profile switch. Algorithm can take it into a count or ignore
     * @param timeShift Time shift from profile switch. Algorithm can take it into a count or ignore
     * @return ic or null if not available
     */
    fun getIc(multiplier: Double, timeShift: Int): Double? = error("Not implemented")

    /**
     * Calculate IC to specified timestamp
     * @param timestamp time
     * @param multiplier Multiplier from profile switch. Algorithm can take it into a count or ignore
     * @param timeShift Time shift from profile switch. Algorithm can take it into a count or ignore
     * @return ic or null if not available
     */
    fun getIc(timestamp: Long, multiplier: Double, timeShift: Int): Double? = error("Not implemented")

    /**
     * Is plugin enabled?
     * Overlap with [PluginBase::isEnabled] to avoid type conversion
     */
    fun isEnabled(): Boolean

    /**
     * Invoke algorithm
     * @param initiator caller
     * @param tempBasalFallback if true previous enact of SMB failed. Try calculation without SMB
     */
    fun invoke(initiator: String, tempBasalFallback: Boolean)
}