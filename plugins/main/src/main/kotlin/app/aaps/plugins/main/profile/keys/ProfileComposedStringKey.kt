package app.aaps.plugins.main.profile.keys

import app.aaps.core.data.configuration.Constants
import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey
import app.aaps.plugins.main.profile.ProfilePlugin.Companion.DEFAULT_ARRAY

enum class ProfileComposedStringKey(
    override val key: String,
    override val format: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringComposedNonPreferenceKey {

    LocalProfileNumberedIsf(Constants.LOCAL_PROFILE + "_isf_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedIc(Constants.LOCAL_PROFILE + "_ic_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedBasal(Constants.LOCAL_PROFILE + "_basal_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedTargetLow(Constants.LOCAL_PROFILE + "_targetlow_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedTargetHigh(Constants.LOCAL_PROFILE + "_targethigh_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedName(Constants.LOCAL_PROFILE + "_name_", "%d", Constants.LOCAL_PROFILE + "0"),
}
