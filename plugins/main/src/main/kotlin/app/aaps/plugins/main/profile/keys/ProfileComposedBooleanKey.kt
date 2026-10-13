package app.aaps.plugins.main.profile.keys

import app.aaps.core.data.configuration.Constants
import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey

enum class ProfileComposedBooleanKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanComposedNonPreferenceKey {

    LocalProfileNumberedMgdl(Constants.LOCAL_PROFILE + "_mgdl_", "%d", false),
}

