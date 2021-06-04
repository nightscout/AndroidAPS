package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import org.joda.time.Duration

class PodConstants {
    companion object {
        val MAX_POD_LIFETIME = Duration.standardHours(80)
    }
}