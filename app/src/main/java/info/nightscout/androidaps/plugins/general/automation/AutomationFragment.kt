package info.nightscout.androidaps.plugins.general.automation

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.OnStartDragListener
import info.nightscout.androidaps.plugins.general.automation.dragHelpers.SimpleItemTouchHelperCallback
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.automation_fragment.*

class AutomationFragment : Fragment(), OnStartDragListener {

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var eventListAdapter: EventListAdapter? = null

    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.automation_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventListAdapter = EventListAdapter(AutomationPlugin.automationEvents, fragmentManager, activity, this)
        automation_eventListView.layoutManager = LinearLayoutManager(context)
        automation_eventListView.adapter = eventListAdapter

        automation_logView.setMovementMethod(ScrollingMovementMethod())

        automation_fabAddEvent.setOnClickListener {
            val dialog = EditEventDialog()
            val args = Bundle()
            args.putString("event", AutomationEvent().toJSON())
            args.putInt("position", -1) // New event
            dialog.arguments = args
            fragmentManager?.let { dialog.show(it, "EditEventDialog") }
        }

        val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(eventListAdapter!!)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(automation_eventListView)

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += RxBus
                .toObservable(EventAutomationUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGui()
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventAutomationDataChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    eventListAdapter?.notifyDataSetChanged()
                }, {
                    FabricPrivacy.logException(it)
                })
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun updateGui() {
        eventListAdapter?.notifyDataSetChanged()
        val sb = StringBuilder()
        for (l in AutomationPlugin.executionLog.reversed())
            sb.append(l).append("<br>")
        automation_logView?.text = HtmlHelper.fromHtml(sb.toString())
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper?.startDrag(viewHolder);
    }

}
