package app.aaps.plugins.smoothing.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class UkfIntNonKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    SessionId("ukf_session_id", 0),
}