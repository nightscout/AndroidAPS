package app.aaps.plugins.main.profile.keys

import app.aaps.core.data.configuration.Constants
import app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey

enum class ProfileComposedDoubleKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleComposedNonPreferenceKey {

    LocalProfileNumberedDia(Constants.LOCAL_PROFILE + "_dia_", "%d", Constants.defaultDIA),
}
