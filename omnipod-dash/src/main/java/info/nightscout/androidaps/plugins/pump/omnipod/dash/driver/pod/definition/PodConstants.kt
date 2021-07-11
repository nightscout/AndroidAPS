package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import java.time.Duration

class PodConstants {
    companion object {
        val MAX_POD_LIFETIME = Duration.ofMinutes(80)
    }
}
