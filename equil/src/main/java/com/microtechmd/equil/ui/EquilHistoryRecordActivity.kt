package com.microtechmd.equil.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.microtechmd.equil.EquilConst
import com.microtechmd.equil.EquilPumpPlugin
import com.microtechmd.equil.R
import com.microtechmd.equil.data.database.EquilHistoryRecord
import com.microtechmd.equil.data.database.EquilHistoryRecordDao
import com.microtechmd.equil.databinding.EquilHistoryRecordActivityBinding
import com.microtechmd.equil.events.EventEquilDataChanged
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.ble.BlePreCheck
import info.nightscout.androidaps.plugins.pump.common.defs.PumpHistoryEntryGroup
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilHistoryRecordActivity : DaggerAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var context: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var equilHistoryRecordDao: EquilHistoryRecordDao
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin

    private lateinit var binding: EquilHistoryRecordActivityBinding
    lateinit var llm: LinearLayoutManager
    lateinit var recyclerViewAdapter: RecyclerViewAdapter

    private val disposable = CompositeDisposable()
    var filteredHistoryList: MutableList<EquilHistoryRecord> = java.util.ArrayList<EquilHistoryRecord>()
    private val fullHistoryList: MutableList<EquilHistoryRecord> = java.util.ArrayList<EquilHistoryRecord>()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    @Inject lateinit var rxBus: RxBus
    val calendar: Calendar = Calendar.getInstance();
    val dateformat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dateformat2 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private var typeListFull: List<TypeList>? = null
    private var manualChange = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EquilHistoryRecordActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = rh.gs(R.string.equil_title_history_events)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        loadData()

        recyclerViewAdapter = RecyclerViewAdapter(filteredHistoryList, rh)
        llm = LinearLayoutManager(this)
        binding.recyclerview?.run {
            setHasFixedSize(true)
            layoutManager = llm
            adapter = recyclerViewAdapter
        }

        disposable += rxBus
            .toObservable(EventEquilDataChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ loadData() }, fabricPrivacy::logException)
        typeListFull = getTypeList(PumpHistoryEntryGroup.Companion.getTranslatedList(rh))

        val spinnerAdapter: ArrayAdapter<TypeList> = ArrayAdapter<TypeList>(this, R.layout.spinner_centered, typeListFull!!)
        binding.equilHistorytype?.run {
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

    private fun filterHistory(group: PumpHistoryEntryGroup) {
        filteredHistoryList.clear()
        aapsLogger.error(LTag.EQUILBLE, "Items on full list: {}", fullHistoryList.size)
        if (group === PumpHistoryEntryGroup.All) {
            aapsLogger.error(LTag.EQUILBLE, "alll===")
            filteredHistoryList.addAll(fullHistoryList)
        } else {
            filteredHistoryList.addAll(fullHistoryList.filter { groupForCommandType(it.type) == group })
        }

        recyclerViewAdapter?.let {
            it.historyList = filteredHistoryList
            it.notifyDataSetChanged()
        }
        aapsLogger.error(LTag.EQUILBLE, "Items on filtered list: {}", filteredHistoryList.size)
    }

    private fun groupForCommandType(type: EquilHistoryRecord.EventType): PumpHistoryEntryGroup {
        return when (type) {
            EquilHistoryRecord.EventType.INITIALIZE_EQUIL       ->
                PumpHistoryEntryGroup.Prime

            EquilHistoryRecord.EventType.INSERT_CANNULA         ->
                PumpHistoryEntryGroup.Prime

            // EquilHistoryRecord.EventType.DEACTIVATE_POD         ->
            //     PumpHistoryEntryGroup.Prime
            //
            // EquilHistoryRecord.EventType.DISCARD_POD            ->
            //     PumpHistoryEntryGroup.Prime

            EquilHistoryRecord.EventType.CANCEL_TEMPORARY_BASAL ->
                PumpHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_BASAL_PROFILE      ->
                PumpHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL    ->
                PumpHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.RESUME_DELIVERY        ->
                PumpHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SUSPEND_DELIVERY       ->
                PumpHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_BOLUS              ->
                PumpHistoryEntryGroup.Bolus

            EquilHistoryRecord.EventType.CANCEL_BOLUS           ->
                PumpHistoryEntryGroup.Bolus

            EquilHistoryRecord.EventType.SET_TIME               ->
                PumpHistoryEntryGroup.Configuration

            // EquilHistoryRecord.EventType.READ_POD_PULSE_LOG     ->
            //     PumpHistoryEntryGroup.Unknown
            else                                                -> {
                PumpHistoryEntryGroup.All

            }
        }
    }

    private fun getTypeList(list: List<PumpHistoryEntryGroup>): List<TypeList> {
        val typeList = java.util.ArrayList<TypeList>()
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

    fun loadData() {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        var startTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        var endTime = calendar.timeInMillis
        aapsLogger.error(LTag.EQUILBLE, "loadData===" + dateformat2.format(startTime) + "====" + dateformat2.format(endTime))
        disposable += equilHistoryRecordDao
            .allSince(endTime)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ historyList ->
                           // aapsLogger.error(LTag.EQUILBLE, "historyList===" + historyList.size)
                           fullHistoryList.clear()
                           fullHistoryList.addAll(historyList)
                           // }
                       }) {
                aapsLogger.error(LTag.EQUILBLE, "historyListerror===" + it)
            }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    class RecyclerViewAdapter internal constructor(
        var historyList: List<EquilHistoryRecord>,
        var rh: ResourceHelper
    ) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        fun setHistoryListInternal(historyList: List<EquilHistoryRecord>) {
            this.historyList = historyList
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.equil_item_record,  //
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = historyList[position]
            holder.timeView.text = android.text.format.DateFormat.format(
                "yyyy-MM-dd HH:mm:ss",
                item.timestamp
            ).toString()
            // holder.timeView.text = item.type.name
            // holder.typeView.text = item.serialNumber
            holder.typeView.text = item.tempBasalRecord?.rate.toString()

            holder.typeView.text = rh.gs(item.type.resourceId)

            holder.valueView.text = when (item.type) {
                EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL -> {
                    val tbr = item.tempBasalRecord
                    val duration = (tbr?.duration?.div(60 * 1000) ?: 0)

                    tbr.let {
                        rh.gs(R.string.equil_common_history_tbr_value, it?.rate, duration)
                    }
                }

                EquilHistoryRecord.EventType.SET_BOLUS           -> {
                    val bolus = item.bolusRecord
                    bolus.let {
                        rh.gs(R.string.equil_common_history_bolus_value, it?.amout)
                    }
                }

                else                                             ->
                    ""
            }

            // holder.typeView.setTextColor(textColor)
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var timeView: TextView
            var typeView: TextView
            var valueView: TextView

            // var valueView: TextView
            init {
                timeView = itemView.findViewById(R.id.equil_history_time)
                typeView = itemView.findViewById(R.id.equil_history_source)
                valueView = itemView.findViewById(R.id.equil_history_description)
            }
        }

    }

    companion object {

        private var selectedGroup: PumpHistoryEntryGroup = PumpHistoryEntryGroup.All
        const val DAYS_TO_DISPLAY = 5
    }
}
