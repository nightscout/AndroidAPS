package app.aaps.pump.equil.compose

import app.aaps.core.data.model.TE
import app.aaps.pump.equil.R
import app.aaps.core.ui.R as CoreUiR

/**
 * Steps for Equil pump activation/deactivation/change-insulin wizards.
 */
enum class EquilWizardStep(val titleResId: Int) {

    // Pre-activation gate (shown only when no profile switch exists yet)
    PROFILE_GATE(CoreUiR.string.pump_wizard_profile_gate_title),

    // PAIR flow: Assemble → BleScan → Password → [SelectInsulin] → Fill → [SiteLocation] → Attach → Air → Confirm
    ASSEMBLE(R.string.equil_title_assemble),
    BLE_SCAN(CoreUiR.string.step_ble_scan),
    PASSWORD(R.string.equil_title_serial),
    FILL(R.string.equil_title_fill),
    ATTACH(R.string.equil_title_attach),
    AIR(R.string.equil_title_air),
    SELECT_INSULIN(CoreUiR.string.select_insulin),
    SITE_LOCATION(CoreUiR.string.site_rotation),
    CONFIRM(CoreUiR.string.confirm),

    // CHANGE_INSULIN flow: ChangeInsulin → Assemble → [SelectInsulin] → Fill → [SiteLocation] → Attach → [Air] → Confirm
    // SiteLocation and Air (prime cannula) only when a new infusion set was put on, see EquilWorkflow.steps.
    CHANGE_INSULIN(R.string.equil_change),

    // UNPAIR flow: UnpairDetach → UnpairConfirm
    UNPAIR_DETACH(R.string.equil_title_unpair_detach),
    UNPAIR_CONFIRM(CoreUiR.string.pump_unpair_confirm_title),

    // Terminal
    CANCEL(0)
}

enum class EquilWorkflow {

    PAIR,
    CHANGE_INSULIN,
    UNPAIR;

    /**
     * @param newInfusionSet CHANGE_INSULIN only: whether a new infusion set (base and cannula) was put on.
     * The Equil allows changing only the reservoir or battery and keeping the base. Then the cannula
     * is already full and in the body, so it must not be primed (that would push insulin into the
     * body), and there is no new site to record. PAIR always means a new infusion set.
     */
    fun steps(
        siteRotationEnabled: Boolean,
        insulinSelectionEnabled: Boolean,
        needsProfileGate: Boolean = false,
        newInfusionSet: Boolean = true
    ): List<EquilWizardStep> = when (this) {
        PAIR           -> buildList {
            if (needsProfileGate) add(EquilWizardStep.PROFILE_GATE)
            add(EquilWizardStep.ASSEMBLE)
            add(EquilWizardStep.BLE_SCAN)
            add(EquilWizardStep.PASSWORD)
            if (insulinSelectionEnabled) add(EquilWizardStep.SELECT_INSULIN)
            add(EquilWizardStep.FILL)
            if (siteRotationEnabled) add(EquilWizardStep.SITE_LOCATION)
            add(EquilWizardStep.ATTACH)
            add(EquilWizardStep.AIR)
            add(EquilWizardStep.CONFIRM)
        }

        CHANGE_INSULIN -> buildList {
            add(EquilWizardStep.CHANGE_INSULIN)
            add(EquilWizardStep.ASSEMBLE)
            if (insulinSelectionEnabled) add(EquilWizardStep.SELECT_INSULIN)
            add(EquilWizardStep.FILL)
            if (siteRotationEnabled && newInfusionSet) add(EquilWizardStep.SITE_LOCATION)
            add(EquilWizardStep.ATTACH)
            if (newInfusionSet) add(EquilWizardStep.AIR)
            add(EquilWizardStep.CONFIRM)
        }

        UNPAIR         -> listOf(
            EquilWizardStep.UNPAIR_DETACH,
            EquilWizardStep.UNPAIR_CONFIRM
        )
    }

    /**
     * Therapy events to log when the workflow completes. A cannula change only when a new infusion
     * set was put on (#5199): a reservoir or battery change alone must not reset the cannula age.
     */
    fun therapyEvents(newInfusionSet: Boolean): List<TE.Type> = when (this) {
        PAIR           -> listOf(TE.Type.CANNULA_CHANGE, TE.Type.INSULIN_CHANGE)
        CHANGE_INSULIN -> if (newInfusionSet) listOf(TE.Type.CANNULA_CHANGE, TE.Type.INSULIN_CHANGE) else listOf(TE.Type.INSULIN_CHANGE)
        UNPAIR         -> emptyList()
    }
}
