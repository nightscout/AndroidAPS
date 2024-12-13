package app.aaps.pump.omnipod.dash.ui

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
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.utils.DateTimeUtil
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.dash.R
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.dash.history.data.BolusRecord
import app.aaps.pump.omnipod.dash.history.data.HistoryRecord
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import app.aaps.pump.omnipod.dash.history.data.TempBasalRecord
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject

class DashPodHistoryActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var dashHistory: DashHistory
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileUtil: ProfileUtil

    private var historyTypeSpinner: Spinner? = null
    private var statusView: TextView? = null
    private var recyclerView: RecyclerView? = null
    private var linearLayoutManager: LinearLayoutManager? = null
    private val fullHistoryList: MutableList<HistoryRecord> = ArrayList()
    private val filteredHistoryList: MutableList<HistoryRecord> = ArrayList()
    private var recyclerViewAdapter: RecyclerViewAdapter? = null
    private var manualChange = false
    private var typeListFull: List<TypeList>? = null
    private var selectedGroup: PumpHistoryEntryGroup = PumpHistoryEntryGroup.All

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
            OmnipodCommandType.INITIALIZE_POD         ->
                PumpHistoryEntryGroup.Prime

            OmnipodCommandType.INSERT_CANNULA         ->
                PumpHistoryEntryGroup.Prime

            OmnipodCommandType.DEACTIVATE_POD         ->
                PumpHistoryEntryGroup.Prime

            OmnipodCommandType.DISCARD_POD            ->
                PumpHistoryEntryGroup.Prime

            OmnipodCommandType.CANCEL_TEMPORARY_BASAL ->
                PumpHistoryEntryGroup.Basal

            OmnipodCommandType.SET_BASAL_PROFILE      ->
                PumpHistoryEntryGroup.Basal

            OmnipodCommandType.SET_TEMPORARY_BASAL    ->
                PumpHistoryEntryGroup.Basal

            OmnipodCommandType.RESUME_DELIVERY        ->
                PumpHistoryEntryGroup.Basal

            OmnipodCommandType.SUSPEND_DELIVERY       ->
                PumpHistoryEntryGroup.Basal

            OmnipodCommandType.SET_BOLUS              ->
                PumpHistoryEntryGroup.Bolus

            OmnipodCommandType.CANCEL_BOLUS           ->
                PumpHistoryEntryGroup.Bolus

            OmnipodCommandType.ACKNOWLEDGE_ALERTS     ->
                PumpHistoryEntryGroup.Alarm

            OmnipodCommandType.CONFIGURE_ALERTS       ->
                PumpHistoryEntryGroup.Alarm

            OmnipodCommandType.PLAY_TEST_BEEP         ->
                PumpHistoryEntryGroup.Alarm

            OmnipodCommandType.GET_POD_STATUS         ->
                PumpHistoryEntryGroup.Configuration

            OmnipodCommandType.SET_TIME               ->
                PumpHistoryEntryGroup.Configuration

            OmnipodCommandType.READ_POD_PULSE_LOG     ->
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

        title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_pod_history)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

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
        val spinnerAdapter: ArrayAdapter<TypeList> = ArrayAdapter<TypeList>(this, app.aaps.core.ui.R.layout.spinner_centered, typeListFull!!)
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
                setAmount(it, holder.amountView)
            }
        }

        private fun setTextViewColor(checkResult: Boolean, textview: TextView, record: HistoryRecord) {
            if (checkResult && !record.isSuccess()) {
                // Record says not success
                textview.setTextColor(rh.gac(textview.context, app.aaps.core.ui.R.attr.omniYellowColor))
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
                OmnipodCommandType.SET_BASAL_PROFILE   -> {
                    app.aaps.core.ui.R.attr.omniCyanColor
                }
                // User action
                OmnipodCommandType.PLAY_TEST_BEEP,
                OmnipodCommandType.ACKNOWLEDGE_ALERTS,
                OmnipodCommandType.CANCEL_BOLUS        -> {
                    app.aaps.core.ui.R.attr.omniCyanColor
                }
                // Insulin treatment
                OmnipodCommandType.SET_BOLUS,
                OmnipodCommandType.SET_TEMPORARY_BASAL -> {
                    app.aaps.core.ui.R.attr.defaultTextColor
                }

                else                                   ->
                    // Other
                    app.aaps.core.ui.R.attr.omniGrayColor
            }
            textview.setTextColor(rh.gac(textview.context, textColorAttr))
        }

        private fun setType(record: HistoryRecord, typeView: TextView) {
            typeView.text = rh.gs(record.commandType.resourceId)
            // Set some color, include result
            setTextViewColor(checkResult = true, typeView, record)
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

                OmnipodCommandType.SET_BOLUS           -> {
                    val bolus = historyEntry.record as BolusRecord
                    bolus.let {
                        rh.gs(R.string.omnipod_common_history_bolus_value, it.amout)
                    }
                }

                OmnipodCommandType.SET_BASAL_PROFILE,
                OmnipodCommandType.SET_TIME,
                OmnipodCommandType.INSERT_CANNULA,
                OmnipodCommandType.RESUME_DELIVERY     -> {
                    val basal = historyEntry.record as BasalValuesRecord
                    profileUtil.getBasalProfilesDisplayable(basal.segments.toTypedArray(), PumpType.OMNIPOD_DASH)
                }

                else                                   ->
                    ""
            }
            // Set some color
            setTextViewColor(checkResult = false, valueView, historyEntry)
        }

        private fun setAmount(historyEntry: HistoryRecord, amountView: TextView) {
            amountView.text = historyEntry.totalAmountDelivered?.let { rh.gs(R.string.omnipod_common_history_total_delivered, it) }
            // Set some color
            setTextViewColor(checkResult = false, amountView, historyEntry)
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val timeView: TextView = itemView.findViewById(R.id.omnipod_history_time)
            val typeView: TextView = itemView.findViewById(R.id.omnipod_history_source)
            val valueView: TextView = itemView.findViewById(R.id.omnipod_history_description)
            val amountView: TextView = itemView.findViewById(R.id.omnipod_history_amount)
        }
    }

    private fun translatedFailure(historyEntry: HistoryRecord): Int {
        return when {
            historyEntry.initialResult == InitialResult.FAILURE_SENDING ->
                R.string.omnipod_dash_failed_to_send

            historyEntry.initialResult == InitialResult.NOT_SENT        ->
                R.string.omnipod_dash_command_not_sent

            historyEntry.initialResult == InitialResult.SENT &&
                historyEntry.resolvedResult == ResolvedResult.FAILURE   ->
                R.string.omnipod_dash_command_not_received_by_the_pod

            else                                                        ->
                R.string.omnipod_dash_unknown
        }
    }

    companion object {

        const val DAYS_TO_DISPLAY = 5
    }
}
