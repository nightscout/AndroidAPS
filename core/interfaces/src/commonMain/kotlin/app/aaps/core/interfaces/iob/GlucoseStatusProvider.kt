package app.aaps.core.interfaces.iob

import app.aaps.core.interfaces.aps.GlucoseStatus

interface GlucoseStatusProvider {

    /**
     * Glucose status data calculated by APS plugin.
     */
    val glucoseStatusData: GlucoseStatus?

    /**
     * Provide glucose status calculation
     * @param allowOldData if true non current data will be allowed
     * @return [GlucoseStatus]
     */
    fun getGlucoseStatusData(allowOldData: Boolean = false): GlucoseStatus?
}