package info.nightscout.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.HasAndroidInjector
import info.nightscout.automation.actions.Action
import info.nightscout.automation.actions.ActionDummy
import info.nightscout.automation.databinding.AutomationDialogActionBinding
import info.nightscout.automation.events.EventAutomationUpdateAction
import info.nightscout.rx.bus.RxBus
import org.json.JSONObject
import javax.inject.Inject

class EditActionDialog : BaseDialog() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var injector: HasAndroidInjector

    private var action: Action? = null
    private var actionPosition: Int = -1

    private var _binding: AutomationDialogActionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            actionPosition = bundle.getInt("actionPosition", -1)
            bundle.getString("action")?.let { action = ActionDummy(injector).instantiate(JSONObject(it)) }
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
