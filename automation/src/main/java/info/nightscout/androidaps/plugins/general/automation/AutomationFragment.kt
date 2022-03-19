package info.nightscout.androidaps.plugins.general.automation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.SparseArray
import android.view.*
import androidx.core.util.forEach
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
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
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import io.reactivex.rxjava3.kotlin.plusAssign
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.ItemTouchHelperAdapter
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.OnStartDragListener
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.SimpleItemTouchHelperCallback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.*
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
    private var selectedItems: SparseArray<AutomationEvent> = SparseArray()
    private var removeActionMode: ActionMode? = null
    private var sortActionMode: ActionMode? = null
    private val itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback())

    private var _binding: AutomationFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutomationFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        eventListAdapter = EventListAdapter()
        binding.eventListView.layoutManager = LinearLayoutManager(context)
        binding.eventListView.adapter = eventListAdapter
        binding.logView.movementMethod = ScrollingMovementMethod()
        binding.fabAddEvent.setOnClickListener {
            removeActionMode?.finish()
            sortActionMode?.finish()
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
        removeActionMode?.finish()
        sortActionMode?.finish()
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        removeActionMode?.finish()
        sortActionMode?.finish()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_automation, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        // Only show when tab automation is shown
        menu.findItem(R.id.nav_remove_automation_items)?.isVisible = isResumed
        menu.findItem(R.id.nav_sort_automation_items)?.isVisible = isResumed
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_remove_automation_items -> {
                removeActionMode = activity?.startActionMode(RemoveActionModeCallback())
                true
            }

            R.id.nav_sort_automation_items   -> {
                sortActionMode = activity?.startActionMode(SortActionModeCallback())
                true
            }

            else                             -> false
        }
    }

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
                rh.gc(
                    if (automation.userAction) R.color.mdtp_line_dark
                    else if (automation.areActionsValid()) R.color.ribbonDefault
                    else R.color.errorAlertBackground
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
            // enabled event
            holder.binding.enabled.setOnClickListener {
                automation.isEnabled = holder.binding.enabled.isChecked
                rxBus.send(EventAutomationDataChanged())
            }
            holder.binding.aapsLogo.visibility = (automation.systemAction).toVisibility()

            fun updateSelection(selected: Boolean) {
                if (selected) {
                    selectedItems.put(position, automation)
                } else {
                    selectedItems.remove(position)
                }
                removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
            }

            holder.binding.rootLayout.setOnTouchListener { _, touchEvent ->
                if (touchEvent.actionMasked == MotionEvent.ACTION_UP && sortActionMode == null && removeActionMode == null) {
                    val dialog = EditEventDialog()
                    val args = Bundle()
                    args.putString("event", automation.toJSON())
                    args.putInt("position", position)
                    dialog.arguments = args
                    dialog.show(childFragmentManager, "EditEventDialog")
                }
                if (touchEvent.actionMasked == MotionEvent.ACTION_DOWN && sortActionMode != null) {
                    onStartDrag(holder)
                }
                if (touchEvent.actionMasked == MotionEvent.ACTION_UP && removeActionMode != null) {
                    holder.binding.cbRemove.toggle()
                    updateSelection(holder.binding.cbRemove.isChecked)
                    removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
                }
                return@setOnTouchListener true
            }
            holder.binding.cbRemove.isChecked = selectedItems.get(position) != null
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value -> updateSelection(value) }
            holder.binding.iconSort.visibility = (sortActionMode != null).toVisibility()
            holder.binding.cbRemove.visibility = (removeActionMode != null).toVisibility()
            holder.binding.cbRemove.isEnabled = !automation.readOnly
            holder.binding.enabled.visibility = if (removeActionMode == null) View.VISIBLE else View.INVISIBLE
        }

        override fun getItemCount(): Int = automationPlugin.size()

        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            automationPlugin.swap(fromPosition, toPosition)
            return true
        }

        override fun onDrop() {
            rxBus.send(EventAutomationDataChanged())
        }

        inner class ViewHolder(view: View, val context: Context) : RecyclerView.ViewHolder(view) {

            val binding = AutomationEventItemBinding.bind(view)
        }
    }

    inner class SortActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.title = rh.gs(R.string.sort_label)
            binding.eventListView.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false

        override fun onDestroyActionMode(mode: ActionMode?) {
            sortActionMode = null
            binding.eventListView.adapter?.notifyDataSetChanged()
        }
    }

    inner class RemoveActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater.inflate(R.menu.menu_delete_selection, menu)
            selectedItems.clear()
            mode.title = rh.gs(R.string.count_selected, selectedItems.size())
            binding.eventListView.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.remove_selected -> {
                    removeSelected()
                    true
                }

                else                 -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            removeActionMode = null
            binding.eventListView.adapter?.notifyDataSetChanged()
        }
    }

    private fun getConfirmationText(): String {
        if (selectedItems.size() == 1) {
            val event = selectedItems.valueAt(0)
            return rh.gs(R.string.removerecord) + " " + event.title
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected() {
        if (selectedItems.size() > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(), Runnable {
                    selectedItems.forEach { _, event ->
                        uel.log(Action.AUTOMATION_REMOVED, Sources.Automation, event.title)
                        automationPlugin.removeAt(event.position)
                        rxBus.send(EventAutomationDataChanged())
                    }
                    removeActionMode?.finish()
                })
            }
        } else {
            removeActionMode?.finish()
        }
    }
}
