package app.aaps.pump.common.hw.rileylink.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.common.hw.rileylink.R
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem
import app.aaps.pump.common.hw.rileylink.databinding.RileylinkStatusHistoryBinding
import app.aaps.pump.common.hw.rileylink.databinding.RileylinkStatusHistoryItemBinding
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class RileyLinkStatusHistoryFragment : DaggerFragment() {

    @Inject lateinit var rileyLinkUtil: RileyLinkUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil

    private var _binding: RileylinkStatusHistoryBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        RileylinkStatusHistoryBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.historyList.setHasFixedSize(true)
        binding.historyList.layoutManager = LinearLayoutManager(view.context)
        binding.refresh.setOnClickListener { refreshData() }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        binding.historyList.adapter =
            RecyclerViewAdapter(rileyLinkUtil.rileyLinkHistory.filter { isValidItem(it) }.sortedWith(RLHistoryItem.Comparator()))
    }

    private fun isValidItem(item: RLHistoryItem): Boolean =
        item.pumpDeviceState !== PumpDeviceState.Sleeping &&
            item.pumpDeviceState !== PumpDeviceState.Active &&
            item.pumpDeviceState !== PumpDeviceState.WakingUp

    inner class RecyclerViewAdapter internal constructor(private val historyList: List<RLHistoryItem>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.rileylink_status_history_item, viewGroup, false)
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = historyList[position]
            holder.binding.historyTime.text = dateUtil.dateAndTimeAndSecondsString(item.dateTime.toDateTime().millis)
            holder.binding.historySource.text = item.source.desc
            holder.binding.historyDescription.text = item.getDescription(rh)
        }

        override fun getItemCount(): Int = historyList.size

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = RileylinkStatusHistoryItemBinding.bind(itemView)
        }
    }
}