package app.aaps.pump.omnipod.dash.driver.pod.definition

import java.time.Duration

class PodConstants {
    companion object {

        val MAX_POD_LIFETIME: Duration = Duration.ofHours(80)

        // Expiration alert time in hours before lifetime end
        const val POD_EXPIRATION_ALERT_HOURS_REMAINING_DEFAULT = 7L

        // Imminent expiration alert time in hours before lifetime end
        const val POD_EXPIRATION_IMMINENT_ALERT_HOURS_REMAINING = 1L

        // Bolus & Priming units
        const val POD_PULSE_BOLUS_UNITS = 0.05

        // Reservoir units alert threshold
        const val DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD: Short = 20
    }
}
