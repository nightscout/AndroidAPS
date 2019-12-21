package info.nightscout.androidaps.plugins.pump.danaR.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.DanaRHistoryRecord
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus.toObservable
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRSyncStatus
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.danar_historyactivity.*
import org.slf4j.LoggerFactory
import java.util.*

class DanaRHistoryActivity : NoSplashAppCompatActivity() {
    private val log = LoggerFactory.getLogger(L.PUMP)
    private val disposable = CompositeDisposable()

    private var showingType = RecordTypes.RECORD_TYPE_ALARM
    private var historyList: List<DanaRHistoryRecord> = ArrayList()

    class TypeList internal constructor(var type: Byte, var name: String) {
        override fun toString(): String = name
    }

    override fun onResume() {
        super.onResume()
        disposable.add(toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ danar_history_status.text = it.getStatus() }) { FabricPrivacy.logException(it) }
        )
        disposable.add(toObservable(EventDanaRSyncStatus::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (L.isEnabled(L.PUMP))
                    log.debug("EventDanaRSyncStatus: " + it.message)
                danar_history_status.setText(it.message)
            }) { FabricPrivacy.logException(it) }
        )
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.danar_historyactivity)

        danar_history_recyclerview.setHasFixedSize(true)
        danar_history_recyclerview.setLayoutManager(LinearLayoutManager(this))
        danar_history_recyclerview.setAdapter(RecyclerViewAdapter(historyList))
        danar_history_status.visibility = View.GONE

        val isKorean = DanaRKoreanPlugin.getPlugin().isEnabled(PluginType.PUMP)
        val isRS = DanaRSPlugin.getPlugin().isEnabled(PluginType.PUMP)

        // Types
        val typeList = ArrayList<TypeList>()
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_ALARM, MainApp.gs(R.string.danar_history_alarm)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, MainApp.gs(R.string.danar_history_basalhours)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BOLUS, MainApp.gs(R.string.danar_history_bolus)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_CARBO, MainApp.gs(R.string.danar_history_carbohydrates)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_DAILY, MainApp.gs(R.string.danar_history_dailyinsulin)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_GLUCOSE, MainApp.gs(R.string.danar_history_glucose)))
        if (!isKorean && !isRS) {
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_ERROR, MainApp.gs(R.string.danar_history_errors)))
        }
        if (isRS) typeList.add(TypeList(RecordTypes.RECORD_TYPE_PRIME, MainApp.gs(R.string.danar_history_prime)))
        if (!isKorean) {
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_REFILL, MainApp.gs(R.string.danar_history_refill)))
            typeList.add(TypeList(RecordTypes.RECORD_TYPE_SUSPEND, MainApp.gs(R.string.danar_history_syspend)))
        }
        danar_history_spinner.adapter = ArrayAdapter(this, R.layout.spinner_centered, typeList)

        danar_history_reload.setOnClickListener {
            val selected = danar_history_spinner.selectedItem as TypeList
            runOnUiThread {
                danar_history_reload.setVisibility(View.GONE)
                danar_history_status.setVisibility(View.VISIBLE)
            }
            clearCardView()
            ConfigBuilderPlugin.getPlugin().commandQueue.loadHistory(selected.type, object : Callback() {
                override fun run() {
                    loadDataFromDB(selected.type)
                    runOnUiThread {
                        danar_history_reload.setVisibility(View.VISIBLE)
                        danar_history_status.setVisibility(View.GONE)
                    }
                }
            })
        }
        danar_history_spinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val selected = danar_history_spinner.selectedItem as TypeList
                loadDataFromDB(selected.type)
                showingType = selected.type
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearCardView()
            }
        })
    }

    inner class RecyclerViewAdapter internal constructor(var historyList: List<DanaRHistoryRecord>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder =
            HistoryViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.danar_history_item, viewGroup, false))

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.time.text = DateUtil.dateAndTimeString(record.recordDate)
            holder.value.text = DecimalFormatter.to2Decimal(record.recordValue)
            holder.stringvalue.text = record.stringRecordValue
            holder.bolustype.text = record.bolusType
            holder.duration.text = DecimalFormatter.to0Decimal(record.recordDuration.toDouble())
            holder.alarm.text = record.recordAlarm
            when (showingType) {
                RecordTypes.RECORD_TYPE_ALARM                                                                                                                                                              -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringvalue.visibility = View.GONE
                    holder.bolustype.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailybasal.visibility = View.GONE
                    holder.dailybolus.visibility = View.GONE
                    holder.dailytotal.visibility = View.GONE
                    holder.alarm.visibility = View.VISIBLE
                }

                RecordTypes.RECORD_TYPE_BOLUS                                                                                                                                                              -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringvalue.visibility = View.GONE
                    holder.bolustype.visibility = View.VISIBLE
                    holder.duration.visibility = View.VISIBLE
                    holder.dailybasal.visibility = View.GONE
                    holder.dailybolus.visibility = View.GONE
                    holder.dailytotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_DAILY                                                                                                                                                              -> {
                    holder.dailybasal.text = MainApp.gs(R.string.formatinsulinunits, record.recordDailyBasal)
                    holder.dailybolus.text = MainApp.gs(R.string.formatinsulinunits, record.recordDailyBolus)
                    holder.dailytotal.text = MainApp.gs(R.string.formatinsulinunits, record.recordDailyBolus + record.recordDailyBasal)
                    holder.time.text = DateUtil.dateString(record.recordDate)
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.GONE
                    holder.stringvalue.visibility = View.GONE
                    holder.bolustype.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailybasal.visibility = View.VISIBLE
                    holder.dailybolus.visibility = View.VISIBLE
                    holder.dailytotal.visibility = View.VISIBLE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_GLUCOSE                                                                                                                                                            -> {
                    holder.value.text = Profile.toUnitsString(record.recordValue, record.recordValue * Constants.MGDL_TO_MMOLL, ProfileFunctions.getSystemUnits())
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringvalue.visibility = View.GONE
                    holder.bolustype.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailybasal.visibility = View.GONE
                    holder.dailybolus.visibility = View.GONE
                    holder.dailytotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_CARBO, RecordTypes.RECORD_TYPE_BASALHOUR, RecordTypes.RECORD_TYPE_ERROR, RecordTypes.RECORD_TYPE_PRIME, RecordTypes.RECORD_TYPE_REFILL, RecordTypes.RECORD_TYPE_TB -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringvalue.visibility = View.GONE
                    holder.bolustype.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailybasal.visibility = View.GONE
                    holder.dailybolus.visibility = View.GONE
                    holder.dailytotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_SUSPEND                                                                                                                                                            -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.GONE
                    holder.stringvalue.visibility = View.VISIBLE
                    holder.bolustype.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailybasal.visibility = View.GONE
                    holder.dailybolus.visibility = View.GONE
                    holder.dailytotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var cv: CardView = itemView.findViewById(R.id.danar_history_cardview)
            var time: TextView = itemView.findViewById(R.id.danar_history_time)
            var value: TextView
            var bolustype: TextView
            var stringvalue: TextView
            var duration: TextView
            var dailybasal: TextView
            var dailybolus: TextView
            var dailytotal: TextView
            var alarm: TextView

            init {
                value = itemView.findViewById(R.id.danar_history_value)
                bolustype = itemView.findViewById(R.id.danar_history_bolustype)
                stringvalue = itemView.findViewById(R.id.danar_history_stringvalue)
                duration = itemView.findViewById(R.id.danar_history_duration)
                dailybasal = itemView.findViewById(R.id.danar_history_dailybasal)
                dailybolus = itemView.findViewById(R.id.danar_history_dailybolus)
                dailytotal = itemView.findViewById(R.id.danar_history_dailytotal)
                alarm = itemView.findViewById(R.id.danar_history_alarm)
            }
        }

    }

    private fun loadDataFromDB(type: Byte) {
        historyList = MainApp.getDbHelper().getDanaRHistoryRecordsByType(type)
        runOnUiThread { danar_history_recyclerview?.swapAdapter(RecyclerViewAdapter(historyList), false) }
    }

    private fun clearCardView() {
        historyList = ArrayList()
        runOnUiThread { danar_history_recyclerview?.swapAdapter(RecyclerViewAdapter(historyList), false) }
    }
}