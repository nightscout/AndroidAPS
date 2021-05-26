package info.nightscout.androidaps.plugins.pump.medtronic.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil
import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

/**
 * Created by andy on 5/19/18.
 * NOTE: This class is not used yet, so it has no Bindings
 *
 * This is for 3rd tab, called Medtronic (in RileyLink stats), that should work similarly as the one in Loop.
 *
 *
 * Showing currently selected RL, speed of RL, ability to issue simple commands (getModel, tuneUp, gerProfile)
 */
// TODO needs to be implemented
class RileyLinkStatusDeviceMedtronic : DaggerFragment(), RefreshableInterface {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil

    var listView: ListView? = null
    var adapter: RileyLinkCommandListAdapter? = null

    private var disposable: CompositeDisposable = CompositeDisposable()
    //private var _binding: RileyLinkStatusDeviceBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        // _binding = LoopFragmentBinding.inflate(inflater, container, false)
        // return binding.root

        val rootView = inflater.inflate(R.layout.rileylink_status_device, container, false)
        adapter = RileyLinkCommandListAdapter()
        return rootView
    }

    override fun onStart() {
        super.onStart()
        //listView = activity!!.findViewById(R.id.rileylink_history_list)
        //listView.setAdapter(adapter)
        refreshData()
    }

    override fun refreshData() {
        // adapter.addItemsAndClean(RileyLinkUtil.getRileyLinkHistory());
    }

    internal class ViewHolder {

        var itemTime: TextView? = null
        var itemSource: TextView? = null
        var itemDescription: TextView? = null
    }

    inner class RileyLinkCommandListAdapter : BaseAdapter() {

        private val historyItemList: MutableList<RLHistoryItem>
        private val mInflator: LayoutInflater
        fun addItem(item: RLHistoryItem) {
            if (!historyItemList.contains(item)) {
                historyItemList.add(item)
                notifyDataSetChanged()
            }
        }

        fun getHistoryItem(position: Int): RLHistoryItem {
            return historyItemList[position]
        }

        fun addItemsAndClean(items: List<RLHistoryItem>) {
            historyItemList.clear()
            for (item in items) {
                if (!historyItemList.contains(item)) {
                    historyItemList.add(item)
                }
            }
            notifyDataSetChanged()
        }

        fun clear() {
            historyItemList.clear()
            notifyDataSetChanged()
        }

        override fun getCount(): Int {
            return historyItemList.size
        }

        override fun getItem(i: Int): Any {
            return historyItemList[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, viewIn: View, viewGroup: ViewGroup): View {
            var view = viewIn
            val viewHolder: ViewHolder
            // General ListView optimization code.
//            if (view == null) {
            view = mInflator.inflate(R.layout.rileylink_status_device_item, null)
            viewHolder = ViewHolder()
            viewHolder.itemTime = view.findViewById(R.id.rileylink_history_time)
            viewHolder.itemSource = view.findViewById(R.id.rileylink_history_source)
            viewHolder.itemDescription = view.findViewById(R.id.rileylink_history_description)
            view.tag = viewHolder
            // }
            // else {
            //     viewHolder = view.tag as ViewHolder
            // }
            val item = historyItemList[i]
            viewHolder.itemTime!!.text = StringUtil.toDateTimeString(dateUtil, item.dateTime)
            viewHolder.itemSource!!.text = "Riley Link" // for now
            viewHolder.itemDescription!!.text = item.getDescription(resourceHelper)
            return view
        }

        init {
            historyItemList = ArrayList()
            mInflator = this@RileyLinkStatusDeviceMedtronic.layoutInflater
        }
    }
}