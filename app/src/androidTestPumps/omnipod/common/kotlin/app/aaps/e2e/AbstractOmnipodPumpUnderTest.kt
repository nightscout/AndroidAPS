package app.aaps.e2e

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel

/**
 * What the Omnipod Dash and the Omnipod 5 share for [PumpScenario]: a pod is activated and removed as the wizard does
 * it, with the wizard's own ViewModel from the app's factory and no screen. The two drivers have the same wizard steps.
 */
abstract class AbstractOmnipodPumpUnderTest<W : OmnipodWizardViewModel>(private val wizardClass: Class<W>) : PumpUnderTest {

    /** A status read is one command. A first guess with room, to be set from what a run shows. */
    override val maxCommandsPerStatusRead = 4

    /**
     * How many times the first activation step may fail and be run again. 0 unless a test expects that step to fail,
     * as after a pairing whose key gives no session.
     */
    var initializePodRetries = 0

    /** How many times the first activation step failed and was run again. */
    var initializePodFailures = 0
        private set

    /**
     * Runs once between the two activation steps: the first one ended well and the second has not started. null unless
     * a test wants to do something to a pod that is paired and primed, but not active yet.
     */
    var betweenActivationSteps: ((PumpScenario) -> Unit)? = null

    /** How far the driver's pod state says the activation is. NOT_STARTED when no pod is paired. */
    protected abstract fun activationProgress(): ActivationProgress

    /** The driver reads its pod state when it is first used, so there is nothing to wait for. */
    override fun awaitStarted(scenario: PumpScenario) = Unit

    /**
     * The activation wizard, step by step. The two action steps talk to the pod: the first pairs it and primes it,
     * which takes the pod about a minute, the second sets the basal and inserts the cannula.
     */
    override fun pair(scenario: PumpScenario) {
        withWizard(scenario) { wizard ->
            scenario.awaitTrue("the wizard is ready") { wizard.ready.value }
            scenario.onMain { wizard.initializeActivation(ActivationType.LONG) }
            while (wizard.currentStep.value != OmnipodWizardStep.POD_ACTIVATED) {
                val step = wizard.currentStep.value
                if (step == OmnipodWizardStep.INITIALIZE_POD || step == OmnipodWizardStep.INSERT_CANNULA) runAction(scenario, wizard)
                if (step == OmnipodWizardStep.INITIALIZE_POD) betweenActivationSteps?.invoke(scenario)
                scenario.onMain { wizard.moveToNext() }
                scenario.awaitTrue("the wizard leaves the step $step") { wizard.currentStep.value != step }
            }
        }
        scenario.awaitQueueIdle()
        scenario.awaitTrue("the pod is active") { testGraphs.activePlugin.activePump.isInitialized() }
    }

    /**
     * As the user removes a pod: the deactivation wizard sends the deactivate command through the queue. When the pod
     * does not answer, the wizard offers "Discard Pod", and the user takes it.
     */
    override fun unpair(scenario: PumpScenario) {
        withWizard(scenario) { wizard ->
            scenario.onMain {
                wizard.initializeDeactivation()
                wizard.moveToNext()
            }
            scenario.awaitTrue("the wizard is at the deactivate step") { wizard.currentStep.value == OmnipodWizardStep.DEACTIVATE_POD }
            scenario.onMain { wizard.executeAction() }
            scenario.awaitTrue("the deactivation ended", DEACTIVATION_TIMEOUT) { wizard.actionState.value.let { it is ActionState.Success || it is ActionState.Error } }
            if (wizard.actionState.value is ActionState.Error) {
                scenario.onMain {
                    wizard.discardPod()
                    wizard.moveToStep(OmnipodWizardStep.POD_DISCARDED)
                }
            }
            scenario.awaitTrue("no pod is paired") { activationProgress() == ActivationProgress.NOT_STARTED }
        }
        scenario.awaitQueueIdle()
    }

    /**
     * Runs the action of the wizard's step, as its screen does when it opens, and fails on the error it would show.
     * While [initializePodRetries] allows it, a failed first step is run again, as the user does with "Retry".
     */
    private fun runAction(scenario: PumpScenario, wizard: W) {
        val step = wizard.currentStep.value
        while (true) {
            scenario.onMain { wizard.executeAction() }
            scenario.awaitTrue("the step $step ended", ACTIVATION_STEP_TIMEOUT) { wizard.actionState.value.let { it is ActionState.Success || it is ActionState.Error } }
            val error = wizard.actionState.value as? ActionState.Error ?: return
            if (step != OmnipodWizardStep.INITIALIZE_POD || initializePodFailures >= initializePodRetries)
                throw AssertionError("The wizard step $step failed: ${error.message}")
            initializePodFailures++
        }
    }

    /** The wizard's ViewModel from the app's factory, cleared after [block]. */
    private fun withWizard(scenario: PumpScenario, block: (W) -> Unit) {
        val viewModels = ViewModelStore()
        try {
            lateinit var wizard: W
            scenario.onMain { wizard = ViewModelProvider(viewModels, testGraphs.viewModelFactory).get(wizardClass) }
            block(wizard)
        } finally {
            scenario.onMain { viewModels.clear() }
        }
    }

    private companion object {

        /** The driver waits one second for each of the 52 priming pulses, after the scan, the pairing and four commands. */
        const val ACTIVATION_STEP_TIMEOUT = 150_000L

        /** A pod that cannot be reached: the queue gives up on the link first, then the wizard offers the discard. */
        const val DEACTIVATION_TIMEOUT = 180_000L
    }
}
