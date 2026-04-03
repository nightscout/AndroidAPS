package app.aaps.core.interfaces.pump

import org.json.JSONObject

interface PumpStatusProvider {

    /**
     * Short info for SMS, Wear etc.
     */
    suspend fun shortStatus(veryShort: Boolean): String

    /**
     * Generate JSON status of pump sent to the NS
     */
    suspend fun generatePumpJsonStatus(): JSONObject
}
