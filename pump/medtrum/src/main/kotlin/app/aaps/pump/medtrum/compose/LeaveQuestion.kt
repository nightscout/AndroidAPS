package app.aaps.pump.medtrum.compose

import app.aaps.pump.medtrum.code.PatchStep

/** What the patch wizard asks before it is left. */
internal enum class LeaveQuestion {

    /** Leave at once: nothing is lost. */
    NONE,

    /** "Are you sure that you want to cancel the activation?" */
    ACTIVATION,

    /** "Are you sure that you want to cancel the deactivation?" */
    DEACTIVATION
}

/**
 * The question asked when the wizard is left on [step], by Cancel, the back arrow or system back.
 *
 * The wizard asked about cancelling the activation on every step, also in a deactivation: "Change Patch"
 * with a running patch, then Cancel on "Deactivate Patch", asked whether to cancel the activation. As in
 * 3.4, the first deactivation screen, which says that Cancel returns to the main screen, and the screen
 * after the deactivation leave at once. Every step is named here, so a new one cannot fall back to the
 * wrong question unseen.
 */
internal fun leaveQuestionFor(step: PatchStep): LeaveQuestion = when (step) {
    PatchStep.START_DEACTIVATION,
    PatchStep.DEACTIVATION_COMPLETE    -> LeaveQuestion.NONE

    PatchStep.DEACTIVATE,
    PatchStep.FORCE_DEACTIVATION       -> LeaveQuestion.DEACTIVATION

    PatchStep.BLE_SCAN,
    PatchStep.PROFILE_GATE,
    PatchStep.PREPARE_PATCH,
    PatchStep.PREPARE_PATCH_CONNECT,
    PatchStep.SELECT_INSULIN,
    PatchStep.PRIME,
    PatchStep.PRIMING,
    PatchStep.PRIME_COMPLETE,
    PatchStep.ATTACH_PATCH,
    PatchStep.ACTIVATE,
    PatchStep.ACTIVATE_COMPLETE,
    PatchStep.SITE_LOCATION,
    PatchStep.COMPLETE,
    PatchStep.CANCEL,
    PatchStep.RETRY_ACTIVATION,
    PatchStep.RETRY_ACTIVATION_CONNECT -> LeaveQuestion.ACTIVATION
}
