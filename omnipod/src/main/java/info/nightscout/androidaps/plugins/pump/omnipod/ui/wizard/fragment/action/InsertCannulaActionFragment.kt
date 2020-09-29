package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.action

import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.ChangePodWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.viewmodel.InsertCannulaActionViewModel
import kotlinx.android.synthetic.main.omnipod_change_pod_wizard_action_page_fragment.*
import javax.inject.Inject

class InsertCannulaActionFragment : ActionFragmentBase() {
    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var podStateManager: PodStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: InsertCannulaActionViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_change_pod_wizard_button_deactivate_pod.setOnClickListener {
            (activity as? ChangePodWizardActivity)?.setStartDestination(R.id.deactivatePodInfoFragment)
            findNavController().navigate(R.id.deactivatePodInfoFragment)
        }
    }

    override fun onActionFailure() {
        if (podStateManager.isPodInitialized && podStateManager.podProgressStatus == PodProgressStatus.ACTIVATION_TIME_EXCEEDED) {
            omnipod_change_pod_wizard_action_error.setText(R.string.omnipod_error_pod_fault_activation_time_exceeded)
            omnipod_change_pod_wizard_button_retry.visibility = View.GONE
            omnipod_change_pod_wizard_button_deactivate_pod.visibility = View.VISIBLE
        }
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_change_pod_wizard_insert_cannula_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_change_pod_wizard_insert_cannula_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_insertCannulaActionFragment_to_PodChangedInfoFragment

    override fun getIndex(): Int = 7
}