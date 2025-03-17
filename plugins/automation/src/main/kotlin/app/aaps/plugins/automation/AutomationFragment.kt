package app.aaps.plugins.automation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.util.forEach
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.dragHelpers.ItemTouchHelperAdapter
import app.aaps.core.ui.dragHelpers.OnStartDragListener
import app.aaps.core.ui.dragHelpers.SimpleItemTouchHelperCallback
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.automation.databinding.AutomationEventItemBinding
import app.aaps.plugins.automation.databinding.AutomationFragmentBinding
import app.aaps.plugins.automation.dialogs.EditEventDialog
import app.aaps.plugins.automation.events.EventAutomationDataChanged
import app.aaps.plugins.automation.events.EventAutomationUpdateGui
import app.aaps.plugins.automation.triggers.TriggerConnector
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class AutomationFragment : DaggerFragment(), OnStartDragListener, MenuProvider {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var uel: UserEntryLogger

    companion object {
        const val ID_MENU_ADD = 504
        const val ID_MENU_RUN = 505
    }

    private var disposable: CompositeDisposable = CompositeDisposable()
    private lateinit var eventListAdapter: EventListAdapter
    private lateinit var actionHelper: ActionModeHelper<AutomationEventObject>
    private val itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback())
    private var _binding: AutomationFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutomationFragmentBinding.inflate(inflater, container, false)
        actionHelper = ActionModeHelper(rh, activity, this)
        actionHelper.setUpdateListHandler { binding.eventListView.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        actionHelper.enableSort = true
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventListAdapter = EventListAdapter()
        binding.eventListView.layoutManager = LinearLayoutManager(context)
        binding.eventListView.adapter = eventListAdapter
        binding.logView.movementMethod = ScrollingMovementMethod()

        itemTouchHelper.attachToRecyclerView(binding.eventListView)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        actionHelper.onCreateOptionsMenu(menu, inflater)
        menu.add(Menu.FIRST, ID_MENU_ADD, 0, rh.gs(R.string.add_automation)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_RUN, 0, rh.gs(R.string.run_automations)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        if (actionHelper.onOptionsItemSelected(item)) true
        else when (item.itemId) {
            ID_MENU_RUN -> {
                Thread { automationPlugin.processActions() }.start()
                true
            }

            ID_MENU_ADD -> {
                add()
                true
            }


            else        -> super.onContextItemSelected(item)
        }

    @SuppressLint("NotifyDataSetChanged")
    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventAutomationUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutomationDataChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           eventListAdapter.notifyDataSetChanged()
                           rxBus.send(EventWearUpdateTiles())
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

    @SuppressLint("NotifyDataSetChanged")
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
                    set.add(icon.get())
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
                rh.gac(
                    context,
                    when {
                        automation.userAction        -> app.aaps.core.ui.R.attr.userAction
                        automation.areActionsValid() -> app.aaps.core.ui.R.attr.validActions
                        else                         -> app.aaps.core.ui.R.attr.actionsError
                    }
                )
            )
            holder.binding.eventTitle.text = automation.title
            holder.binding.enabled.isChecked = automation.isEnabled
            holder.binding.enabled.isEnabled = !automation.readOnly
            holder.binding.iconLayout.removeAllViews()
            // trigger icons
            val triggerIcons = HashSet<Int>()
            if (automation.userAction) triggerIcons.add(app.aaps.core.ui.R.drawable.ic_user_options)
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

    private fun getConfirmationText(selectedItems: SparseArray<AutomationEventObject>): String {
        if (selectedItems.size() == 1) {
            val event = selectedItems.valueAt(0)
            return rh.gs(app.aaps.core.ui.R.string.removerecord) + " " + event.title
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<AutomationEventObject>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, event ->
                    uel.log(Action.AUTOMATION_REMOVED, Sources.Automation, event.title)
                    automationPlugin.remove(event)
                    rxBus.send(EventAutomationDataChanged())
                }
                actionHelper.finish()
            })
        }
    }

    private fun add() {
        actionHelper.finish()
        EditEventDialog().also {
            it.arguments = Bundle().apply {
                putString("event", AutomationEventObject(injector).toJSON())
                putInt("position", -1) // New event
            }
        }.show(childFragmentManager, "EditEventDialog")
    }
}