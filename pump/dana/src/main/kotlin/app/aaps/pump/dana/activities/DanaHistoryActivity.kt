package app.aaps.pump.dana.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
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
import app.aaps.pump.dana.R
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecord
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.dana.databinding.DanarHistoryActivityBinding
import app.aaps.pump.dana.databinding.DanarHistoryItemBinding
import app.aaps.pump.dana.events.EventDanaRSyncStatus
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class DanaHistoryActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private val disposable = CompositeDisposable()

    private var showingType = RecordTypes.RECORD_TYPE_ALARM

    class TypeList internal constructor(var type: Byte, var name: String) {

        override fun toString(): String = name
    }

    private lateinit var binding: DanarHistoryActivityBinding

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.status.text = it.getStatus(this@DanaHistoryActivity) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDanaRSyncStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           aapsLogger.debug(LTag.PUMP, "EventDanaRSyncStatus: " + it.message)
                           binding.status.text = it.message
                       }, fabricPrivacy::logException)
        swapAdapter(showingType)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarHistoryActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(app.aaps.core.ui.R.string.pump_history)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.status.visibility = View.GONE

        val pump = activePlugin.activePump
        val isKorean = pump.pumpDescription.pumpType == PumpType.DANA_R_KOREAN
        val isRS = pump.pumpDescription.pumpType == PumpType.DANA_RS

        // Types
        val typeList = ArrayList<TypeList>()
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_ALARM, rh.gs(R.string.danar_history_alarm)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, rh.gs(R.string.danar_history_basalhours)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BOLUS, rh.gs(R.string.danar_history_bolus)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_CARBO, rh.gs(R.string.danar_history_carbohydrates)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_DAILY, rh.gs(R.string.danar_history_dailyinsulin)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_GLUCOSE, rh.gs(R.string.danar_history_glucose)))
        if (!isKorean && !isRS) {
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_ERROR, rh.gs(R.string.danar_history_errors)))
        }
        if (isRS) typeList.add(TypeList(RecordTypes.RECORD_TYPE_PRIME, rh.gs(R.string.danar_history_prime)))
        if (!isKorean) {
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_REFILL, rh.gs(R.string.danar_history_refill)))
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_SUSPEND, rh.gs(R.string.danar_history_syspend)))
        }
        binding.typeList.setAdapter(ArrayAdapter(this, app.aaps.core.ui.R.layout.spinner_centered, typeList))

        binding.reload.setOnClickListener {
            val selected = typeList.firstOrNull { it.name == binding.typeList.text.toString() } ?: return@setOnClickListener
            binding.reload.visibility = View.GONE
            binding.status.visibility = View.VISIBLE
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

    inner class RecyclerViewAdapter internal constructor(private var historyList: List<DanaHistoryRecord>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder =
            HistoryViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.danar_history_item, viewGroup, false))

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.binding.time.text = dateUtil.dateAndTimeString(record.timestamp)
            holder.binding.value.text = decimalFormatter.to2Decimal(record.value)
            holder.binding.stringValue.text = record.stringValue
            holder.binding.bolusType.text = record.bolusType
            holder.binding.duration.text = record.duration.toString()
            holder.binding.alarm.text = record.alarm
            when (showingType) {
                RecordTypes.RECORD_TYPE_ALARM                                                                                                                                                              -> {
                    holder.binding.time.visibility = View.VISIBLE
                    holder.binding.value.visibility = View.VISIBLE
                    holder.binding.stringValue.visibility = View.GONE
                    holder.binding.bolusType.visibility = View.GONE
                    holder.binding.duration.visibility = View.GONE
                    holder.binding.dailyBasal.visibility = View.GONE
                    holder.binding.dailyBolus.visibility = View.GONE
                    holder.binding.dailyTotal.visibility = View.GONE
                    holder.binding.alarm.visibility = View.VISIBLE
                }

                RecordTypes.RECORD_TYPE_BOLUS                                                                                                                                                              -> {
                    holder.binding.time.visibility = View.VISIBLE
                    holder.binding.value.visibility = View.VISIBLE
                    holder.binding.stringValue.visibility = View.GONE
                    holder.binding.bolusType.visibility = View.VISIBLE
                    holder.binding.duration.visibility = View.VISIBLE
                    holder.binding.dailyBasal.visibility = View.GONE
                    holder.binding.dailyBolus.visibility = View.GONE
                    holder.binding.dailyTotal.visibility = View.GONE
                    holder.binding.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_DAILY                                                                                                                                                              -> {
                    holder.binding.dailyBasal.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.dailyBasal)
                    holder.binding.dailyBolus.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.dailyBolus)
                    holder.binding.dailyTotal.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, record.dailyBolus + record.dailyBasal)
                    holder.binding.time.text = dateUtil.dateString(record.timestamp)
                    holder.binding.time.visibility = View.VISIBLE
                    holder.binding.value.visibility = View.GONE
                    holder.binding.stringValue.visibility = View.GONE
                    holder.binding.bolusType.visibility = View.GONE
                    holder.binding.duration.visibility = View.GONE
                    holder.binding.dailyBasal.visibility = View.VISIBLE
                    holder.binding.dailyBolus.visibility = View.VISIBLE
                    holder.binding.dailyTotal.visibility = View.VISIBLE
                    holder.binding.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_GLUCOSE                                                                                                                                                            -> {
                    holder.binding.value.text = profileUtil.fromMgdlToStringInUnits(record.value)
                    holder.binding.time.visibility = View.VISIBLE
                    holder.binding.value.visibility = View.VISIBLE
                    holder.binding.stringValue.visibility = View.GONE
                    holder.binding.bolusType.visibility = View.GONE
                    holder.binding.duration.visibility = View.GONE
                    holder.binding.dailyBasal.visibility = View.GONE
                    holder.binding.dailyBolus.visibility = View.GONE
                    holder.binding.dailyTotal.visibility = View.GONE
                    holder.binding.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_CARBO, RecordTypes.RECORD_TYPE_BASALHOUR, RecordTypes.RECORD_TYPE_ERROR, RecordTypes.RECORD_TYPE_PRIME, RecordTypes.RECORD_TYPE_REFILL, RecordTypes.RECORD_TYPE_TB -> {
                    holder.binding.time.visibility = View.VISIBLE
                    holder.binding.value.visibility = View.VISIBLE
                    holder.binding.stringValue.visibility = View.GONE
                    holder.binding.bolusType.visibility = View.GONE
                    holder.binding.duration.visibility = View.GONE
                    holder.binding.dailyBasal.visibility = View.GONE
                    holder.binding.dailyBolus.visibility = View.GONE
                    holder.binding.dailyTotal.visibility = View.GONE
                    holder.binding.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_SUSPEND                                                                                                                                                            -> {
                    holder.binding.time.visibility = View.VISIBLE
                    holder.binding.value.visibility = View.GONE
                    holder.binding.stringValue.visibility = View.VISIBLE
                    holder.binding.bolusType.visibility = View.GONE
                    holder.binding.duration.visibility = View.GONE
                    holder.binding.dailyBasal.visibility = View.GONE
                    holder.binding.dailyBolus.visibility = View.GONE
                    holder.binding.dailyTotal.visibility = View.GONE
                    holder.binding.alarm.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = DanarHistoryItemBinding.bind(itemView)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.reload.setOnClickListener(null)
        binding.typeList.setAdapter(null)
        binding.typeList.onItemClickListener = null
        binding.recyclerview.adapter = null
    }

    private fun swapAdapter(type: Byte) {
        disposable += danaHistoryRecordDao
            .allFromByType(dateUtil.now() - T.months(1).msecs(), type)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { historyList -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(historyList), false) }
    }

    private fun clearCardView() = binding.recyclerview.swapAdapter(RecyclerViewAdapter(ArrayList()), false)
}