package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.common.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.common.viewmodel.ActionViewModelBase
import info.nightscout.androidaps.utils.extensions.toVisibility

abstract class ActionFragmentBase : WizardFragmentBase() {

    protected lateinit var viewModel: ActionViewModelBase

    @SuppressLint("CutPasteId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.navButtonsLayout.buttonNext.isEnabled = false
        view.findViewById<Button>(R.id.omnipod_wizard_action_page_text).setText(getTextId())

        view.findViewById<Button>(R.id.omnipod_wizard_button_retry).setOnClickListener { viewModel.executeAction() }

        viewModel.isActionExecutingLiveData.observe(viewLifecycleOwner, { isExecuting ->
            if (isExecuting) {
                view.findViewById<Button>(R.id.omnipod_wizard_action_error).visibility = View.GONE
                view.findViewById<Button>(R.id.omnipod_wizard_button_deactivate_pod).visibility = View.GONE
                view.findViewById<Button>(R.id.omnipod_wizard_button_discard_pod).visibility = View.GONE
                view.findViewById<Button>(R.id.omnipod_wizard_button_retry).visibility = View.GONE
            }
            view.findViewById<Button>(R.id.omnipod_wizard_action_progress_indication).visibility = isExecuting.toVisibility()
            view.findViewById<Button>(R.id.button_cancel).isEnabled = !isExecuting
        })

        viewModel.actionResultLiveData.observe(viewLifecycleOwner, { result ->
            result?.let {
                val isExecuting = isActionExecuting()

                view.findViewById<Button>(R.id.button_next).isEnabled = result.success
                view.findViewById<Button>(R.id.omnipod_wizard_action_success).visibility = result.success.toVisibility()
                view.findViewById<Button>(R.id.omnipod_wizard_action_error).visibility = (!isExecuting && !result.success).toVisibility()
                view.findViewById<Button>(R.id.omnipod_wizard_button_retry).visibility = (!isExecuting && !result.success).toVisibility()

                if (result.success) {
                    onActionSuccess()
                } else {
                    view.findViewById<Button>(R.id.omnipod_wizard_action_error).text = result.comment
                    onActionFailure()
                }
            }
        })

        if (savedInstanceState == null && !isActionExecuting()) {
            viewModel.executeAction()
        }

    }

    protected fun isActionExecuting() = viewModel.isActionExecutingLiveData.value!!

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.isActionExecutingLiveData.removeObservers(viewLifecycleOwner)
        viewModel.actionResultLiveData.removeObservers(viewLifecycleOwner)
    }

    fun onActionSuccess() {}

    open fun onActionFailure() {}

    @StringRes
    abstract fun getTextId(): Int

    @LayoutRes
    override fun getLayoutId(): Int {
        return R.layout.omnipod_wizard_action_page_fragment
    }
}