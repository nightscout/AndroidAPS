package app.aaps.pump.equil.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.data.database.EquilBasalValuesRecord
import app.aaps.pump.equil.data.database.EquilHistoryPump
import app.aaps.pump.equil.data.database.EquilHistoryRecord
import app.aaps.pump.equil.data.database.EquilHistoryRecordDao
import app.aaps.pump.equil.data.database.ResolvedResult
import app.aaps.pump.equil.databinding.EquilHistoryRecordActivityBinding
import app.aaps.pump.equil.driver.definition.EquilHistoryEntryGroup
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.manager.Utils
import app.aaps.pump.equil.manager.command.PumpEvent
import com.google.android.material.tabs.TabLayout
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.pump.common.utils.ProfileUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

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
    @Inject lateinit var rxBus: RxBus
    val calendar: Calendar = Calendar.getInstance()
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
        binding.recyclerview.run {
            setHasFixedSize(true)
            layoutManager = llm
            adapter = recyclerViewAdapter
        }
        binding.recyclerviewEquil.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@EquilHistoryRecordActivity)
        }
        disposable += rxBus
            .toObservable(EventEquilDataChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           loadData()
                           loadDataEquil()
                       }, fabricPrivacy::logException)
        typeListFull = getTypeList(EquilHistoryEntryGroup.getTranslatedList(rh))

        val spinnerAdapter: ArrayAdapter<TypeList> = ArrayAdapter<TypeList>(this, app.aaps.core.ui.R.layout.spinner_centered, typeListFull!!)
        binding.equilHistorytype.run {
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
                    filterHistory(EquilHistoryEntryGroup.All)
                }
            }
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                processVisibility(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        loadDataEquil()
    }

    private fun processVisibility(position: Int) {
        binding.recyclerview.visibility = (position == 0).toVisibility()
        binding.recyclerviewEquil.visibility = (position == 1).toVisibility()

    }

    private fun filterHistory(group: EquilHistoryEntryGroup) {
        filteredHistoryList.clear()
        aapsLogger.debug(LTag.PUMPCOMM, "Items on full list: {}", fullHistoryList.size)
        if (group === EquilHistoryEntryGroup.All) {
            aapsLogger.debug(LTag.PUMPCOMM, "alll===")
            filteredHistoryList.addAll(fullHistoryList)
        } else {
            filteredHistoryList.addAll(fullHistoryList.filter { it.type?.let { it1 -> groupForCommandType(it1) } == group })
        }

        recyclerViewAdapter.let {
            it.historyList = filteredHistoryList
            it.notifyDataSetChanged()
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Items on filtered list: {}", filteredHistoryList.size)
    }

    private fun groupForCommandType(type: EquilHistoryRecord.EventType): EquilHistoryEntryGroup {
        return when (type) {
            EquilHistoryRecord.EventType.INITIALIZE_EQUIL        ->
                EquilHistoryEntryGroup.Pair

            EquilHistoryRecord.EventType.INSERT_CANNULA          ->
                EquilHistoryEntryGroup.Pair

            EquilHistoryRecord.EventType.UNPAIR_EQUIL            ->
                EquilHistoryEntryGroup.Pair
            // EquilHistoryRecord.EventType.DEACTIVATE_POD         ->
            //     PumpHistoryEntryGroup.Prime
            //
            // EquilHistoryRecord.EventType.DISCARD_POD            ->
            //     PumpHistoryEntryGroup.Prime

            EquilHistoryRecord.EventType.CANCEL_TEMPORARY_BASAL  ->
                EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.CANCEL_EXTENDED_BOLUS   ->
                EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_EXTENDED_BOLUS      ->
                EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_BASAL_PROFILE       ->
                EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL     ->
                EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.RESUME_DELIVERY         ->
                EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SUSPEND_DELIVERY        ->
                EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_BOLUS               ->
                EquilHistoryEntryGroup.Bolus

            EquilHistoryRecord.EventType.CANCEL_BOLUS            ->
                EquilHistoryEntryGroup.Bolus

            EquilHistoryRecord.EventType.SET_TIME                ->
                EquilHistoryEntryGroup.Configuration

            EquilHistoryRecord.EventType.SET_ALARM_MUTE          ->
                EquilHistoryEntryGroup.Configuration

            EquilHistoryRecord.EventType.SET_ALARM_SHAKE         ->
                EquilHistoryEntryGroup.Configuration

            EquilHistoryRecord.EventType.SET_ALARM_TONE          ->
                EquilHistoryEntryGroup.Configuration

            EquilHistoryRecord.EventType.SET_ALARM_TONE_AND_SHAK ->
                EquilHistoryEntryGroup.Configuration

            // EquilHistoryRecord.EventType.READ_POD_PULSE_LOG     ->
            //     PumpHistoryEntryGroup.Unknown
            else                                                 -> {
                EquilHistoryEntryGroup.All

            }
        }
    }

    private fun getTypeList(list: List<EquilHistoryEntryGroup>): List<TypeList> {
        val typeList = java.util.ArrayList<TypeList>()
        for (pumpHistoryEntryGroup in list) {
            typeList.add(TypeList(pumpHistoryEntryGroup))
        }
        return typeList
    }

    internal class TypeList(val entryGroup: EquilHistoryEntryGroup) {

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
        calendar.add(Calendar.DAY_OF_MONTH, -5)
        val startTime = calendar.timeInMillis

        aapsLogger.debug(LTag.PUMPCOMM, "loadData===" + dateformat2.format(startTime) + "====")
        disposable += equilHistoryRecordDao
            .allSince(startTime, System.currentTimeMillis())
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ historyList ->
                           aapsLogger.debug(LTag.PUMPCOMM, "historyList===" + historyList.size)
                           fullHistoryList.clear()
                           fullHistoryList.addAll(historyList)
                           // }
                       }) {
                aapsLogger.debug(LTag.PUMPCOMM, "historyListerror===" + it)
            }
    }

    fun loadDataEquil() {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -5)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        aapsLogger.debug(LTag.PUMPCOMM, "loadData===" + dateformat2.format(startTime) + "====" + dateformat2.format(endTime))
        disposable += equilHistoryRecordDao
            .allFromByType(startTime, endTime, equilPumpPlugin.serialNumber())
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ historyList ->
                           aapsLogger.debug(LTag.PUMPCOMM, "loadDataEquil===" + historyList.size)
                           binding.recyclerviewEquil.swapAdapter(RecyclerViewAdapterEquil(toModels(historyList), rh), false)
                       }) {
            }
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
            holder.typeView.text = rh.gs(item.type!!.resourceId)

            if (!item.isSuccess()) {
                holder.valueView.text = rh.gs(translatedFailure(item))
                return
            }
            holder.valueView.text = when (item.type) {
                EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL -> {
                    val tbr = item.tempBasalRecord
                    val duration = (tbr?.duration?.div(60 * 1000) ?: 0)

                    tbr.let {
                        rh.gs(R.string.equil_common_history_tbr_value, it?.rate, duration)
                    }
                }

                EquilHistoryRecord.EventType.SET_EXTENDED_BOLUS  -> {
                    val tbr = item.tempBasalRecord
                    val duration = (tbr?.duration?.div(60 * 1000) ?: 0)
                    val rate = (tbr!!.rate * (60 / duration))
                    tbr.let {
                        rh.gs(R.string.equil_common_history_tbr_value, rate, duration)
                    }
                }

                EquilHistoryRecord.EventType.SET_BOLUS           -> {
                    val bolus = item.bolusRecord
                    bolus.let {
                        rh.gs(R.string.equil_common_history_bolus_value, it?.amout)
                    }
                }

                EquilHistoryRecord.EventType.INSERT_CANNULA      -> {
                    rh.gs(R.string.history_mannual_confirm)
                }

                EquilHistoryRecord.EventType.EQUIL_ALARM         -> {
                    item.note
                }

                EquilHistoryRecord.EventType.SET_BASAL_PROFILE   -> {
                    val basal = item.basalValuesRecord as EquilBasalValuesRecord
                    ProfileUtil.getBasalProfilesDisplayable(basal.segments.toTypedArray(), PumpType.EQUIL)
                }

                else                                             ->
                    rh.gs(R.string.equil_success)
            }

            // holder.typeView.setTextColor(textColor)
        }

        private fun translatedFailure(historyEntry: EquilHistoryRecord): Int {
            return when {
                historyEntry.resolvedStatus == ResolvedResult.NOT_FOUNT     ->
                    R.string.equil_command_not_found

                historyEntry.resolvedStatus == ResolvedResult.CONNECT_ERROR ->
                    R.string.equil_command_connect_error

                historyEntry.resolvedStatus == ResolvedResult.FAILURE       ->
                    R.string.equil_command_connect_no_response

                historyEntry.resolvedStatus == ResolvedResult.SUCCESS       ->
                    R.string.equil_success

                historyEntry.resolvedStatus == ResolvedResult.NONE          ->
                    R.string.equil_none

                else                                                        ->
                    R.string.equil_command__unknown
            }
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

        private var selectedGroup: EquilHistoryEntryGroup = EquilHistoryEntryGroup.All
    }

    fun toModels(list: List<EquilHistoryPump>): List<ItemModel> {
        val arrayList = ArrayList<ItemModel>()
        var record: EquilHistoryPump? = null
        var record2: EquilHistoryPump? = null
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val list2 = list.sortedWith(compareBy(EquilHistoryPump::eventTimestamp, EquilHistoryPump::eventIndex))
        val iterator = list2.listIterator()
        var pre: EquilHistoryPump? = null

        while (iterator.hasNext()) {
            val next = iterator.next()

            // Process basal speed
            if (record2 == null || record2.rate != next.rate) {
                val format = dateFormat.format(next.eventTimestamp)
                val valueOf = Utils.decodeSpeedToUH(next.rate).toString()
                if (pre?.type == 10) {
                    // arrayList.add(ItemModel(format, valueOf, ItemModel.TYPE_BASAL, next.eventTimestamp))
                    arrayList.add(ItemModel(format, valueOf, ItemModel.TYPE_BASAL_TEMP, next.eventTimestamp))
                } else {
                    arrayList.add(ItemModel(format, valueOf, ItemModel.TYPE_BASAL, next.eventTimestamp))

                }
                record2 = next
            }
            // Process bolus speed
            if (record != null && next.largeRate != record.largeRate) {
                val time = next.eventTimestamp
                val time2 = record.eventTimestamp

                val format2 = dateFormat.format(time2)
                val format3 = "%.3f".format(
                    (Math.abs(time - time2) / 1000.0)
                    // * _root_ide_package_.app.aaps.pump.equil.manager.Utils.decodeSpeedToUS(record.largeRate)
                )
                val t = (Math.abs(time - time2) / 1000.0)
                aapsLogger.debug(LTag.PUMPCOMM, "time===" + t + "===" + format3)
                arrayList.add(ItemModel(format2, format3, ItemModel.TYPE_BOLUS, time2))
                record = null
            }
            pre = next
            if (next.largeRate > 0) {
                record = next
            }
            // Process event
            val string = PumpEvent.getTips(next.port, next.type, next.level)

            if (!"--".equals(string)) {
                val format4 = dateFormat.format(next.eventTimestamp)
                arrayList.add(ItemModel(format4, string, ItemModel.TYPE_TEXT, next.eventTimestamp))
            }
        }

        // Process remaining bolus speed
        // record?.let {
        //     val decodeSpeedToUH = Utils.decodeSpeedToUH(it.largeRate)
        //     val format5 = format.format(it.eventTimestamp)
        //     arrayList.add(ItemModel(format5, "正在开始 $decodeSpeedToUH U/H", 4, it.eventTimestamp))
        // }

        val reversedList = arrayList.reversed()
        return reversedList
    }

    data class ItemModel(val time: String, val text: String, val type: Int, var eventTime: Long) {
        companion object {

            const val TYPE_BOLUS = 1
            const val TYPE_BASAL = 2
            const val TYPE_TEXT = 3
            const val TYPE_BOLUS_ING = 4
            const val TYPE_BASAL_TEMP = 5

        }
    }

    class RecyclerViewAdapterEquil internal constructor(
        var historyList: List<ItemModel>,
        var rh: ResourceHelper
    ) : RecyclerView.Adapter<RecyclerViewAdapterEquil.EquilHistoryViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): EquilHistoryViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.equil_item_record,  //
                viewGroup, false
            )
            return EquilHistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: EquilHistoryViewHolder, position: Int) {
            val item = historyList[position]
            Log.e(LTag.PUMPCOMM.tag, "onBindViewHolder  ${position}")
            holder.timeView.text = item.time
            holder.typeView.text = item.text
            val type = item.type
            val textColor = when (type) {
                ItemModel.TYPE_BOLUS      -> rh.gc(R.color.equil_bolus)
                ItemModel.TYPE_BASAL      -> rh.gc(R.color.equil_basal)
                ItemModel.TYPE_BASAL_TEMP -> rh.gc(R.color.equil_basal)
                ItemModel.TYPE_BOLUS_ING  -> rh.gc(R.color.equil_bolus_ing)
                else                      -> rh.gc(R.color.equil_noramll)
            }
            val text = when (type) {
                ItemModel.TYPE_BOLUS      -> rh.gs(R.string.equil_record_bolus, item.text)
                ItemModel.TYPE_BASAL      -> rh.gs(R.string.equil_record_basal, item.text)
                ItemModel.TYPE_BASAL_TEMP -> rh.gs(R.string.equil_record_basal_temp, item.text)
                else                      -> item.text
            }
            holder.timeView.text = item.time
            holder.typeView.text = text
            holder.typeView.setTextColor(textColor)
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        class EquilHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var timeView: TextView
            var typeView: TextView
            var descriptionView: TextView

            init {
                timeView = itemView.findViewById(R.id.equil_history_time)
                typeView = itemView.findViewById(R.id.equil_history_source)
                descriptionView = itemView.findViewById(R.id.equil_history_description)
                descriptionView.visibility = View.GONE

            }
        }

    }
}
