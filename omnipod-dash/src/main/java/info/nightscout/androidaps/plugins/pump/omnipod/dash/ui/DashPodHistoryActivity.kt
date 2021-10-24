package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui
// import info.nightscout.androidaps.plugins.pump.omnipod.dash.definition.PodHistoryEntryType;
// import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.AapsOmnipodUtil;
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
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.defs.PumpHistoryEntryGroup
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.DashHistory
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordEntity
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import java.util.*
import javax.inject.Inject

class DashPodHistoryActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dashHistory: DashHistory
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var historyTypeSpinner: Spinner? = null
    private var statusView: TextView? = null
    private var recyclerView: RecyclerView? = null
    private var linearLayoutManager: LinearLayoutManager? = null
    private val fullHistoryList: MutableList<HistoryRecordEntity> = ArrayList<HistoryRecordEntity>()
    private val filteredHistoryList: MutableList<HistoryRecordEntity> = ArrayList<HistoryRecordEntity>()
    private var recyclerViewAdapter: RecyclerViewAdapter? = null
    private var manualChange = false
    private var typeListFull: List<TypeList>? = null

    private fun prepareData() {
        val gc = GregorianCalendar()
        // TODO: limit to the last 3 days. Using 30days here for testing
        gc.add(Calendar.DAY_OF_MONTH, -30)

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

    override fun onPause() {
        super.onPause()
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
        typeListFull = getTypeList(PumpHistoryEntryGroup.Companion.getTranslatedList(resourceHelper))
        val spinnerAdapter: ArrayAdapter<TypeList> = ArrayAdapter<TypeList>(this, R.layout.spinner_centered, typeListFull!!)
        historyTypeSpinner?.run {
            adapter = spinnerAdapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
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

    internal class TypeList(entryGroup: PumpHistoryEntryGroup) {

        val entryGroup: PumpHistoryEntryGroup = entryGroup
        val name: String = entryGroup.translated ?: "XXX TODO"

        override fun toString(): String {
            return name
        }
    }

    inner class RecyclerViewAdapter internal constructor(historyList: List<HistoryRecordEntity>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        var historyList: List<HistoryRecordEntity> = historyList

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v: View = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.omnipod_dash_pod_history_item,
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record: HistoryRecordEntity = historyList[position]
            record?.let {
                holder.timeView.text = DateTimeUtil.toStringFromTimeInMillis(record.displayTimestamp())
                setValue(record, holder.valueView)
                setType(record, holder.typeView)
            }
        }

        private fun setType(record: HistoryRecordEntity, typeView: TextView) {
            typeView.text = resourceHelper.gs(record.commandType.resourceId)
        }

        private fun setValue(historyEntry: HistoryRecordEntity, valueView: TextView) {
            valueView.text = historyEntry.toString()
            // val entryType = historyEntry.commandType
            if (!historyEntry.isSuccess()) {
                valueView.text = resourceHelper.gs(translatedFailure(historyEntry))
                return
            }
            valueView.text = when (historyEntry.commandType) {
                OmnipodCommandType.SET_TEMPORARY_BASAL -> {
                    val tbr = historyEntry.tempBasalRecord
                    tbr?.let {
                        resourceHelper.gs(R.string.omnipod_common_history_tbr_value, it.rate, it.duration)
                    } ?: "n/a"
                }
                OmnipodCommandType.SET_BOLUS -> {
                    val bolus = historyEntry.bolusRecord
                    bolus?.let {
                        resourceHelper.gs(R.string.omnipod_common_history_bolus_value, it.amout)
                    } ?: "n/a"
                }
                else ->
                    ""
            }
        }

        private fun setProfileValue(data: String, valueView: TextView) {
            aapsLogger.debug(LTag.PUMP, "Profile json:\n$data")
            valueView.text = "Profile informations from history"
            /*
            try {
                Profile.ProfileValue[] profileValuesArray = aapsOmnipodUtil.getGsonInstance().fromJson(data, Profile.ProfileValue[].class);
                valueView.setText(ProfileUtil.INSTANCE.getBasalProfilesDisplayable(profileValuesArray, PumpType.OMNIPOD_EROS));
            } catch (Exception e) {
                aapsLogger.error(LTag.PUMP, "Problem parsing Profile json. Ex: {}, Data:\n{}", e.getMessage(), data);
                valueView.setText("");
            }
            */
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val timeView: TextView = itemView.findViewById(R.id.omnipod_history_time)
            val typeView: TextView = itemView.findViewById(R.id.omnipod_history_source)
            val valueView: TextView = itemView.findViewById(R.id.omnipod_history_description)
        }
    }

    private fun translatedFailure(historyEntry: HistoryRecordEntity): Int {
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
    }
}
