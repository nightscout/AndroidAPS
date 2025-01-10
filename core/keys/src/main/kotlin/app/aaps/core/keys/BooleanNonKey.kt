package app.aaps.core.keys

enum class BooleanNonKey(
    override val key: String,
    override val defaultValue: Boolean,
) : BooleanNonPreferenceKey {

    SetupWizardIUnderstand("I_understand", false)
}