package app.aaps.plugins.automationstate.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.automationstate.R
import app.aaps.plugins.automationstate.databinding.AutomationAddStateDialogBinding
import app.aaps.plugins.automationstate.services.AutomationStateService
import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject

class AutomationAddStateDialog : DaggerDialogFragment() {

    @Inject lateinit var automationStateService: AutomationStateService
    @Inject lateinit var rh: ResourceHelper

    private var _binding: AutomationAddStateDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutomationAddStateDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.okButton.setOnClickListener {
            val stateName = binding.stateNameInput.text.toString().trim()
            if (stateName.isEmpty()) {
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.automation_state_missing_name_value))
                return@setOnClickListener
            }

            // First dismiss this dialog
            dismiss()
            
            // Then open the state values dialog for the new state
            val stateValuesDialog = AutomationStateValuesDialog.newInstance(stateName)
            stateValuesDialog.show(parentFragmentManager, "AutomationStateValuesDialog")
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): AutomationAddStateDialog {
            return AutomationAddStateDialog()
        }
    }
} 