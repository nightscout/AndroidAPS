package app.aaps.pump.omnipod.eros.ui

import android.annotation.SuppressLint
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
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.common.defs.PumpHistoryEntryGroup.Companion.getTranslatedList
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.definition.PodHistoryEntryType
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject

class ErosPodHistoryActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsOmnipodUtil: AapsOmnipodUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var erosHistory: ErosHistory
    @Inject lateinit var profileUtil: ProfileUtil

    private var historyTypeSpinner: Spinner? = null
    private var statusView: TextView? = null
    private var recyclerView: RecyclerView? = null
    private var linearLayoutManager: LinearLayoutManager? = null
    private val fullHistoryList: MutableList<ErosHistoryRecordEntity> = ArrayList()
    private val filteredHistoryList: MutableList<ErosHistoryRecordEntity> = ArrayList()
    private var recyclerViewAdapter: RecyclerViewAdapter? = null
    private var manualChange = false
    private var typeListFull: List<TypeList> = ArrayList()
    private var selectedGroup = PumpHistoryEntryGroup.All
    private fun prepareData() {
        val gc = GregorianCalendar()
        gc.add(Calendar.HOUR_OF_DAY, -24)
        fullHistoryList.addAll(erosHistory.getAllErosHistoryRecordsFromTimestamp(gc.timeInMillis))
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterHistory(group: PumpHistoryEntryGroup) {
        filteredHistoryList.clear()
        aapsLogger.debug(LTag.PUMP, "Items on full list: {}", fullHistoryList.size)
        if (group === PumpHistoryEntryGroup.All) {
            filteredHistoryList.addAll(fullHistoryList)
        } else {
            for (pumpHistoryEntry in fullHistoryList) {
                if (PodHistoryEntryType.getByCode(pumpHistoryEntry.podEntryTypeCode).group === group) {
                    filteredHistoryList.add(pumpHistoryEntry)
                }
            }
        }
        recyclerViewAdapter?.setHistoryList(filteredHistoryList)
        recyclerViewAdapter?.notifyDataSetChanged()
        aapsLogger.debug(LTag.PUMP, "Items on filtered list: {}", filteredHistoryList.size)
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
                historyTypeSpinner?.setSelection(i)
                break
            }
        }
        SystemClock.sleep(200)
        manualChange = false
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.omnipod_eros_pod_history_activity)

        title = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_management_button_pod_history)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        historyTypeSpinner = findViewById(R.id.omnipod_historytype)
        statusView = findViewById(R.id.omnipod_historystatus)
        recyclerView = findViewById(R.id.omnipod_history_recyclerview)
        recyclerView?.setHasFixedSize(true)
        linearLayoutManager = LinearLayoutManager(this)
        recyclerView?.layoutManager = linearLayoutManager
        prepareData()
        recyclerViewAdapter = RecyclerViewAdapter(filteredHistoryList)
        recyclerView?.adapter = recyclerViewAdapter
        statusView?.visibility = View.GONE
        typeListFull = getTypeList(getTranslatedList(rh))
        historyTypeSpinner?.adapter = ArrayAdapter(this, app.aaps.core.ui.R.layout.spinner_centered, typeListFull)
        historyTypeSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                if (manualChange) return
                val selected = historyTypeSpinner?.selectedItem as TypeList
                selectedGroup = selected.entryGroup
                filterHistory(selectedGroup)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (manualChange) return
                filterHistory(PumpHistoryEntryGroup.All)
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

        val name: String = entryGroup.translated ?: ""
        override fun toString(): String = name
    }

    inner class RecyclerViewAdapter internal constructor(private var historyList: MutableList<ErosHistoryRecordEntity>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        fun setHistoryList(historyList: MutableList<ErosHistoryRecordEntity>) {
            this.historyList = historyList
            this.historyList.sort()
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.omnipod_eros_pod_history_item,  //
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.timeView.text = record.dateTimeString
            holder.typeView.setText(PodHistoryEntryType.getByCode(record.podEntryTypeCode).resourceId)
            setValue(record, holder.valueView)
        }

        private fun setValue(historyEntry: ErosHistoryRecordEntity, valueView: TextView) {
            //valueView.setText("");
            if (historyEntry.isSuccess) {
                when (PodHistoryEntryType.getByCode(historyEntry.podEntryTypeCode)) {
                    PodHistoryEntryType.SET_TEMPORARY_BASAL, PodHistoryEntryType.SPLIT_TEMPORARY_BASAL                                                                                                                                                                                                                                                                                                                                                                                                                                                               -> {
                        val tempBasalPair = aapsOmnipodUtil.gsonInstance.fromJson(historyEntry.data, TempBasalPair::class.java)
                        valueView.text = rh.gs(R.string.omnipod_eros_history_tbr_value, tempBasalPair.insulinRate, tempBasalPair.durationMinutes)
                    }

                    PodHistoryEntryType.INSERT_CANNULA, PodHistoryEntryType.SET_BASAL_SCHEDULE                                                                                                                                                                                                                                                                                                                                                                                                                                                                       -> {
                        historyEntry.data?.let {
                            setProfileValue(it, valueView)
                        }
                    }

                    PodHistoryEntryType.SET_BOLUS                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    -> {
                        historyEntry.data?.let {
                            if (it.contains(";")) {
                                val splitVal = it.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                valueView.text = rh.gs(R.string.omnipod_eros_history_bolus_value_with_carbs, java.lang.Double.valueOf(splitVal[0]), java.lang.Double.valueOf(splitVal[1]))
                            } else {
                                valueView.text = rh.gs(R.string.omnipod_eros_history_bolus_value, java.lang.Double.valueOf(it))
                            }
                        }
                    }

                    PodHistoryEntryType.PLAY_TEST_BEEP                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               -> {
                        if (historyEntry.data != null) {
                            valueView.text = historyEntry.data
                        }
                    }

                    PodHistoryEntryType.GET_POD_STATUS, PodHistoryEntryType.GET_POD_INFO, PodHistoryEntryType.SET_TIME, PodHistoryEntryType.INITIALIZE_POD, PodHistoryEntryType.CANCEL_TEMPORARY_BASAL_BY_DRIVER, PodHistoryEntryType.CANCEL_TEMPORARY_BASAL, PodHistoryEntryType.CONFIGURE_ALERTS, PodHistoryEntryType.CANCEL_BOLUS, PodHistoryEntryType.DEACTIVATE_POD, PodHistoryEntryType.DISCARD_POD, PodHistoryEntryType.ACKNOWLEDGE_ALERTS, PodHistoryEntryType.SUSPEND_DELIVERY, PodHistoryEntryType.RESUME_DELIVERY, PodHistoryEntryType.UNKNOWN_ENTRY_TYPE -> valueView.text =
                        ""

                    else                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             -> valueView.text =
                        ""
                }
            } else {
                valueView.text = historyEntry.data
            }
        }

        private fun setProfileValue(data: String, valueView: TextView) {
            aapsLogger.debug(LTag.PUMP, "Profile json:\n$data")
            try {
                val profileValuesArray = aapsOmnipodUtil.gsonInstance.fromJson(data, Array<ProfileValue>::class.java)
                valueView.text = profileUtil.getBasalProfilesDisplayable(profileValuesArray, PumpType.OMNIPOD_EROS)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Problem parsing Profile json. Ex: {}, Data:\n{}", e.message, data)
                valueView.text = ""
            }
        }

        override fun getItemCount(): Int = historyList.size

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val timeView: TextView = itemView.findViewById(R.id.omnipod_history_time)
            val typeView: TextView = itemView.findViewById(R.id.omnipod_history_source)
            val valueView: TextView = itemView.findViewById(R.id.omnipod_history_description)
        }
    }
}
