package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.fragment.ActionFragmentBase
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.PodDeactivationWizardActivity
import kotlinx.android.synthetic.main.omnipod_wizard_action_page_fragment.*
import javax.inject.Inject

abstract class PodActivationActionFragmentBase : ActionFragmentBase() {

    @Inject
    protected lateinit var podStateManager: PodStateManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_wizard_button_deactivate_pod.setOnClickListener {
            activity?.let {
                startActivity(Intent(it, PodDeactivationWizardActivity::class.java))
                it.finish()
            }
        }
    }

    override fun onActionFailure() {
        if ((podStateManager.isPodActivationTimeExceeded && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)) || podStateManager.isPodFaulted) {
            omnipod_wizard_button_retry.visibility = View.GONE
            omnipod_wizard_button_deactivate_pod.visibility = View.VISIBLE
        }
    }
}