package app.aaps.core.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

private const val LOCAL_PROFILE = "LocalProfile"

enum class ProfileIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true
) : IntNonPreferenceKey {

    AmountOfProfiles(LOCAL_PROFILE + "_profiles", 0),
}
