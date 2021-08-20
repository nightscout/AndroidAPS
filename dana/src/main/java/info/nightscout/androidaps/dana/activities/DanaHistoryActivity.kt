package info.nightscout.androidaps.dana.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.dana.R
import info.nightscout.androidaps.dana.comm.RecordTypes
import info.nightscout.androidaps.dana.databinding.DanarHistoryActivityBinding
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.DanaRHistoryRecord
import info.nightscout.androidaps.events.EventDanaRSyncStatus
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

class DanaHistoryActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var databaseHelper: DatabaseHelperInterface
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    private var showingType = RecordTypes.RECORD_TYPE_ALARM
    private var historyList: List<DanaRHistoryRecord> = ArrayList()

    class TypeList internal constructor(var type: Byte, var name: String) {

        override fun toString(): String = name
    }

    private lateinit var binding: DanarHistoryActivityBinding

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ binding.status.text = it.getStatus(resourceHelper) }) { fabricPrivacy.logException(it) }
        disposable += rxBus
            .toObservable(EventDanaRSyncStatus::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                aapsLogger.debug(LTag.PUMP, "EventDanaRSyncStatus: " + it.message)
                binding.status.text = it.message
            }) { fabricPrivacy.logException(it) }
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarHistoryActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(historyList)
        binding.status.visibility = View.GONE

        val pump = activePlugin.activePump
        val isKorean = pump.pumpDescription.pumpType == PumpType.DanaRKorean
        val isRS = pump.pumpDescription.pumpType == PumpType.DanaRS

        // Types
        val typeList = ArrayList<TypeList>()
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_ALARM, resourceHelper.gs(R.string.danar_history_alarm)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, resourceHelper.gs(R.string.danar_history_basalhours)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BOLUS, resourceHelper.gs(R.string.danar_history_bolus)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_CARBO, resourceHelper.gs(R.string.danar_history_carbohydrates)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_DAILY, resourceHelper.gs(R.string.danar_history_dailyinsulin)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_GLUCOSE, resourceHelper.gs(R.string.danar_history_glucose)))
        if (!isKorean && !isRS) {
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_ERROR, resourceHelper.gs(R.string.danar_history_errors)))
        }
        if (isRS) typeList.add(TypeList(RecordTypes.RECORD_TYPE_PRIME, resourceHelper.gs(R.string.danar_history_prime)))
        if (!isKorean) {
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_REFILL, resourceHelper.gs(R.string.danar_history_refill)))
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_SUSPEND, resourceHelper.gs(R.string.danar_history_syspend)))
        }
        binding.spinner.adapter = ArrayAdapter(this, R.layout.spinner_centered, typeList)

        binding.reload.setOnClickListener {
            val selected = binding.spinner.selectedItem as TypeList?
                ?: return@setOnClickListener
            runOnUiThread {
                binding.reload.visibility = View.GONE
                binding.status.visibility = View.VISIBLE
            }
            clearCardView()
            commandQueue.loadHistory(selected.type, object : Callback() {
                override fun run() {
                    loadDataFromDB(selected.type)
                    runOnUiThread {
                        binding.reload.visibility = View.VISIBLE
                        binding.status.visibility = View.GONE
                    }
                }
            })
        }
        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = typeList[position]
                loadDataFromDB(selected.type)
                showingType = selected.type
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearCardView()
            }
        }
    }

    inner class RecyclerViewAdapter internal constructor(private var historyList: List<DanaRHistoryRecord>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder =
            HistoryViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.danar_history_item, viewGroup, false))

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.time.text = dateUtil.dateAndTimeString(record.recordDate)
            holder.value.text = DecimalFormatter.to2Decimal(record.recordValue)
            holder.stringValue.text = record.stringRecordValue
            holder.bolusType.text = record.bolusType
            holder.duration.text = DecimalFormatter.to0Decimal(record.recordDuration.toDouble())
            holder.alarm.text = record.recordAlarm
            when (showingType) {
                RecordTypes.RECORD_TYPE_ALARM -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.GONE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.VISIBLE
                }

                RecordTypes.RECORD_TYPE_BOLUS -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.GONE
                    holder.bolusType.visibility = View.VISIBLE
                    holder.duration.visibility = View.VISIBLE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_DAILY -> {
                    holder.dailyBasal.text = resourceHelper.gs(R.string.formatinsulinunits, record.recordDailyBasal)
                    holder.dailyBolus.text = resourceHelper.gs(R.string.formatinsulinunits, record.recordDailyBolus)
                    holder.dailyTotal.text = resourceHelper.gs(R.string.formatinsulinunits, record.recordDailyBolus + record.recordDailyBasal)
                    holder.time.text = DateUtil.dateString(record.recordDate)
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.GONE
                    holder.stringValue.visibility = View.GONE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.VISIBLE
                    holder.dailyBolus.visibility = View.VISIBLE
                    holder.dailyTotal.visibility = View.VISIBLE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_GLUCOSE -> {
                    holder.value.text = Profile.toUnitsString(record.recordValue, record.recordValue * Constants.MGDL_TO_MMOLL, profileFunction.getUnits())
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.GONE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_CARBO, RecordTypes.RECORD_TYPE_BASALHOUR, RecordTypes.RECORD_TYPE_ERROR, RecordTypes.RECORD_TYPE_PRIME, RecordTypes.RECORD_TYPE_REFILL, RecordTypes.RECORD_TYPE_TB -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.GONE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_SUSPEND -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.GONE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var time: TextView = itemView.findViewById(R.id.danar_history_time)
            var value: TextView = itemView.findViewById(R.id.danar_history_value)
            var bolusType: TextView = itemView.findViewById(R.id.danar_history_bolustype)
            var stringValue: TextView = itemView.findViewById(R.id.danar_history_stringvalue)
            var duration: TextView = itemView.findViewById(R.id.danar_history_duration)
            var dailyBasal: TextView = itemView.findViewById(R.id.danar_history_dailybasal)
            var dailyBolus: TextView = itemView.findViewById(R.id.danar_history_dailybolus)
            var dailyTotal: TextView = itemView.findViewById(R.id.danar_history_dailytotal)
            var alarm: TextView = itemView.findViewById(R.id.danar_history_alarm)
        }
    }

    private fun loadDataFromDB(type: Byte) {
        historyList = databaseHelper.getDanaRHistoryRecordsByType(type)
        runOnUiThread { binding.recyclerview.swapAdapter(RecyclerViewAdapter(historyList), false) }
    }

    private fun clearCardView() {
        historyList = ArrayList()
        runOnUiThread { binding.recyclerview.swapAdapter(RecyclerViewAdapter(historyList), false) }
    }
}