package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateTrigger
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import kotlinx.android.synthetic.main.automation_dialog_edit_trigger.*
import kotlinx.android.synthetic.main.okcancel.*

class EditTriggerDialog : DialogFragment() {

    private var trigger: Trigger? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            bundle.getString("trigger")?.let { trigger = Trigger.instantiate(it) }
        }

        dialog?.setCanceledOnTouchOutside(false)
        return inflater.inflate(R.layout.automation_dialog_edit_trigger, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // display root trigger
        trigger?.generateDialog(automation_layoutTrigger, fragmentManager)

        // OK button
        ok.setOnClickListener {
            dismiss()
            trigger?.let { trigger -> RxBus.send(EventAutomationUpdateTrigger(trigger)) }
        }

        // Cancel button
        cancel.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        trigger?.let { bundle.putString("trigger", it.toJSON()) }
    }
}