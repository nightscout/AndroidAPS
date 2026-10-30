package app.aaps.plugins.sync.garmin.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class GarminIntNonKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    /** Last total step count of the day that the device sent over HTTP. -1 means: nothing received yet. */
    LastTotalSteps("garmin_http_last_steps", -1),
}
