package info.nightscout.androidaps.plugins.general.automation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.SparseArray
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.util.forEach
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.automation.databinding.AutomationEventItemBinding
import info.nightscout.androidaps.automation.databinding.AutomationFragmentBinding
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.utils.ActionModeHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.dragHelpers.ItemTouchHelperAdapter
import info.nightscout.androidaps.utils.dragHelpers.OnStartDragListener
import info.nightscout.androidaps.utils.dragHelpers.SimpleItemTouchHelperCallback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class AutomationFragment : DaggerFragment(), OnStartDragListener {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var uel: UserEntryLogger

    private var disposable: CompositeDisposable = CompositeDisposable()
    private lateinit var eventListAdapter: EventListAdapter
    private lateinit var actionHelper: ActionModeHelper<AutomationEvent>
    private val itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback())
    private var _binding: AutomationFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutomationFragmentBinding.inflate(inflater, container, false)
        actionHelper = ActionModeHelper(rh, activity)
        actionHelper.setUpdateListHandler { binding.eventListView.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        actionHelper.enableSort = true
        setHasOptionsMenu(actionHelper.inMenu)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventListAdapter = EventListAdapter()
        binding.eventListView.layoutManager = LinearLayoutManager(context)
        binding.eventListView.adapter = eventListAdapter
        binding.logView.movementMethod = ScrollingMovementMethod()
        binding.fabAddEvent.setOnClickListener {
            actionHelper.finish()
            val dialog = EditEventDialog()
            val args = Bundle()
            args.putString("event", AutomationEvent(injector).toJSON())
            args.putInt("position", -1) // New event
            dialog.arguments = args
            dialog.show(childFragmentManager, "EditEventDialog")
        }

        itemTouchHelper.attachToRecyclerView(binding.eventListView)
    }

    @SuppressLint("NotifyDataSetChanged")
    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventAutomationUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateGui()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutomationDataChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           eventListAdapter.notifyDataSetChanged()
                       }, fabricPrivacy::logException)
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        actionHelper.finish()
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        actionHelper.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        actionHelper.onOptionsItemSelected(item)

    @Synchronized
    private fun updateGui() {
        if (_binding == null) return
        eventListAdapter.notifyDataSetChanged()
        val sb = StringBuilder()
        for (l in automationPlugin.executionLog.reversed())
            sb.append(l).append("<br>")
        binding.logView.text = HtmlHelper.fromHtml(sb.toString())
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    fun fillIconSet(connector: TriggerConnector, set: HashSet<Int>) {
        for (t in connector.list) {
            if (t is TriggerConnector) {
                fillIconSet(t, set)
            } else {
                val icon = t.icon()
                if (icon.isPresent) {
                    set.add(icon.get()!!)
                }
            }
        }
    }

    inner class EventListAdapter : RecyclerView.Adapter<EventListAdapter.ViewHolder>(), ItemTouchHelperAdapter {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.automation_event_item, parent, false)
            return ViewHolder(v, parent.context)
        }

        private fun addImage(@DrawableRes res: Int, context: Context, layout: LinearLayout) {
            val iv = ImageView(context)
            iv.setImageResource(res)
            iv.layoutParams = LinearLayout.LayoutParams(rh.dpToPx(24), rh.dpToPx(24))
            layout.addView(iv)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val automation = automationPlugin.at(position)
            holder.binding.rootLayout.setBackgroundColor(
                rh.gac( context,
                    if (automation.userAction) R.attr.userAction
                    else if (automation.areActionsValid()) R.attr.validActions
                    else R.attr.actionsError
                )
            )
            holder.binding.eventTitle.text = automation.title
            holder.binding.enabled.isChecked = automation.isEnabled
            holder.binding.enabled.isEnabled = !automation.readOnly
            holder.binding.iconLayout.removeAllViews()
            // trigger icons
            val triggerIcons = HashSet<Int>()
            if (automation.userAction) triggerIcons.add(R.drawable.ic_danar_useropt)
            fillIconSet(automation.trigger, triggerIcons)
            for (res in triggerIcons) {
                addImage(res, holder.context, holder.binding.iconLayout)
            }
            // arrow icon
            val iv = ImageView(holder.context)
            iv.setImageResource(R.drawable.ic_arrow_forward_white_24dp)
            iv.layoutParams = LinearLayout.LayoutParams(rh.dpToPx(24), rh.dpToPx(24))
            iv.setPadding(rh.dpToPx(4), 0, rh.dpToPx(4), 0)
            holder.binding.iconLayout.addView(iv)
            // action icons
            val actionIcons = HashSet<Int>()
            for (action in automation.actions) {
                actionIcons.add(action.icon())
            }
            for (res in actionIcons) {
                addImage(res, holder.context, holder.binding.iconLayout)
            }
            holder.binding.aapsLogo.visibility = (automation.systemAction).toVisibility()
            // Enabled events
            holder.binding.enabled.setOnClickListener {
                automation.isEnabled = holder.binding.enabled.isChecked
                rxBus.send(EventAutomationDataChanged())
            }
            holder.binding.rootLayout.setOnClickListener {
                if (actionHelper.isNoAction) {
                    val dialog = EditEventDialog()
                    val args = Bundle()
                    args.putString("event", automation.toJSON())
                    args.putInt("position", position)
                    dialog.arguments = args
                    dialog.show(childFragmentManager, "EditEventDialog")
                } else if (actionHelper.isRemoving) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, automation, holder.binding.cbRemove.isChecked)
                }
            }
            holder.binding.rootLayout.setOnLongClickListener {
                actionHelper.startAction()
            }
            holder.binding.sortHandle.setOnTouchListener { _, touchEvent ->
                if (touchEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                    return@setOnTouchListener true
                }
                return@setOnTouchListener false
            }
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, automation, value)
            }
            holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            holder.binding.sortHandle.visibility = actionHelper.isSorting.toVisibility()
            holder.binding.cbRemove.visibility = actionHelper.isRemoving.toVisibility()
            holder.binding.cbRemove.isEnabled = automation.readOnly.not()
            holder.binding.enabled.visibility = if (actionHelper.isRemoving) View.INVISIBLE else View.VISIBLE
        }

        override fun getItemCount() = automationPlugin.size()

        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            binding.eventListView.adapter?.notifyItemMoved(fromPosition, toPosition)
            automationPlugin.swap(fromPosition, toPosition)
            return true
        }

        override fun onDrop() = rxBus.send(EventAutomationDataChanged())

        inner class ViewHolder(view: View, val context: Context) : RecyclerView.ViewHolder(view) {

            val binding = AutomationEventItemBinding.bind(view)
        }
    }

    private fun getConfirmationText(selectedItems: SparseArray<AutomationEvent>): String {
        if (selectedItems.size() == 1) {
            val event = selectedItems.valueAt(0)
            return rh.gs(R.string.removerecord) + " " + event.title
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<AutomationEvent>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, event ->
                    uel.log(Action.AUTOMATION_REMOVED, Sources.Automation, event.title)
                    automationPlugin.removeAt(event.position)
                    rxBus.send(EventAutomationDataChanged())
                }
                actionHelper.finish()
            })
        }
    }

}
