package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.common.fragment.ActionFragmentBase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.DeactivatePodActionViewModel
import info.nightscout.androidaps.utils.extensions.toVisibility
import javax.inject.Inject

class DeactivatePodActionFragment : ActionFragmentBase() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var aapsOmnipodManager: AapsOmnipodManager

    private lateinit var buttonDiscardPod: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: DeactivatePodActionViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonDiscardPod = view.findViewById(R.id.button_discard_pod)
        buttonDiscardPod.setOnClickListener {
            context?.let {
                AlertDialog.Builder(it)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(getString(R.string.omnipod_pod_deactivation_wizard_discard_pod))
                    .setMessage(getString(R.string.omnipod_pod_deactivation_wizard_discard_pod_confirmation))
                    .setPositiveButton(getString(R.string.omnipod_yes)) { _, _ ->
                        aapsOmnipodManager.discardPodState()
                        findNavController().navigate(R.id.action_deactivatePodActionFragment_to_podDiscardedInfoFragment)
                    }
                    .setNegativeButton(getString(R.string.omnipod_no), null)
                    .show()
            }
        }
    }

    override fun onActionFailure() {
        buttonDiscardPod.visibility = (!isActionExecuting()).toVisibility()
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_pod_deactivation_wizard_deactivating_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_pod_deactivation_wizard_deactivating_pod_text

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_deactivatePodActionFragment_to_podDeactivatedInfoFragment

    override fun getIndex(): Int = 2
}