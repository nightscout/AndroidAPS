package app.aaps.core.keys

import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey

private const val LOCAL_PROFILE = "LocalProfile"
private const val DEFAULT_ARRAY = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0}]"

enum class ProfileComposedStringKey(
    override val key: String,
    override val format: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringComposedNonPreferenceKey {

    LocalProfileNumberedIsf(LOCAL_PROFILE + "_isf_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedIc(LOCAL_PROFILE + "_ic_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedBasal(LOCAL_PROFILE + "_basal_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedTargetLow(LOCAL_PROFILE + "_targetlow_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedTargetHigh(LOCAL_PROFILE + "_targethigh_", "%d", DEFAULT_ARRAY),
    LocalProfileNumberedName(LOCAL_PROFILE + "_name_", "%d", LOCAL_PROFILE + "0"),
}
