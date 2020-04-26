package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateTrigger
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import kotlinx.android.synthetic.main.automation_dialog_edit_trigger.*

class EditTriggerDialog : DialogFragmentWithDate() {

    private var trigger: Trigger? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            bundle.getString("trigger")?.let { trigger = Trigger.instantiate(it) }
        }

        onCreateViewGeneral()
        return inflater.inflate(R.layout.automation_dialog_edit_trigger, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // display root trigger
        trigger?.generateDialog(automation_layoutTrigger, fragmentManager)
    }

    override fun submit():Boolean {
        trigger?.let { trigger -> RxBus.send(EventAutomationUpdateTrigger(trigger)) }
        return true
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        trigger?.let { savedInstanceState.putString("trigger", it.toJSON()) }
    }
}