package app.aaps.pump.omnipod.common.ui.wizard.activation.fragment.info

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.di.OmnipodPluginQualifier
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.info.AttachPodViewModel
import app.aaps.pump.omnipod.common.ui.wizard.common.fragment.InfoFragmentBase
import javax.inject.Inject

class AttachPodFragment : InfoFragmentBase() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: AttachPodViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_attachPodFragment_to_insertCannulaFragment

    override fun getIndex(): Int = 3

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            context?.let {
                AlertDialog.Builder(it, app.aaps.core.ui.R.style.DialogTheme)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(getString(getTitleId()))
                    .setMessage(getString(R.string.omnipod_common_pod_activation_wizard_attach_pod_confirm_insert_cannula_text))
                    .setPositiveButton(getString(R.string.omnipod_common_ok)) { _, _ ->
                        findNavController().navigate(
                            getNextPageActionId()
                        )
                    }
                    .setNegativeButton(getString(R.string.omnipod_common_cancel), null)
                    .show()
            }
        }
    }
}