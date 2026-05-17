package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

enum class MedtrumBooleanNonKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanNonPreferenceKey {

    PatchPrimed("patch_primed_flag", defaultValue = false),
}
