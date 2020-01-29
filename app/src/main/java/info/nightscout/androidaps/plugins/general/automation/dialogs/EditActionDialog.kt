package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateAction
import kotlinx.android.synthetic.main.automation_dialog_action.*
import org.json.JSONObject

class EditActionDialog : DialogFragmentWithDate() {
    private var action: Action? = null
    private var actionPosition: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            actionPosition = bundle.getInt("actionPosition", -1)
            bundle.getString("action")?.let { action = Action.instantiate(JSONObject(it)) }
        }
        onCreateViewGeneral()
        return inflater.inflate(R.layout.automation_dialog_action, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        action?.let {
            automation_actionTitle.setText(it.friendlyName())
            automation_editActionLayout.removeAllViews()
            it.generateDialog(automation_editActionLayout)
        }
    }

    override fun submit(): Boolean {
        action?.let {
            RxBus.send(EventAutomationUpdateAction(it, actionPosition))
        }
        return true
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        action?.let {
            bundle.putInt("actionPosition", actionPosition)
            bundle.putString("action", it.toJSON())
        }
    }
}
