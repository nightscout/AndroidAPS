package app.aaps.pump.equil.compose

import app.aaps.pump.equil.R

/**
 * Steps for Equil pump activation/deactivation/change-insulin wizards.
 */
enum class EquilWizardStep(val titleResId: Int) {

    // PAIR flow: Assemble → BleScan → Password → [SelectInsulin] → Fill → Attach → Air → [SiteLocation] → Confirm
    ASSEMBLE(R.string.equil_title_assemble),
    BLE_SCAN(R.string.equil_title_serial),
    PASSWORD(R.string.equil_title_serial),
    FILL(R.string.equil_title_fill),
    ATTACH(R.string.equil_title_attach),
    AIR(R.string.equil_title_air),
    SELECT_INSULIN(app.aaps.core.ui.R.string.select_insulin),
    SITE_LOCATION(app.aaps.core.ui.R.string.site_rotation),
    CONFIRM(app.aaps.core.ui.R.string.confirm),

    // CHANGE_INSULIN flow: ChangeInsulin → Assemble → [SelectInsulin] → Fill → Attach → Air → [SiteLocation] → Confirm
    CHANGE_INSULIN(R.string.equil_change),

    // UNPAIR flow: UnpairDetach → UnpairConfirm
    UNPAIR_DETACH(R.string.equil_title_unpair_detach),
    UNPAIR_CONFIRM(R.string.equil_title_unpair_confirm),

    // Terminal
    CANCEL(0)
}

enum class EquilWorkflow {

    PAIR,
    CHANGE_INSULIN,
    UNPAIR;

    fun steps(siteRotationEnabled: Boolean, insulinSelectionEnabled: Boolean): List<EquilWizardStep> = when (this) {
        PAIR           -> buildList {
            add(EquilWizardStep.ASSEMBLE)
            add(EquilWizardStep.BLE_SCAN)
            add(EquilWizardStep.PASSWORD)
            if (insulinSelectionEnabled) add(EquilWizardStep.SELECT_INSULIN)
            add(EquilWizardStep.FILL)
            add(EquilWizardStep.ATTACH)
            add(EquilWizardStep.AIR)
            if (siteRotationEnabled) add(EquilWizardStep.SITE_LOCATION)
            add(EquilWizardStep.CONFIRM)
        }

        CHANGE_INSULIN -> buildList {
            add(EquilWizardStep.CHANGE_INSULIN)
            add(EquilWizardStep.ASSEMBLE)
            if (insulinSelectionEnabled) add(EquilWizardStep.SELECT_INSULIN)
            add(EquilWizardStep.FILL)
            add(EquilWizardStep.ATTACH)
            add(EquilWizardStep.AIR)
            if (siteRotationEnabled) add(EquilWizardStep.SITE_LOCATION)
            add(EquilWizardStep.CONFIRM)
        }

        UNPAIR         -> listOf(
            EquilWizardStep.UNPAIR_DETACH,
            EquilWizardStep.UNPAIR_CONFIRM
        )
    }
}
