package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui
//import info.nightscout.androidaps.plugins.pump.omnipod.dash.definition.PodHistoryEntryType;
//import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.AapsOmnipodUtil;
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
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.DashHistory
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.HistoryRecordEntity
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import java.util.*
import javax.inject.Inject

class DashPodHistoryActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    //@Inject AapsOmnipodUtil aapsOmnipodUtil;
    //@Inject lateinit var resourceHelper: ResourceHelper

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

    private fun filterHistory(group: PumpHistoryEntryGroup) {
        filteredHistoryList.clear()
        aapsLogger.debug(LTag.PUMP, "Items on full list: {}", fullHistoryList.size)
        if (group === PumpHistoryEntryGroup.All) {
            filteredHistoryList.addAll(fullHistoryList)
        } /* Here you can add dedicated dao according to type of event selection in history Block
         disabled because PodHistoryEntryType doesn't exist in Dash module
        else {
            for (HistoryRecordEntity pumpHistoryEntry : fullHistoryList) {
                if (PodHistoryEntryType.getByCode(pumpHistoryEntry.getPodEntryTypeCode()).getGroup() == group) {
                    this.filteredHistoryList.add(pumpHistoryEntry);
                }
            }
        }*/
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
        for (i in typeListFull!!.indices) {
            if (typeListFull!![i].entryGroup === selectedGroup) {
                historyTypeSpinner!!.setSelection(i)
                break
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
        historyTypeSpinner = findViewById<Spinner>(R.id.omnipod_historytype)
        statusView = findViewById<TextView>(R.id.omnipod_historystatus)
        recyclerView = findViewById<RecyclerView>(R.id.omnipod_history_recyclerview)
        recyclerView!!.setHasFixedSize(true)
        linearLayoutManager = LinearLayoutManager(this)
        recyclerView!!.layoutManager = linearLayoutManager
        prepareData()
        recyclerViewAdapter = RecyclerViewAdapter(filteredHistoryList)
        recyclerView!!.adapter = recyclerViewAdapter
        statusView!!.visibility = View.GONE
        typeListFull = getTypeList(PumpHistoryEntryGroup.Companion.getTranslatedList(resourceHelper))!!
        val spinnerAdapter: ArrayAdapter<TypeList> = ArrayAdapter<TypeList>(this, R.layout.spinner_centered, typeListFull!!)
        historyTypeSpinner!!.setAdapter(spinnerAdapter)
        historyTypeSpinner!!.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                if (manualChange) return
                val selected = historyTypeSpinner!!.getSelectedItem() as TypeList
                selectedGroup = selected.entryGroup
                filterHistory(selectedGroup)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (manualChange) return
                filterHistory(PumpHistoryEntryGroup.All)
            }
        })
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
            val v: View = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.omnipod_dash_pod_history_item,  //
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record: HistoryRecordEntity = historyList[position]
            if (record != null) {
                holder.timeView.setText(DateTimeUtil.toStringFromTimeInMillis(record.date))
                //holder.typeView.setText(PodHistoryEntryType.getByCode(record.getPodEntryTypeCode()).getResourceId());
                setValue(record, holder.valueView)
            }
        }

        private fun setValue(historyEntry: HistoryRecordEntity, valueView: TextView) {
            valueView.text = historyEntry.toString()
            /* Here you define which information to show in history according to historyEntry Type
            if (historyEntry.isSuccess()) {
                PodHistoryEntryType entryType = PodHistoryEntryType.getByCode(historyEntry.getPodEntryTypeCode());
                switch (entryType) {

                    case SET_TEMPORARY_BASAL:
                    case SPLIT_TEMPORARY_BASAL: {
                        TempBasalPair tempBasalPair = aapsOmnipodUtil.getGsonInstance().fromJson(historyEntry.getData(), TempBasalPair.class);
                        valueView.setText(resourceHelper.gs(R.string.omnipod_eros_history_tbr_value, tempBasalPair.getInsulinRate(), tempBasalPair.getDurationMinutes()));
                    }
                    break;

                    case INSERT_CANNULA:
                    case SET_BASAL_SCHEDULE: {
                        if (historyEntry.getData() != null) {
                            setProfileValue(historyEntry.getData(), valueView);
                        }
                    }
                    break;

                    case SET_BOLUS: {
                        if (historyEntry.getData().contains(";")) {
                            String[] splitVal = historyEntry.getData().split(";");
                            valueView.setText(resourceHelper.gs(R.string.omnipod_eros_history_bolus_value_with_carbs, Double.valueOf(splitVal[0]), Double.valueOf(splitVal[1])));
                        } else {
                            valueView.setText(resourceHelper.gs(R.string.omnipod_eros_history_bolus_value, Double.valueOf(historyEntry.getData())));
                        }
                    }
                    break;

                    case PLAY_TEST_BEEP: {
                        if (historyEntry.getData() != null) {
                            valueView.setText(historyEntry.getData());
                        }
                    }
                    break;
                    case GET_POD_STATUS:
                    case GET_POD_INFO:
                    case SET_TIME:
                    case INITIALIZE_POD:
                    case CANCEL_TEMPORARY_BASAL_BY_DRIVER:
                    case CANCEL_TEMPORARY_BASAL:
                    case CONFIGURE_ALERTS:
                    case CANCEL_BOLUS:
                    case DEACTIVATE_POD:
                    case DISCARD_POD:
                    case ACKNOWLEDGE_ALERTS:
                    case SUSPEND_DELIVERY:
                    case RESUME_DELIVERY:
                    case UNKNOWN_ENTRY_TYPE:
                    default:
                        valueView.setText("");
                        break;

                }
            } else {
                valueView.setText(historyEntry.toString());
            }
            */
        }

        private fun setProfileValue(data: String, valueView: TextView) {
            aapsLogger.debug(LTag.PUMP, "Profile json:\n$data")
            valueView.setText("Profile informations from history")
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

            val timeView: TextView
            val typeView: TextView
            val valueView: TextView

            init {
                timeView = itemView.findViewById<TextView>(R.id.omnipod_history_time)
                typeView = itemView.findViewById<TextView>(R.id.omnipod_history_source)
                valueView = itemView.findViewById<TextView>(R.id.omnipod_history_description)
            }
        }

    }

    companion object {

        private var selectedGroup: PumpHistoryEntryGroup = PumpHistoryEntryGroup.All
    }
}