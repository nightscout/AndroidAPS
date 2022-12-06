package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui

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
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.DashHistory
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BasalValuesRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.HistoryRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.TempBasalRecord
import info.nightscout.core.utils.DateTimeUtil
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.pump.common.defs.PumpHistoryEntryGroup
import info.nightscout.pump.common.utils.ProfileUtil
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject

class DashPodHistoryActivity : DaggerAppCompatActivity() {

    @Inject lateinit var dashHistory: DashHistory
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    private var historyTypeSpinner: Spinner? = null
    private var statusView: TextView? = null
    private var recyclerView: RecyclerView? = null
    private var linearLayoutManager: LinearLayoutManager? = null
    private val fullHistoryList: MutableList<HistoryRecord> = ArrayList<HistoryRecord>()
    private val filteredHistoryList: MutableList<HistoryRecord> = ArrayList<HistoryRecord>()
    private var recyclerViewAdapter: RecyclerViewAdapter? = null
    private var manualChange = false
    private var typeListFull: List<TypeList>? = null

    private fun prepareData() {
        val gc = GregorianCalendar()
        gc.add(Calendar.DAY_OF_MONTH, -DAYS_TO_DISPLAY)

        val since = gc.timeInMillis
        val records = dashHistory.getRecordsAfter(since)
            .subscribeOn(aapsSchedulers.io)
            .blockingGet()
        fullHistoryList.addAll(records)
    }

    private fun groupForCommandType(type: OmnipodCommandType): PumpHistoryEntryGroup {
        return when (type) {
            OmnipodCommandType.INITIALIZE_POD ->
                PumpHistoryEntryGroup.Prime
            OmnipodCommandType.INSERT_CANNULA ->
                PumpHistoryEntryGroup.Prime
            OmnipodCommandType.DEACTIVATE_POD ->
                PumpHistoryEntryGroup.Prime
            OmnipodCommandType.DISCARD_POD ->
                PumpHistoryEntryGroup.Prime

            OmnipodCommandType.CANCEL_TEMPORARY_BASAL ->
                PumpHistoryEntryGroup.Basal
            OmnipodCommandType.SET_BASAL_PROFILE ->
                PumpHistoryEntryGroup.Basal
            OmnipodCommandType.SET_TEMPORARY_BASAL ->
                PumpHistoryEntryGroup.Basal
            OmnipodCommandType.RESUME_DELIVERY ->
                PumpHistoryEntryGroup.Basal
            OmnipodCommandType.SUSPEND_DELIVERY ->
                PumpHistoryEntryGroup.Basal

            OmnipodCommandType.SET_BOLUS ->
                PumpHistoryEntryGroup.Bolus
            OmnipodCommandType.CANCEL_BOLUS ->
                PumpHistoryEntryGroup.Bolus

            OmnipodCommandType.ACKNOWLEDGE_ALERTS ->
                PumpHistoryEntryGroup.Alarm
            OmnipodCommandType.CONFIGURE_ALERTS ->
                PumpHistoryEntryGroup.Alarm
            OmnipodCommandType.PLAY_TEST_BEEP ->
                PumpHistoryEntryGroup.Alarm

            OmnipodCommandType.GET_POD_STATUS ->
                PumpHistoryEntryGroup.Configuration
            OmnipodCommandType.SET_TIME ->
                PumpHistoryEntryGroup.Configuration

            OmnipodCommandType.READ_POD_PULSE_LOG ->
                PumpHistoryEntryGroup.Unknown
        }
    }

    private fun filterHistory(group: PumpHistoryEntryGroup) {
        filteredHistoryList.clear()
        aapsLogger.debug(LTag.PUMP, "Items on full list: {}", fullHistoryList.size)
        if (group === PumpHistoryEntryGroup.All) {
            filteredHistoryList.addAll(fullHistoryList)
        } else {
            filteredHistoryList.addAll(fullHistoryList.filter { groupForCommandType(it.commandType) == group })
        }
        recyclerViewAdapter?.let {
            it.historyList = filteredHistoryList
            it.notifyDataSetChanged()
        }
        aapsLogger.debug(LTag.PUMP, "Items on filtered list: {}", filteredHistoryList.size)
    }

    override fun onResume() {
        super.onResume()
        filterHistory(selectedGroup)
        setHistoryTypeSpinner()
    }

    private fun setHistoryTypeSpinner() {
        manualChange = true
        val typeList = typeListFull
        typeList?.let {
            for (i in it.indices) {
                if (it[i].entryGroup === selectedGroup) {
                    historyTypeSpinner!!.setSelection(i)
                    break
                }
            }
        }
        SystemClock.sleep(200)
        manualChange = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.omnipod_dash_pod_history_activity)
        prepareData()

        recyclerView = findViewById(R.id.omnipod_history_recyclerview)
        recyclerViewAdapter = RecyclerViewAdapter(filteredHistoryList)
        linearLayoutManager = LinearLayoutManager(this)
        recyclerView?.run {
            setHasFixedSize(true)
            layoutManager = linearLayoutManager
            adapter = recyclerViewAdapter
        }

        statusView = findViewById(R.id.omnipod_historystatus)
        statusView?.run { visibility = View.GONE }

        historyTypeSpinner = findViewById(R.id.omnipod_historytype)
        typeListFull = getTypeList(PumpHistoryEntryGroup.Companion.getTranslatedList(rh))
        val spinnerAdapter: ArrayAdapter<TypeList> = ArrayAdapter<TypeList>(this, info.nightscout.core.ui.R.layout.spinner_centered, typeListFull!!)
        historyTypeSpinner?.run {
            adapter = spinnerAdapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (manualChange) return
                    val selected = selectedItem as TypeList
                    selectedGroup = selected.entryGroup
                    filterHistory(selectedGroup)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    if (manualChange) return
                    filterHistory(PumpHistoryEntryGroup.All)
                }
            }
        }
    }

    private fun getTypeList(list: List<PumpHistoryEntryGroup>): List<TypeList> {
        val typeList = ArrayList<TypeList>()
        for (pumpHistoryEntryGroup in list) {
            typeList.add(TypeList(pumpHistoryEntryGroup))
        }
        return typeList
    }

    internal class TypeList(val entryGroup: PumpHistoryEntryGroup) {

        val name: String = entryGroup.translated ?: "XXX TODO"

        override fun toString(): String {
            return name
        }
    }

    inner class RecyclerViewAdapter internal constructor(var historyList: List<HistoryRecord>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v: View = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.omnipod_dash_pod_history_item,
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record: HistoryRecord = historyList[position]
            record.let {
                holder.timeView.text = DateTimeUtil.toStringFromTimeInMillis(it.displayTimestamp())
                setValue(it, holder.valueView)
                setType(it, holder.typeView)
            }
        }

        private fun setTextViewColor(check_result: Boolean, textview: TextView, record: HistoryRecord) {
            if (check_result && !record.isSuccess()) {
                // Record says not success
                textview.setTextColor(rh.gac(textview.context, info.nightscout.core.ui.R.attr.omniYellowColor))
                return
            }
            // On success set color
            val textColorAttr = when (record.commandType) {
                // Operational
                OmnipodCommandType.INITIALIZE_POD,
                OmnipodCommandType.CONFIGURE_ALERTS,
                OmnipodCommandType.INSERT_CANNULA,
                OmnipodCommandType.DEACTIVATE_POD,
                OmnipodCommandType.DISCARD_POD,
                OmnipodCommandType.SUSPEND_DELIVERY,
                OmnipodCommandType.RESUME_DELIVERY,
                OmnipodCommandType.SET_BASAL_PROFILE -> {
                    info.nightscout.core.ui.R.attr.omniCyanColor
                }
                // User action
                OmnipodCommandType.PLAY_TEST_BEEP,
                OmnipodCommandType.ACKNOWLEDGE_ALERTS,
                OmnipodCommandType.CANCEL_BOLUS -> {
                    info.nightscout.core.ui.R.attr.omniCyanColor
                }
                // Insulin treatment
                OmnipodCommandType.SET_BOLUS,
                OmnipodCommandType.SET_TEMPORARY_BASAL -> {
                    info.nightscout.core.ui.R.attr.defaultTextColor
                }

                else ->
                    // Other
                    info.nightscout.core.ui.R.attr.omniGrayColor
            }
            textview.setTextColor(rh.gac(textview.context, textColorAttr))
        }

        private fun setType(record: HistoryRecord, typeView: TextView) {
            typeView.text = rh.gs(record.commandType.resourceId)
            // Set some color, include result
            setTextViewColor(check_result = true, typeView, record)
        }

        private fun setValue(historyEntry: HistoryRecord, valueView: TextView) {
            valueView.text = historyEntry.toString()
            // val entryType = historyEntry.commandType
            if (!historyEntry.isSuccess()) {
                valueView.text = rh.gs(translatedFailure(historyEntry))
                return
            }
            valueView.text = when (historyEntry.commandType) {
                OmnipodCommandType.SET_TEMPORARY_BASAL -> {
                    val tbr = historyEntry.record as TempBasalRecord
                    tbr.let {
                        rh.gs(R.string.omnipod_common_history_tbr_value, it.rate, it.duration)
                    }
                }

                OmnipodCommandType.SET_BOLUS -> {
                    val bolus = historyEntry.record as BolusRecord
                    bolus.let {
                        rh.gs(R.string.omnipod_common_history_bolus_value, it.amout)
                    }
                }

                OmnipodCommandType.SET_BASAL_PROFILE,
                OmnipodCommandType.SET_TIME,
                OmnipodCommandType.INSERT_CANNULA,
                OmnipodCommandType.RESUME_DELIVERY -> {
                    val basal = historyEntry.record as BasalValuesRecord
                    ProfileUtil.getBasalProfilesDisplayable(basal.segments.toTypedArray(), PumpType.OMNIPOD_DASH)
                }

                else ->
                    ""
            }
            // Set some color
            setTextViewColor(check_result = false, valueView, historyEntry)
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val timeView: TextView = itemView.findViewById(R.id.omnipod_history_time)
            val typeView: TextView = itemView.findViewById(R.id.omnipod_history_source)
            val valueView: TextView = itemView.findViewById(R.id.omnipod_history_description)
        }
    }

    private fun translatedFailure(historyEntry: HistoryRecord): Int {
        return when {
            historyEntry.initialResult == InitialResult.FAILURE_SENDING ->
                R.string.omnipod_dash_failed_to_send
            historyEntry.initialResult == InitialResult.NOT_SENT ->
                R.string.omnipod_dash_command_not_sent
            historyEntry.initialResult == InitialResult.SENT &&
                historyEntry.resolvedResult == ResolvedResult.FAILURE ->
                R.string.omnipod_dash_command_not_received_by_the_pod
            else ->
                R.string.omnipod_dash_unknown
        }
    }

    companion object {

        private var selectedGroup: PumpHistoryEntryGroup = PumpHistoryEntryGroup.All
        const val DAYS_TO_DISPLAY = 5
    }
}
