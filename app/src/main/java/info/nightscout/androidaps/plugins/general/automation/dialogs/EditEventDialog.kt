package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.events.*
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.ToastUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.automation_dialog_event.*

class EditEventDialog : DialogFragmentWithDate() {

    private var actionListAdapter: ActionListAdapter? = null
    private var event: AutomationEvent = AutomationEvent()
    private var position: Int = -1

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            position = bundle.getInt("position", -1)
            bundle.getString("event")?.let { event = AutomationEvent().fromJSON(it) }
        }

        onCreateViewGeneral()
        return inflater.inflate(R.layout.automation_dialog_event, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        automation_inputEventTitle.setText(event.title)
        automation_triggerDescription.text = event.trigger.friendlyDescription()

        automation_editTrigger.setOnClickListener {
            val args = Bundle()
            args.putString("trigger", event.trigger.toJSON())
            val dialog = EditTriggerDialog()
            dialog.arguments = args
            fragmentManager?.let { dialog.show(it, "EditTriggerDialog") }
        }

        // setup action list view
        fragmentManager?.let { actionListAdapter = ActionListAdapter(it, event.actions) }
        automation_actionListView.layoutManager = LinearLayoutManager(context)
        automation_actionListView.adapter = actionListAdapter

        automation_addAction.setOnClickListener { fragmentManager?.let { ChooseActionDialog().show(it, "ChooseActionDialog") } }

        showPreconditions()

        disposable.add(RxBus
                .toObservable(EventAutomationUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    actionListAdapter?.notifyDataSetChanged()
                    showPreconditions()
                }, {
                    FabricPrivacy.logException(it)
                })
        )
        disposable.add(RxBus
                .toObservable(EventAutomationAddAction::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    event.addAction(it.action)
                    actionListAdapter?.notifyDataSetChanged()
                }, {
                    FabricPrivacy.logException(it)
                })
        )
        disposable.add(RxBus
                .toObservable(EventAutomationUpdateTrigger::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    event.trigger = it.trigger
                    automation_triggerDescription.text = event.trigger.friendlyDescription()
                }, {
                    FabricPrivacy.logException(it)
                })
        )
        disposable.add(RxBus
                .toObservable(EventAutomationUpdateAction::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    event.actions[it.position] = it.action
                    actionListAdapter?.notifyDataSetChanged()
                }, {
                    FabricPrivacy.logException(it)
                })
        )
    }

    override fun submit() : Boolean{
        // check for title
        val title = automation_inputEventTitle.text.toString()
        if (title.isEmpty()) {
            ToastUtils.showToastInUiThread(context, R.string.automation_missing_task_name)
            return false
        }
        event.title = title
        // check for at least one trigger
        val con = event.trigger as TriggerConnector
        if (con.size() == 0) {
            ToastUtils.showToastInUiThread(context, R.string.automation_missing_trigger)
            return false
        }
        // check for at least one action
        if (event.actions.isEmpty()) {
            ToastUtils.showToastInUiThread(context, R.string.automation_missing_action)
            return false
        }
        // store
        if (position == -1)
            AutomationPlugin.automationEvents.add(event)
        else
            AutomationPlugin.automationEvents[position] = event

        RxBus.send(EventAutomationDataChanged())
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putString("event", event.toJSON())
        savedInstanceState.putInt("position", position)
    }

    private fun showPreconditions() {
        val forcedTriggers = event.preconditions
        if (forcedTriggers.size() > 0) {
            automation_forcedTriggerDescription.visibility = View.VISIBLE
            automation_forcedTriggerDescriptionLabel.visibility = View.VISIBLE
            automation_forcedTriggerDescription.text = forcedTriggers.friendlyDescription()
        } else {
            automation_forcedTriggerDescription.visibility = View.GONE
            automation_forcedTriggerDescriptionLabel.visibility = View.GONE
        }
    }
}
