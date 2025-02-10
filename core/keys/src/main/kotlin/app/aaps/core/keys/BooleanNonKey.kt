package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

@Suppress("SpellCheckingInspection")
enum class BooleanNonKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanNonPreferenceKey {

    SetupWizardIUnderstand("I_understand", false),
    ObjectivesLoopUsed("ObjectivesLoopUsed", false),
    ObjectivesActionsUsed("ObjectivesActionsUsed", false),
    ObjectivesScaleUsed("ObjectivesScaleUsed", false),
    ObjectivesPumpStatusIsAvailableInNS("ObjectivespumpStatusIsAvailableInNS", false),
    ObjectivesBgIsAvailableInNs("ObjectivesbgIsAvailableInNS", false),
    ObjectivesProfileSwitchUsed("ObjectivesProfileSwitchUsed", false),
    ObjectivesDisconnectUsed("ObjectivesDisconnectUsed", false),
    ObjectivesReconnectUsed("ObjectivesReconnectUsed", false),
    ObjectivesTempTargetUsed("ObjectivesTempTargetUsed", false),
    AutosensUsedOnMainPhone("used_autosens_on_main_phone", false),
}