package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import java.time.Duration

class PodConstants {
    companion object {
        val MAX_POD_LIFETIME : Duration = Duration.ofHours(80)

        const val POD_PULSE_BOLUS_UNITS = 0.05
        // Priming
        const val POD_PRIMING_BOLUS_UNITS = 0.05
        const val POD_CANNULA_INSERTION_BOLUS_UNITS = 0.05
        // Reservoir
        const val DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD : Short = 20
    }
}
