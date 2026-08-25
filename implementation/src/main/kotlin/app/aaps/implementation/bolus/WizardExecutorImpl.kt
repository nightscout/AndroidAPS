package app.aaps.implementation.bolus

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WizardExecutor] implementation — the RECOMPUTE bolus path (QuickWizard WIZARD-mode + manual Bolus Wizard). It
 * only supplies the client command to dispatch (`BolusPrepare`/`WizardPrepare` + `BolusCommit`, the latter carrying
 * the high-BG `asAdvisor` fork) and the master executor call (`prepareQuickWizard`/`prepareWizard` + `confirm`);
 * the role branch (client round-trip vs master-local + the result mapping) lives once in [RoleBranch], shared with
 * [BatchExecutorImpl]. The compute/deliver logic lives once in the executor, so a client's request lands in that
 * same executor on the master and both roles render the master's identical lines.
 */
@Singleton
class WizardExecutorImpl @Inject constructor(
    private val roleBranch: RoleBranch,
    private val wizardBolusExecutor: WizardBolusExecutor
) : WizardExecutor {

    override suspend fun prepare(source: WizardExecutor.WizardSource, label: String): ActionProgress {
        val command = when (source) {
            is WizardExecutor.WizardSource.QuickWizard -> ClientControlActionDispatcher.Command.BolusPrepare(source.guid)
            is WizardExecutor.WizardSource.Manual      -> ClientControlActionDispatcher.Command.WizardPrepare(source.inputs)
        }
        return roleBranch.prepare(label, command) {
            when (source) {
                is WizardExecutor.WizardSource.QuickWizard -> wizardBolusExecutor.prepareQuickWizard(source.guid)
                is WizardExecutor.WizardSource.Manual      -> wizardBolusExecutor.prepareWizard(source.inputs)
            }
        }
    }

    override suspend fun commit(bolusId: Long, asAdvisor: Boolean, source: Sources, label: String, correctionU: Double): ActionProgress =
        roleBranch.commit(label, ClientControlActionDispatcher.Command.BolusCommit(bolusId, asAdvisor, correctionU = correctionU)) { onError ->
            wizardBolusExecutor.confirm(bolusId, source, onError, asAdvisor, correctionU)
        }
}
