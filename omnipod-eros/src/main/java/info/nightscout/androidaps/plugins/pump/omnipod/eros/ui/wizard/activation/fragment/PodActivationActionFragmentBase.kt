package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.common.fragment.ActionFragmentBase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.PodDeactivationWizardActivity
import javax.inject.Inject

abstract class PodActivationActionFragmentBase : ActionFragmentBase() {

    @Inject protected lateinit var podStateManager: PodStateManager

    private lateinit var buttonDeactivatePod: Button
    private lateinit var buttonRetry: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonDeactivatePod = view.findViewById(R.id.omnipod_wizard_button_deactivate_pod)
        buttonRetry = view.findViewById(R.id.omnipod_wizard_button_retry)

        buttonDeactivatePod.setOnClickListener {
            activity?.let {
                startActivity(Intent(it, PodDeactivationWizardActivity::class.java))
                it.finish()
            }
        }
    }

    override fun onActionFailure() {
        if ((podStateManager.isPodActivationTimeExceeded && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)) || podStateManager.isPodFaulted) {
            buttonRetry.visibility = View.GONE
            buttonDeactivatePod.visibility = View.VISIBLE
        }
    }
}