package app.aaps.plugins.sync.nfcCommands

import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State that outlives a single [app.aaps.plugins.sync.nfcCommands.actions.NfcAction] but belongs to no
 * single one of them.
 *
 * Both members used to sit on `NfcCommandsPlugin`, which meant every action had to be handed the whole
 * plugin to reach them. They are here so an action can ask for this and nothing else.
 */
@Singleton
class NfcRuntimeState @Inject constructor() {

    /**
     * When the last bolus was delivered through NFC, used for the remote bolus cooldown that
     * `Constants.remoteBolusMinDistance` defines. Shared because the wizard and the plain bolus must
     * respect one another's deliveries.
     */
    var lastRemoteBolusTime: Long = 0

    /**
     * The dose the bolus wizard previewed, parked between the confirmation dialog and execution so
     * that execution commits the exact dose that was shown, by id, rather than recalculating it.
     *
     * Keyed by the action's parameters, because one tag may carry more than one wizard command.
     */
    private val wizardPreviews = mutableMapOf<String, WizardBolusExecutor.PrepareResult.Preview>()

    fun setWizardPreview(key: String, preview: WizardBolusExecutor.PrepareResult.Preview) {
        wizardPreviews[key] = preview
    }

    fun getWizardPreview(key: String): WizardBolusExecutor.PrepareResult.Preview? = wizardPreviews[key]

    /** Called before a chain starts and after it finishes, so nothing leaks between scans. */
    fun clearWizardPreviews() {
        wizardPreviews.clear()
    }
}
