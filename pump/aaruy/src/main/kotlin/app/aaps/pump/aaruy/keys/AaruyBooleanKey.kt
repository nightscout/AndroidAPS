package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

enum class AaruyBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
) : BooleanNonPreferenceKey {

    PumpSaveHistory("key_aaruy_save_history", false),
    PumpAlreadyBack("key_already_back", false),
}
