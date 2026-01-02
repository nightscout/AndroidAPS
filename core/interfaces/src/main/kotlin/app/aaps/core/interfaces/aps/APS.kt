package app.aaps.core.interfaces.aps

import app.aaps.core.interfaces.configuration.ConfigExportImport
import app.aaps.core.interfaces.profile.Profile

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
     * Dedicated string for Sensitivity OKDialog in overview on ISF calculation ?
     * @return string or null if nothing to show
     */
    fun getSensitivityOverviewString(): String? = null

    /**
     * Calculate current ISF
     * @param profile Actual profile to get multiplier form [ProfileSealed.EPS]
     * @param caller Caller identification for logging purposes
     * @return isf or null if not available
     *
     * Remember calculation must be as fast as possible. It's called very often
     */
    fun getIsfMgdl(profile: Profile, caller: String): Double? = error("Not implemented")

    /**
     * Calculate ISF to specified timestamp
     * @param timestamp time
     * @param caller Caller identification for logging purposes
     * @return isf or null if not available
     *
     * Remember calculation must be as fast as possible. It's called very often
     */
    fun getAverageIsfMgdl(timestamp: Long, caller: String): Double? = error("Not implemented")

    /**
     * Calculate current IC
     * @param profile Actual profile to get multiplier form [ProfileSealed.EPS]
     * @return ic or null if not available
     */
    fun getIc(profile: Profile): Double? = error("Not implemented")

    /**
     * Calculate IC to specified timestamp
     * @param timestamp time
     * @param profile Actual profile to get multiplier form [ProfileSealed.EPS]
     * @return ic or null if not available
     */
    fun getIc(timestamp: Long, profile: Profile): Double? = error("Not implemented")

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

    /**
     * Provide glucose status calculation
     * @param allowOldData if true non current data will be allowed
     * @return [GlucoseStatus]
     */
    fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus?
}