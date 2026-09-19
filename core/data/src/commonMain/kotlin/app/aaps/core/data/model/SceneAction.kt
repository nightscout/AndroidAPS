package app.aaps.core.data.model

/**
 * Sealed class representing individual actions that can be part of a Scene.
 * Each action maps to a specific system operation (TT, profile switch, etc.).
 */
sealed class SceneAction {

    /**
     * Set a temporary target.
     * @param reason TT reason category
     * @param targetMgdl Target value in mg/dL (converted at display time)
     */
    data class TempTarget(
        val reason: TT.Reason,
        val targetMgdl: Double
    ) : SceneAction()

    /**
     * Switch to a named profile with optional percentage and time shift.
     * @param profileName Name of the profile to switch to
     * @param percentage Basal percentage (100 = no change)
     * @param timeShiftHours Time shift in hours (0 = no shift)
     */
    data class ProfileSwitch(
        val profileName: String,
        val percentage: Int = 100,
        val timeShiftHours: Int = 0
    ) : SceneAction()

    /**
     * Toggle SMB on or off.
     * @param enabled Whether SMB should be enabled
     */
    data class SmbToggle(
        val enabled: Boolean
    ) : SceneAction()

    /**
     * Change the loop running mode.
     * @param mode Target running mode
     */
    data class LoopModeChange(
        val mode: RM.Mode
    ) : SceneAction()

    /**
     * Create a CarePortal therapy event entry.
     * @param type Therapy event type (Exercise, Sickness, etc.)
     * @param note Optional note text
     */
    data class CarePortalEvent(
        val type: TE.Type,
        val note: String = ""
    ) : SceneAction()
}
