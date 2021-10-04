package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import java.time.Duration
import java.util.concurrent.TimeUnit

class PodConstants {
    companion object {
        val MAX_POD_LIFETIME : Duration = Duration.ofHours(80)

        // Expiration alerts duration in minutes
        const val POD_EXPIRATION_ALERT_MINUTES_DURATION = 60 * 7
        // Expiration alert time in minutes since activation
        const val POD_EXPIRATION_ALERT_MINUTES = 60 * 72
        // Expiration eminent alert time in minutes since activation
        const val POD_EXPIRATION_EMINENT_ALERT_MINUTES = 60 * 79

        // Bolus units
        const val POD_PULSE_BOLUS_UNITS = 0.05

        // Priming units
        const val POD_PRIMING_BOLUS_UNITS = 0.05
        const val POD_CANNULA_INSERTION_BOLUS_UNITS = 0.05

        // Reservoir units alert threshold
        const val DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD : Short = 20
    }
}
