package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.databinding.AutomationDialogActionBinding
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateAction
import org.json.JSONObject
import javax.inject.Inject

class EditActionDialog : DialogFragmentWithDate() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var mainApp: MainApp

    private var action: Action? = null
    private var actionPosition: Int = -1

    private var _binding: AutomationDialogActionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            actionPosition = bundle.getInt("actionPosition", -1)
            bundle.getString("action")?.let { action = ActionDummy(mainApp).instantiate(JSONObject(it)) }
        }
        onCreateViewGeneral()
        _binding = AutomationDialogActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        action?.let {
            binding.actionTitle.setText(it.friendlyName())
            binding.editActionLayout.removeAllViews()
            it.generateDialog(binding.editActionLayout)
        }
    }

    override fun submit(): Boolean {
        action?.let {
            rxBus.send(EventAutomationUpdateAction(it, actionPosition))
        }
        return true
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        action?.let {
            savedInstanceState.putInt("actionPosition", actionPosition)
            savedInstanceState.putString("action", it.toJSON())
        }
    }
}
