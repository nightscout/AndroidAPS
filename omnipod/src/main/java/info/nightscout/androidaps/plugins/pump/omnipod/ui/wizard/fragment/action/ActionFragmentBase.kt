package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.action

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.lifecycle.Observer
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.fragment.ChangePodWizardFragmentBase
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.viewmodel.ActionViewModelBase
import info.nightscout.androidaps.utils.extensions.toVisibility
import kotlinx.android.synthetic.main.omnipod_change_pod_wizard_action_page_fragment.*
import kotlinx.android.synthetic.main.omnipod_change_pod_wizard_nav_buttons.*

abstract class ActionFragmentBase : ChangePodWizardFragmentBase() {
    protected lateinit var viewModel: ActionViewModelBase

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_change_pod_wizard_button_next.isEnabled = false
        omnipod_change_pod_wizard_action_page_text.setText(getTextId())

        omnipod_change_pod_wizard_button_retry.setOnClickListener { viewModel.executeAction() }

        viewModel.isActionExecutingLiveData.observe(viewLifecycleOwner, Observer { isExecuting ->
            if (isExecuting) {
                omnipod_change_pod_wizard_action_error.visibility = View.GONE
                omnipod_change_pod_wizard_button_deactivate_pod.visibility = View.GONE
                omnipod_change_pod_wizard_button_discard_pod.visibility = View.GONE
                omnipod_change_pod_wizard_button_retry.visibility = View.GONE
            }
            omnipod_change_pod_wizard_action_progress_indication.visibility = isExecuting.toVisibility()
            omnipod_change_pod_wizard_button_cancel.isEnabled = !isExecuting
        })

        viewModel.actionResultLiveData.observe(viewLifecycleOwner, Observer { result ->
            result?.let {
                val isExecuting = isActionExecuting()

                omnipod_change_pod_wizard_button_next.isEnabled = result.success
                omnipod_change_pod_wizard_action_success.visibility = result.success.toVisibility()
                omnipod_change_pod_wizard_action_error.visibility = (!isExecuting && !result.success).toVisibility()
                omnipod_change_pod_wizard_button_retry.visibility = (!isExecuting && !result.success).toVisibility()

                if (result.success) {
                    onActionSuccess()
                } else {
                    omnipod_change_pod_wizard_action_error.text = result.comment
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
        return R.layout.omnipod_change_pod_wizard_action_page_fragment
    }
}