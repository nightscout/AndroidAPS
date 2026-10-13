package app.aaps.pump.medtronic.dialog

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.medtronic.R
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.data.MedtronicHistoryData
import dagger.android.DaggerActivity
import javax.inject.Inject

class MedtronicHistoryActivity : DaggerActivity() {

    @Inject lateinit var medtronicHistoryData: MedtronicHistoryData
    @Inject lateinit var rh: ResourceHelper

    lateinit var historyTypeSpinner: Spinner
    lateinit var statusView: TextView
    lateinit var recyclerView: RecyclerView
    lateinit var llm: LinearLayoutManager
    lateinit var recyclerViewAdapter: RecyclerViewAdapter

    var filteredHistoryList: MutableList<PumpHistoryEntry> = ArrayList()
    var manualChange = false
    lateinit var typeListFull: List<TypeList>

    //private var _binding: MedtronicHistoryActivityBinding? = null

    //@Inject
    //var fragmentInjector: DispatchingAndroidInjector<Fragment>? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    private fun filterHistory(group: PumpHistoryEntryGroup) {
        filteredHistoryList.clear()
        val list: MutableList<PumpHistoryEntry> = ArrayList()
        list.addAll(medtronicHistoryData.allHistory)

        //LOG.debug("Items on full list: {}", list.size());
        if (group === PumpHistoryEntryGroup.All) {
            filteredHistoryList.addAll(list)
        } else {
            for (pumpHistoryEntry in list) {
                if (pumpHistoryEntry.entryType.group === group) {
                    filteredHistoryList.add(pumpHistoryEntry)
                }
            }
        }

        recyclerViewAdapter.setHistoryListInternal(filteredHistoryList)
        recyclerViewAdapter.notifyDataSetChanged()

        //LOG.debug("Items on filtered list: {}", filteredHistoryList.size());
    }

    override fun onResume() {
        super.onResume()
        filterHistory(selectedGroup)
        setHistoryTypeSpinner()
    }

    private fun setHistoryTypeSpinner() {
        manualChange = true
        for (i in typeListFull.indices) {
            if (typeListFull[i].entryGroup === selectedGroup) {
                historyTypeSpinner.setSelection(i)
                break
            }
        }
        SystemClock.sleep(200)
        manualChange = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.medtronic_history_activity)
        historyTypeSpinner = findViewById(R.id.medtronic_historytype)
        statusView = findViewById(R.id.medtronic_historystatus)
        recyclerView = findViewById(R.id.medtronic_history_recyclerview)
        recyclerView.setHasFixedSize(true)
        llm = LinearLayoutManager(this)
        recyclerView.layoutManager = llm
        recyclerViewAdapter = RecyclerViewAdapter(filteredHistoryList)
        recyclerView.adapter = recyclerViewAdapter
        statusView.visibility = View.GONE
        typeListFull = getTypeList(PumpHistoryEntryGroup.getTranslatedList(rh))
        val spinnerAdapter = ArrayAdapter(this, app.aaps.core.ui.R.layout.spinner_centered, typeListFull)
        historyTypeSpinner.adapter = spinnerAdapter
        historyTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (manualChange) return
                val selected = historyTypeSpinner.selectedItem as TypeList
                showingType = selected
                selectedGroup = selected.entryGroup
                filterHistory(selectedGroup)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (manualChange) return
                filterHistory(PumpHistoryEntryGroup.All)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        historyTypeSpinner.adapter = null
        historyTypeSpinner.onItemSelectedListener = null
    }

    private fun getTypeList(list: List<PumpHistoryEntryGroup>): List<TypeList> {
        val typeList = ArrayList<TypeList>()
        for (pumpHistoryEntryGroup in list) {
            typeList.add(TypeList(pumpHistoryEntryGroup))
        }
        return typeList
    }

    class TypeList internal constructor(var entryGroup: PumpHistoryEntryGroup) {

        var name: String = entryGroup.translated!!
        override fun toString(): String {
            return name
        }
    }

    class RecyclerViewAdapter internal constructor(var historyList: List<PumpHistoryEntry>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        fun setHistoryListInternal(historyList: List<PumpHistoryEntry>) {
            // this.historyList.clear();
            // this.historyList.addAll(historyList);
            this.historyList = historyList

            // this.notifyDataSetChanged();
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.medtronic_history_item,  //
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.timeView.text = record.dateTimeString
            holder.typeView.text = record.entryType.description
            holder.valueView.text = record.displayableValue
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            // cv = (CardView)itemView.findViewById(R.id.rileylink_history_item);
            var timeView: TextView = itemView.findViewById(R.id.medtronic_history_time)
            var typeView: TextView = itemView.findViewById(R.id.medtronic_history_source)
            var valueView: TextView = itemView.findViewById(R.id.medtronic_history_description)
        }

    }

    companion object {

        var showingType: TypeList? = null
        var selectedGroup = PumpHistoryEntryGroup.All
    }
}