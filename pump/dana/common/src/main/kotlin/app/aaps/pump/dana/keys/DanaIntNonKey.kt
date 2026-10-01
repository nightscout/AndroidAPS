package app.aaps.pump.dana.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class DanaIntNonKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    Password("danar_password", 0),
}
