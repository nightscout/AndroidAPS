package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey

enum class BooleanComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean
) : BooleanComposedNonPreferenceKey {

    Log("log_", "%s", false)
}