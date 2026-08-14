package app.aaps.core.interfaces.pump

import kotlinx.serialization.json.JsonObject

interface PumpStatusProvider {

    /**
     * Short info for SMS, Wear etc.
     */
    suspend fun shortStatus(veryShort: Boolean): String

    /**
     * Generate JSON status of pump sent to the NS
     */
    suspend fun generatePumpJsonStatus(): JsonObject
}
