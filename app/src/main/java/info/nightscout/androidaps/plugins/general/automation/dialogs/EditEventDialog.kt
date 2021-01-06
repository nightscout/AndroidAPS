package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.AutomationDialogEventBinding
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationAddAction
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateAction
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateTrigger
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class EditEventDialog : DialogFragmentWithDate() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var automationPlugin: AutomationPlugin

    private var actionListAdapter: ActionListAdapter? = null
    private lateinit var event: AutomationEvent
    private var position: Int = -1

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: AutomationDialogEventBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        event = AutomationEvent(mainApp)
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            position = bundle.getInt("position", -1)
            bundle.getString("event")?.let { event = AutomationEvent(mainApp).fromJSON(it) }
        }

        onCreateViewGeneral()
        _binding = AutomationDialogEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.okcancel.ok.visibility = (!event.readOnly).toVisibility()

        binding.inputEventTitle.setText(event.title)
        binding.inputEventTitle.isFocusable = !event.readOnly
        binding.triggerDescription.text = event.trigger.friendlyDescription()

        binding.editTrigger.visibility = (!event.readOnly).toVisibility()
        binding.editTrigger.setOnClickListener {
            val args = Bundle()
            args.putString("trigger", event.trigger.toJSON())
            val dialog = EditTriggerDialog()
            dialog.arguments = args
            dialog.show(childFragmentManager, "EditTriggerDialog")
        }

        // setup action list view
        actionListAdapter = ActionListAdapter()
        binding.actionListView.layoutManager = LinearLayoutManager(context)
        binding.actionListView.adapter = actionListAdapter

        binding.addAction.visibility = (!event.readOnly).toVisibility()
        binding.addAction.setOnClickListener { ChooseActionDialog().show(childFragmentManager, "ChooseActionDialog") }

        showPreconditions()

        disposable += rxBus
            .toObservable(EventAutomationUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                actionListAdapter?.notifyDataSetChanged()
                showPreconditions()
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutomationAddAction::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                event.addAction(it.action)
                actionListAdapter?.notifyDataSetChanged()
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutomationUpdateTrigger::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                event.trigger = it.trigger
                binding.triggerDescription.text = event.trigger.friendlyDescription()
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutomationUpdateAction::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                event.actions[it.position] = it.action
                actionListAdapter?.notifyDataSetChanged()
            }, fabricPrivacy::logException)
    }

    override fun submit(): Boolean {
        // check for title
        val title = binding.inputEventTitle.text?.toString() ?: return false
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
            automationPlugin.add(event)
        else
            automationPlugin.set(event, position)

        rxBus.send(EventAutomationDataChanged())
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putString("event", event.toJSON())
        savedInstanceState.putInt("position", position)
    }

    private fun showPreconditions() {
        val forcedTriggers = event.getPreconditions()
        if (forcedTriggers.size() > 0) {
            binding.forcedTriggerDescription.visibility = View.VISIBLE
            binding.forcedTriggerDescriptionLabel.visibility = View.VISIBLE
            binding.forcedTriggerDescription.text = forcedTriggers.friendlyDescription()
        } else {
            binding.forcedTriggerDescription.visibility = View.GONE
            binding.forcedTriggerDescriptionLabel.visibility = View.GONE
        }
    }

    inner class ActionListAdapter : RecyclerView.Adapter<ActionListAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.automation_action_item, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val action = event.actions[position]
            holder.bind(action, this, position)
        }

        override fun getItemCount(): Int = event.actions.size

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

            fun bind(action: Action, recyclerView: RecyclerView.Adapter<ViewHolder>, position: Int) {
                if (!event.readOnly)
                    view.findViewById<LinearLayout>(R.id.automation_layoutText).setOnClickListener {
                        if (action.hasDialog()) {
                            val args = Bundle()
                            args.putInt("actionPosition", position)
                            args.putString("action", action.toJSON())
                            val dialog = EditActionDialog()
                            dialog.arguments = args
                            dialog.show(childFragmentManager, "EditActionDialog")
                        }
                    }
                view.findViewById<ImageView>(R.id.automation_iconTrash).run {
                    visibility = (!event.readOnly).toVisibility()
                    setOnClickListener {
                        event.actions.remove(action)
                        recyclerView.notifyDataSetChanged()
                        rxBus.send(EventAutomationUpdateGui())
                    }
                }
                view.findViewById<ImageView>(R.id.automation_action_image).setImageResource(action.icon())
                view.findViewById<TextView>(R.id.automation_viewActionTitle).text = action.shortDescription()
            }
        }
    }
}
