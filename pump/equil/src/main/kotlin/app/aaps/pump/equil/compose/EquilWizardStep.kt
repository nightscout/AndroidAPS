package app.aaps.pump.equil.compose

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

    // CHANGE_INSULIN flow: ChangeInsulin → Assemble → [SelectInsulin] → Fill → [SiteLocation] → Attach → Air → Confirm
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

    fun steps(siteRotationEnabled: Boolean, insulinSelectionEnabled: Boolean, needsProfileGate: Boolean = false): List<EquilWizardStep> = when (this) {
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
            if (siteRotationEnabled) add(EquilWizardStep.SITE_LOCATION)
            add(EquilWizardStep.ATTACH)
            add(EquilWizardStep.AIR)
            add(EquilWizardStep.CONFIRM)
        }

        UNPAIR         -> listOf(
            EquilWizardStep.UNPAIR_DETACH,
            EquilWizardStep.UNPAIR_CONFIRM
        )
    }
}
