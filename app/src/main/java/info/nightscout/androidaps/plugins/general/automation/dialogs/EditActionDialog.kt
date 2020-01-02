package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateAction
import kotlinx.android.synthetic.main.automation_dialog_action.*
import org.json.JSONObject
import javax.inject.Inject

class EditActionDialog : DialogFragmentWithDate() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var mainApp: MainApp

    private var action: Action? = null
    private var actionPosition: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            actionPosition = bundle.getInt("actionPosition", -1)
            bundle.getString("action")?.let { action = ActionDummy(mainApp).instantiate(JSONObject(it)) }
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
