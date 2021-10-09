package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import java.time.Duration

class PodConstants {
    companion object {
        val MAX_POD_LIFETIME: Duration = Duration.ofHours(80)

        // Expiration alert time in minutes since activation and  duration in minutes
        const val POD_EXPIRATION_ALERT_HOURS = 72L
        const val POD_EXPIRATION_ALERT_HOURS_DURATION = 7L

        // Expiration eminent alert time in minutes since activation
        const val POD_EXPIRATION_IMMINENT_ALERT_HOURS = 79L

        // Bolus & Priming units
        const val POD_PULSE_BOLUS_UNITS = 0.05

        // Reservoir units alert threshold
        const val DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD: Short = 20
    }
}
