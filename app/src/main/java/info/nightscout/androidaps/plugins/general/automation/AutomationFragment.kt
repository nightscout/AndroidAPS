package info.nightscout.androidaps.plugins.general.automation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.ItemTouchHelperAdapter
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.ItemTouchHelperViewHolder
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.OnStartDragListener
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.SimpleItemTouchHelperCallback
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.automation_fragment.*
import java.util.*
import javax.inject.Inject

class AutomationFragment : DaggerFragment(), OnStartDragListener {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var mainApp : MainApp

    private var disposable: CompositeDisposable = CompositeDisposable()
    private lateinit var eventListAdapter: EventListAdapter

    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.automation_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventListAdapter = EventListAdapter()
        automation_eventListView.layoutManager = LinearLayoutManager(context)
        automation_eventListView.adapter = eventListAdapter

        automation_logView.movementMethod = ScrollingMovementMethod()

        automation_fabAddEvent.setOnClickListener {
            val dialog = EditEventDialog()
            val args = Bundle()
            args.putString("event", AutomationEvent(mainApp).toJSON())
            args.putInt("position", -1) // New event
            dialog.arguments = args
            dialog.show(childFragmentManager, "EditEventDialog")
        }

        val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(eventListAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(automation_eventListView)

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventAutomationUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateGui()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventAutomationDataChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                eventListAdapter.notifyDataSetChanged()
            }, { fabricPrivacy.logException(it) })
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun updateGui() {
        eventListAdapter.notifyDataSetChanged()
        val sb = StringBuilder()
        for (l in automationPlugin.executionLog.reversed())
            sb.append(l).append("<br>")
        automation_logView?.text = HtmlHelper.fromHtml(sb.toString())
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper?.startDrag(viewHolder)
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
            iv.layoutParams = LinearLayout.LayoutParams(resourceHelper.dpToPx(24), resourceHelper.dpToPx(24))
            layout.addView(iv)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = automationPlugin.automationEvents[position]
            holder.eventTitle.text = event.title
            holder.enabled.isChecked = event.isEnabled
            holder.iconLayout.removeAllViews()
            // trigger icons
            val triggerIcons = HashSet<Int>()
            fillIconSet(event.trigger as TriggerConnector, triggerIcons)
            for (res in triggerIcons) {
                addImage(res, holder.context, holder.iconLayout)
            }
            // arrow icon
            val iv = ImageView(holder.context)
            iv.setImageResource(R.drawable.ic_arrow_forward_white_24dp)
            iv.layoutParams = LinearLayout.LayoutParams(resourceHelper.dpToPx(24), resourceHelper.dpToPx(24))
            iv.setPadding(resourceHelper.dpToPx(4), 0, resourceHelper.dpToPx(4), 0)
            holder.iconLayout.addView(iv)
            // action icons
            val actionIcons = HashSet<Int>()
            for (action in event.actions) {
                actionIcons.add(action.icon())
            }
            for (res in actionIcons) {
                addImage(res, holder.context, holder.iconLayout)
            }
            // enabled event
            holder.enabled.setOnClickListener {
                event.isEnabled = holder.enabled.isChecked
                rxBus.send(EventAutomationDataChanged())
            }
            // edit event
            holder.rootLayout.setOnClickListener {
                val dialog = EditEventDialog()
                val args = Bundle()
                args.putString("event", event.toJSON())
                args.putInt("position", position)
                dialog.arguments = args
                dialog.show(childFragmentManager, "EditEventDialog")
            }
            // Start a drag whenever the handle view it touched
            holder.iconSort.setOnTouchListener { v: View, motionEvent: MotionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    this@AutomationFragment.onStartDrag(holder)
                    return@setOnTouchListener true
                }
                v.onTouchEvent(motionEvent)
            }
        }

        override fun getItemCount(): Int = automationPlugin.automationEvents.size

        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            Collections.swap(automationPlugin.automationEvents, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
            rxBus.send(EventAutomationDataChanged())
            return true
        }

        override fun onItemDismiss(position: Int) {
            activity?.let { activity ->
                showConfirmation(activity, resourceHelper.gs(R.string.removerecord) + " " + automationPlugin.automationEvents[position].title,
                    Runnable {
                        automationPlugin.automationEvents.removeAt(position)
                        notifyItemRemoved(position)
                        rxBus.send(EventAutomationDataChanged())
                        rxBus.send(EventAutomationUpdateGui())
                    }, Runnable { rxBus.send(EventAutomationUpdateGui()) })
            }
        }

        inner class ViewHolder(view: View, val context: Context) : RecyclerView.ViewHolder(view), ItemTouchHelperViewHolder {
            val rootLayout: RelativeLayout = view.findViewById(R.id.rootLayout)
            val iconLayout: LinearLayout = view.findViewById(R.id.iconLayout)
            val eventTitle: TextView = view.findViewById(R.id.viewEventTitle)
            val iconSort: ImageView = view.findViewById(R.id.iconSort)
            val enabled: CheckBox = view.findViewById(R.id.automation_enabled)

            override fun onItemSelected() = itemView.setBackgroundColor(Color.LTGRAY)

            override fun onItemClear() = itemView.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault))
        }
    }
}
