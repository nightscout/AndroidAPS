package app.aaps.plugins.main.profile.keys

import app.aaps.core.data.configuration.Constants
import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class ProfileIntKey(
    override val key: String,
    override val defaultValue: Int
) : IntNonPreferenceKey {

    AmountOfProfiles(Constants.LOCAL_PROFILE + "_profiles", 0),
}
