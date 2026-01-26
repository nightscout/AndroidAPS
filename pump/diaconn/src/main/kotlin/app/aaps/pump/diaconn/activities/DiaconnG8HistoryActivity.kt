package app.aaps.pump.diaconn.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.common.RecordTypes
import app.aaps.pump.diaconn.database.DiaconnHistoryRecord
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import app.aaps.pump.diaconn.databinding.DiaconnG8HistoryActivityBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class DiaconnG8HistoryActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var diaconnHistoryRecordDao: DiaconnHistoryRecordDao
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private val disposable = CompositeDisposable()

    private var showingType = RecordTypes.RECORD_TYPE_ALARM
    private var historyList: List<DiaconnHistoryRecord> = ArrayList()

    class TypeList internal constructor(var type: Byte, var name: String) {

        override fun toString(): String = name
    }

    private lateinit var binding: DiaconnG8HistoryActivityBinding

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.status.text = it.getStatus(this@DiaconnG8HistoryActivity) }) { fabricPrivacy.logException(it) }
        swapAdapter(showingType)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.recyclerview.adapter = null
        binding.reload.setOnClickListener(null)
        binding.typeList.onItemClickListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DiaconnG8HistoryActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(app.aaps.core.ui.R.string.pump_history)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(historyList)
        binding.status.visibility = View.GONE

        // Types
        val typeList = ArrayList<TypeList>()
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_ALARM, rh.gs(R.string.diaconn_g8_history_alarm)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, rh.gs(R.string.diaconn_g8_history_basalhours)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BOLUS, rh.gs(R.string.diaconn_g8_history_bolus)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_TB, rh.gs(R.string.diaconn_g8_history_tempbasal)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_DAILY, rh.gs(R.string.diaconn_g8_history_dailyinsulin)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_REFILL, rh.gs(R.string.diaconn_g8_history_refill)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_SUSPEND, rh.gs(R.string.diaconn_g8_history_suspend)))
        binding.typeList.setAdapter(ArrayAdapter(this, app.aaps.core.ui.R.layout.spinner_centered, typeList))

        binding.reload.setOnClickListener {
            val selected = typeList.firstOrNull { it.name == binding.typeList.text.toString() } ?: return@setOnClickListener
            runOnUiThread {
                binding.reload.visibility = View.GONE
                binding.status.visibility = View.VISIBLE
            }
            clearCardView()
            commandQueue.loadHistory(selected.type, object : Callback() {
                override fun run() {
                    swapAdapter(selected.type)
                    runOnUiThread {
                        binding.reload.visibility = View.VISIBLE
                        binding.status.visibility = View.GONE
                    }
                }
            })
        }
        binding.typeList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = typeList[position]
            showingType = selected.type
            swapAdapter(selected.type)
        }
    }

    inner class RecyclerViewAdapter internal constructor(private var historyList: List<DiaconnHistoryRecord>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder =
            HistoryViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.diaconn_g8_history_item, viewGroup, false))

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.time.text = dateUtil.dateAndTimeString(record.timestamp)
            holder.value.text = decimalFormatter.to2Decimal(record.value)
            holder.stringValue.text = record.stringValue
            holder.bolusType.text = record.bolusType
            holder.duration.text = decimalFormatter.to0Decimal(record.duration.toDouble())
            holder.alarm.text = record.alarm
            when (showingType) {
                RecordTypes.RECORD_TYPE_ALARM   -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.VISIBLE
                }

                RecordTypes.RECORD_TYPE_BOLUS   -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.VISIBLE
                    holder.duration.visibility = View.VISIBLE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_DAILY   -> {
                    holder.dailyBasal.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.dailyBasal)
                    holder.dailyBolus.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.dailyBolus)
                    holder.dailyTotal.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.dailyBolus + record.dailyBasal)
                    holder.time.text = dateUtil.dateString(record.timestamp)
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

                RecordTypes.RECORD_TYPE_BASALHOUR,
                RecordTypes.RECORD_TYPE_REFILL  -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_TB      -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.VISIBLE
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

            var time: TextView = itemView.findViewById(R.id.diaconn_g8_history_time)
            var value: TextView = itemView.findViewById(R.id.diaconn_g8_history_value)
            var bolusType: TextView = itemView.findViewById(R.id.diaconn_g8_history_bolustype)
            var stringValue: TextView = itemView.findViewById(R.id.diaconn_g8_history_stringvalue)
            var duration: TextView = itemView.findViewById(R.id.diaconn_g8_history_duration)
            var dailyBasal: TextView = itemView.findViewById(R.id.diaconn_g8_history_dailybasal)
            var dailyBolus: TextView = itemView.findViewById(R.id.diaconn_g8_history_dailybolus)
            var dailyTotal: TextView = itemView.findViewById(R.id.diaconn_g8_history_dailytotal)
            var alarm: TextView = itemView.findViewById(R.id.diaconn_g8_history_alarm)
        }
    }

    private fun swapAdapter(type: Byte) {
        disposable += diaconnHistoryRecordDao
            .allFromByType(dateUtil.now() - T.months(1).msecs(), type)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { historyList -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(historyList), false) }
    }

    private fun clearCardView() = binding.recyclerview.swapAdapter(RecyclerViewAdapter(ArrayList()), false)
}