package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

/** Only what the pump cannot tell again after an app restart is kept. What only this phone knows is not exported. */
enum class AaruyBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true,
) : BooleanNonPreferenceKey {

    /** False after pairing until the old log of the pump is skipped. */
    PumpSaveHistory("key_aaruy_save_history", false, exportable = false),
}
