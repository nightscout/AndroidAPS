package app.aaps.pump.equil.compose

import app.aaps.core.data.model.TE
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The reservoir change flow depends on whether a new infusion set (base plate and cannula) was put
 * on (#5199). The Equil allows changing only the reservoir or battery; then the cannula is already
 * full and in the body.
 */
internal class EquilWorkflowTest {

    @Test
    fun `reservoir change with a new infusion set records the site and primes the cannula`() {
        val steps = EquilWorkflow.CHANGE_INSULIN.steps(siteRotationEnabled = true, insulinSelectionEnabled = false, newInfusionSet = true)

        assertThat(steps).containsExactly(
            EquilWizardStep.CHANGE_INSULIN, EquilWizardStep.ASSEMBLE, EquilWizardStep.FILL,
            EquilWizardStep.SITE_LOCATION, EquilWizardStep.ATTACH, EquilWizardStep.AIR, EquilWizardStep.CONFIRM
        ).inOrder()
    }

    @Test
    fun `reservoir change without a new infusion set skips the site and the cannula prime`() {
        // Priming a cannula that is already in the body would push insulin into the body.
        val steps = EquilWorkflow.CHANGE_INSULIN.steps(siteRotationEnabled = true, insulinSelectionEnabled = false, newInfusionSet = false)

        assertThat(steps).containsExactly(
            EquilWizardStep.CHANGE_INSULIN, EquilWizardStep.ASSEMBLE, EquilWizardStep.FILL,
            EquilWizardStep.ATTACH, EquilWizardStep.CONFIRM
        ).inOrder()
    }

    @Test
    fun `pairing always records the site and primes the cannula`() {
        val steps = EquilWorkflow.PAIR.steps(siteRotationEnabled = true, insulinSelectionEnabled = false, newInfusionSet = false)

        assertThat(steps).contains(EquilWizardStep.SITE_LOCATION)
        assertThat(steps).contains(EquilWizardStep.AIR)
    }

    @Test
    fun `reservoir change without a new infusion set logs only an insulin change`() {
        assertThat(EquilWorkflow.CHANGE_INSULIN.therapyEvents(newInfusionSet = false)).containsExactly(TE.Type.INSULIN_CHANGE)
    }

    @Test
    fun `reservoir change with a new infusion set logs cannula and insulin change`() {
        assertThat(EquilWorkflow.CHANGE_INSULIN.therapyEvents(newInfusionSet = true))
            .containsExactly(TE.Type.CANNULA_CHANGE, TE.Type.INSULIN_CHANGE)
    }

    @Test
    fun `pairing logs cannula and insulin change, unpairing nothing`() {
        assertThat(EquilWorkflow.PAIR.therapyEvents(newInfusionSet = false)).containsExactly(TE.Type.CANNULA_CHANGE, TE.Type.INSULIN_CHANGE)
        assertThat(EquilWorkflow.UNPAIR.therapyEvents(newInfusionSet = true)).isEmpty()
    }
}
