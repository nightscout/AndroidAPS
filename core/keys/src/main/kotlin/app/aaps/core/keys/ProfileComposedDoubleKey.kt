package app.aaps.core.keys

import app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey

// private const val LOCAL_PROFILE = "LocalProfile"
// private const val DEFAULT_DIA = 5.0

enum class ProfileComposedDoubleKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleComposedNonPreferenceKey {
    // keep LocalProfileNumberedDia as example
    // LocalProfileNumberedDia(LOCAL_PROFILE + "_dia_", "%d", DEFAULT_DIA),
}
