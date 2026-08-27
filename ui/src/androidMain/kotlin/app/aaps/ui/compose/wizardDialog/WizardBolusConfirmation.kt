package app.aaps.ui.compose.wizardDialog

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.ui.R

/**
 * The single wizard-bolus confirmation renderer, shared by the QuickWizard button (`MainViewModel`) and the manual
 * Bolus Wizard dialog (`WizardDialogViewModel`) for BOTH roles (D2 of `_docs/GENERAL_EXECUTION_PATH_PLAN.md`). The
 * master already authored [normalLines]/[advisorLines] (`WizardExecutor.prepare`); this only renders them on the one
 * app-level dialog and routes the user's choice. When [advisorApplies], the high-BG "correct now, eat later?" Yes/No
 * precedes the OkCancel; [onCommit] receives the chosen asAdvisor flag and should issue `WizardExecutor.commit`.
 */
fun showWizardBolusConfirmation(
    rxBus: RxBus,
    rh: ResourceHelper,
    title: String,
    icon: ImageVector,
    advisorApplies: Boolean,
    normalLines: List<ConfirmationLine>,
    advisorLines: List<ConfirmationLine>,
    onCommit: (asAdvisor: Boolean) -> Unit
) {
    fun showConfirm(lines: List<ConfirmationLine>, asAdvisor: Boolean) =
        rxBus.send(EventShowDialog.OkCancel(title = title, message = "", confirmationLines = lines, icon = icon, onOk = { onCommit(asAdvisor) }))
    if (advisorApplies)
        rxBus.send(
            EventShowDialog.YesNoCancel(
                title = rh.gs(R.string.bolus_advisor),
                message = rh.gs(R.string.bolus_advisor_message),
                onYes = { showConfirm(advisorLines, true) },
                onNo = { showConfirm(normalLines, false) }
            )
        )
    else showConfirm(normalLines, false)
}
