package app.aaps.pump.omnipod.common.ui.wizard.common.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase

abstract class ActionFragmentBase : WizardFragmentBase() {

    private val actionViewModel: ActionViewModelBase
        get() = viewModel as ActionViewModelBase

    @SuppressLint("CutPasteId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.navButtonsLayout.buttonNext.isEnabled = false
        view.findViewById<TextView>(R.id.omnipod_wizard_action_page_text).setText(getTextId())

        view.findViewById<Button>(R.id.omnipod_wizard_button_retry)
            .setOnClickListener { actionViewModel.executeAction() }

        actionViewModel.isActionExecutingLiveData.observe(viewLifecycleOwner) { isExecuting ->
            if (isExecuting) {
                view.findViewById<TextView>(R.id.omnipod_wizard_action_error).visibility = View.GONE
                view.findViewById<Button>(R.id.omnipod_wizard_button_deactivate_pod).visibility = View.GONE
                view.findViewById<Button>(R.id.omnipod_wizard_button_discard_pod).visibility = View.GONE
                view.findViewById<Button>(R.id.omnipod_wizard_button_retry).visibility = View.GONE
            }
            view.findViewById<ProgressBar>(R.id.omnipod_wizard_action_progress_indication).visibility =
                isExecuting.toVisibility()
            view.findViewById<Button>(R.id.button_cancel).isEnabled = !isExecuting
        }

        actionViewModel.actionResultLiveData.observe(viewLifecycleOwner) { result ->
            result?.let {
                val isExecuting = isActionExecuting()

                view.findViewById<Button>(R.id.button_next).isEnabled = result.success
                view.findViewById<ImageView>(R.id.omnipod_wizard_action_success).visibility =
                    result.success.toVisibility()
                view.findViewById<TextView>(R.id.omnipod_wizard_action_error).visibility =
                    (!isExecuting && !result.success).toVisibility()
                view.findViewById<Button>(R.id.omnipod_wizard_button_retry).visibility =
                    (!isExecuting && !result.success).toVisibility()

                if (!result.success) {
                    view.findViewById<TextView>(R.id.omnipod_wizard_action_error).text = result.comment
                    onFailure()
                }
            }
        }

        if (savedInstanceState == null && !isActionExecuting()) {
            actionViewModel.executeAction()
        }

    }

    private fun isActionExecuting() = actionViewModel.isActionExecutingLiveData.value!!

    override fun onDestroyView() {
        super.onDestroyView()
        actionViewModel.isActionExecutingLiveData.removeObservers(viewLifecycleOwner)
        actionViewModel.actionResultLiveData.removeObservers(viewLifecycleOwner)
    }

    abstract fun onFailure()

    @LayoutRes
    override fun getLayoutId(): Int = R.layout.omnipod_common_wizard_action_page_fragment
}