package app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.action

import android.view.View
import android.widget.Button
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action.PodActivationActionViewModelBase
import app.aaps.pump.omnipod.common.ui.wizard.common.fragment.ActionFragmentBase

abstract class PodActivationActionFragmentBase : ActionFragmentBase() {

    /*
     * Removed by Milos. It's causing
     * android.content.ActivityNotFoundException: Unable to find explicit activity class {info.nightscout.androidaps/app.aaps.pump.omnipod.common.ui.wizard.deactivation.PodDeactivationWizardActivity}; have you declared this activity in your AndroidManifest.xml?
     *
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<Button>(R.id.omnipod_wizard_button_deactivate_pod).setOnClickListener {
                activity?.let {
                    startActivity(Intent(it, PodDeactivationWizardActivity::class.java))
                    it.finish()
                }
            }
        }
    */
    override fun onFailure() {
        (viewModel as? PodActivationActionViewModelBase)?.let { viewModel ->
            if (viewModel.isPodDeactivatable() and (viewModel.isPodInAlarm() or viewModel.isPodActivationTimeExceeded())) {
                view?.let {
                    it.findViewById<Button>(R.id.omnipod_wizard_button_retry)?.visibility = View.GONE
                    it.findViewById<Button>(R.id.omnipod_wizard_button_deactivate_pod)?.visibility = View.VISIBLE
                }
            }
        }
    }
}