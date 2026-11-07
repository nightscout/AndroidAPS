package app.aaps.ui.compose.wizardDialog

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.ui.CoreUiStrings
import kotlin.time.TimeMark

/**
 * The single wizard-bolus confirmation renderer, shared by the QuickWizard button (`MainViewModel`) and the manual
 * Bolus Wizard dialog (`WizardDialogViewModel`) for BOTH roles (D2 of `_docs/GENERAL_EXECUTION_PATH_PLAN.md`). The
 * master already authored [normalLines]/[advisorLines] (`WizardExecutor.prepare`); this only renders them on the one
 * app-level dialog and routes the user's choice. When [advisorApplies], the high-BG "correct now, eat later?" Yes/No
 * precedes the OkCancel; [onCommit] receives the chosen asAdvisor flag and should issue `WizardExecutor.commit`.
 *
 * The confirmation closes by itself at [closeAt], when the master's SMB pause for the dose has run out. That and
 * every Cancel call [onCancel], which should issue `WizardExecutor.discard`. When the user answers the advisor
 * question only after [closeAt], the dose is too old to show: the user is told to tap again.
 */
fun showWizardBolusConfirmation(
    rxBus: RxBus,
    rh: TextResolver,
    title: String,
    icon: ImageVector,
    advisorApplies: Boolean,
    normalLines: List<ConfirmationLine>,
    advisorLines: List<ConfirmationLine>,
    closeAt: TimeMark? = null,
    onCancel: () -> Unit = {},
    onCommit: (asAdvisor: Boolean) -> Unit
) {
    fun showConfirm(lines: List<ConfirmationLine>, asAdvisor: Boolean) {
        if (closeAt?.hasPassedNow() == true) {
            onCancel()
            rxBus.send(EventShowDialog.Ok(title = title, message = rh.gs(CoreUiStrings.clientcontrol_fail_no_pending_bolus)))
            return
        }
        rxBus.send(
            EventShowDialog.OkCancel(
                title = title, message = "", confirmationLines = lines, icon = icon,
                onOk = { onCommit(asAdvisor) },
                onCancel = { onCancel() },
                closeAt = closeAt
            )
        )
    }
    if (advisorApplies)
        rxBus.send(
            EventShowDialog.YesNoCancel(
                title = rh.gs(CoreUiStrings.bolus_advisor),
                message = rh.gs(CoreUiStrings.bolus_advisor_message),
                onYes = { showConfirm(advisorLines, true) },
                onNo = { showConfirm(normalLines, false) },
                onCancel = onCancel
            )
        )
    else showConfirm(normalLines, false)
}
