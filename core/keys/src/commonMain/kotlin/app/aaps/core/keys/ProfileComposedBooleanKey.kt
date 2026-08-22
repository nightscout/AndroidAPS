package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey

private const val LOCAL_PROFILE = "LocalProfile"

enum class ProfileComposedBooleanKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanComposedNonPreferenceKey {

    LocalProfileNumberedMgdl(LOCAL_PROFILE + "_mgdl_", "%d", false),
}
