package app.aaps.pump.aaruy.ui.history

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.common.util.AaruyConst
import app.aaps.pump.aaruy.common.util.AaruySetting
import app.aaps.pump.aaruy.common.util.AaruyNumberUtils
import app.aaps.pump.aaruy.common.util.AaruyTimeUtil

class HistoryLogRecyclerviewAdapter(
    private var mContext: Context?,
    private var historyRecordList: List<HistoryUnitInfo>?
) : RecyclerView.Adapter<HistoryLogRecyclerviewAdapter.MyViewHolder>() {


    @SuppressLint("InflateParams")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView =
            LayoutInflater.from(mContext).inflate(R.layout.history_log_rc_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun getItemCount(): Int {
        //LogUtils.e("HistoryLogRecyclerviewAdapter", "size=${historyRecordList!!.size}")
        return historyRecordList!!.size
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {

        val historyUnitInfo = historyRecordList!![position]
        val time = historyUnitInfo.time
        holder.history_time_tv.text = AaruyTimeUtil.getTime(time)

        val data = AaruySetting.factInjectValue(historyUnitInfo.data.toDouble())
        val injectTime = historyUnitInfo.injectTime.toInt()
        val delayBolus = AaruySetting.factInjectValue(historyUnitInfo.delayBolus.toDouble())
        when (historyUnitInfo.type) {
            0 -> { //basal
                holder.history_prolong_type_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                holder.history_prolong_time_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                holder.history_type_tv.visibility = View.GONE
                holder.history_bolus_prolong_ll.visibility = View.VISIBLE

                holder.history_prolong_type_tv.text = mContext!!.getText(R.string.basal_rate_title)
                holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                holder.history_value_tv.text =
                    AaruyNumberUtils.getDotThreeString(data) + mContext!!.getString(R.string.unit_u_hr)

                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
            }
            1 -> { //temp basal
                holder.history_prolong_type_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                holder.history_prolong_time_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                holder.history_type_tv.visibility = View.GONE
                holder.history_bolus_prolong_ll.visibility = View.VISIBLE

                holder.history_prolong_type_tv.text = mContext!!.getText(R.string.temp_basal_title)
                holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                holder.history_value_tv.text =
                    AaruyNumberUtils.getDotThreeString(data) + mContext!!.getString(R.string.unit_u_hr)


                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
            }
            2 -> { //bolus

                if (delayBolus == 0.0) {
                    holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                    holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                    holder.history_type_tv.visibility = View.VISIBLE
                    holder.history_type_tv.text = mContext!!.getText(R.string.bolus_title)
                    holder.history_value_tv.text =
                        AaruyNumberUtils.getDotThreeString(data) + mContext!!.getString(R.string.unit_u)
                    holder.history_bolus_prolong_ll.visibility = View.GONE

                    holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                } else {
                    holder.history_prolong_type_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                    holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                    holder.history_prolong_time_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                    holder.history_type_tv.visibility = View.GONE
                    holder.history_bolus_prolong_ll.visibility = View.VISIBLE
                    holder.history_prolong_type_tv.text =
                        mContext!!.getText(R.string.history_insulin_name4)
                    holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                    holder.history_value_tv.text =
                        AaruyNumberUtils.getDotThreeString(delayBolus) + mContext!!.getString(R.string.unit_u)

                    holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.color_2cfc9b))
                }
            }
            3 -> {//报警
                holder.history_bolus_prolong_ll.visibility = View.GONE

                holder.history_type_tv.visibility = View.VISIBLE
                holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.color_ff5656))
                holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.color_ff5656))
                holder.history_type_tv.text = mContext!!.getText(R.string.history_alarm)

                holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                holder.history_value_tv.text = ""

                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.color_ff5656))
                when (historyUnitInfo.data) {
                    AaruyConst.errCodeType.ERROR_LOW_BATTERY.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getText(R.string.low_battery)
                    }
                    AaruyConst.errCodeType.ERROR_MIN_BATTERY.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getText(R.string.min_battery)
                    }
                    AaruyConst.errCodeType.ERROR_MIN_REMAINAMOUNT.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getText(R.string.zero_doze)
                    }
                    AaruyConst.errCodeType.ERROR_LOW_REMAINAMOUNT.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getText(R.string.min_remainAmount)
                    }

                    AaruyConst.errCodeType.ERROR_MOTOR_FAILED.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getText(R.string.pump_fault)
                    }
                    AaruyConst.errCodeType.ERROR_FLAG_AUTO_POWEROFF.toLong() -> {
                        holder.history_value_tv.text =
                            mContext!!.getText(R.string.setting_insulin_auto_off)
                    }

                    AaruyConst.errCodeType.ERROR_MOTOR_BLOCK.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getText(R.string.motor_block)
                    }
                    AaruyConst.errCodeType.ERROR_FLAG_MOTOR_MCU_BLOCK.toLong() -> {
                        holder.history_value_tv.text =
                            mContext!!.getString(R.string.motor_block_two)
                    }

                    AaruyConst.errCodeType.ERROR_FLAG_MOTOR_MCU_ILLEGAL.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getString(R.string.pump_fault1)
                    }

                    AaruyConst.errCodeType.ERROR_FLAG_MOTOR_MCU_PLUSES_EXTRA.toLong() -> {
                        holder.history_value_tv.text = mContext!!.getString(R.string.pump_fault2)
                    }

                    AaruyConst.errCodeType.ERROR_FLAG_MOTOR_DESTRUCTIVE_ANOMALY.toLong() -> {
                        holder.history_value_tv.text =
                            mContext!!.getString(R.string.error_motor_destructive_anomaly)
                    }

                    AaruyConst.errCodeType.ERROR_FLAG_MOTOR_FAILED_PLUS.toLong() -> {
                        holder.history_value_tv.text =
                            mContext!!.getString(R.string.error_motor_failed_plus_dialog)
                    }
                    else ->{
                        holder.history_value_tv.text =
                            mContext!!.getString(R.string.unknow_error)
                    }
                }
            }

            4 -> {//推杆回退
                holder.history_bolus_prolong_ll.visibility = View.GONE

                holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.white))
                holder.history_prolong_time_tv.setTextColor(mContext!!.getColor(R.color.white))
                holder.history_type_tv.visibility = View.VISIBLE
                holder.history_type_tv.text =
                    mContext!!.getText(R.string.notification_text_rewinding)

                holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                holder.history_value_tv.text = ""

                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.white))
            }

            5 -> {//推杆定位
                val locateData = data.times(10)

                if (locateData > 0){
                    holder.history_prolong_type_tv.setTextColor(mContext!!.getColor(R.color.white))
                    holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.white))
                    holder.history_prolong_time_tv.setTextColor(mContext!!.getColor(R.color.white))
                    holder.history_type_tv.visibility = View.GONE
                    holder.history_bolus_prolong_ll.visibility = View.VISIBLE

                    holder.history_prolong_type_tv.text =
                        mContext!!.getText(R.string.actions_reservoir_prime)
                    holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                    holder.history_value_tv.text =
                        AaruyNumberUtils.getDotThreeString(locateData) + mContext!!.getString(R.string.unit_u)

                    holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.white))
                }else{
                    holder.history_bolus_prolong_ll.visibility = View.GONE

                    holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.white))
                    holder.history_prolong_time_tv.setTextColor(mContext!!.getColor(R.color.white))
                    holder.history_type_tv.visibility = View.VISIBLE
                    holder.history_type_tv.text =
                        mContext!!.getText(R.string.actions_reservoir_prime)
                    holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                    holder.history_value_tv.text = ""

                    holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.white))
                }


            }

            7 -> {
                holder.history_bolus_prolong_ll.visibility = View.GONE

                holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.white))
                holder.history_prolong_time_tv.setTextColor(mContext!!.getColor(R.color.white))
                holder.history_type_tv.visibility = View.VISIBLE
                holder.history_type_tv.text = mContext!!.getText(R.string.suspend)
                holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                holder.history_value_tv.text = ""

                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.white))
            }

            108 -> {
                holder.history_bolus_prolong_ll.visibility = View.GONE

                holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.color_fcdb32))
                holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.color_fcdb32))
                holder.history_type_tv.visibility = View.VISIBLE
                holder.history_type_tv.text = mContext!!.getText(R.string.history_carbs_name)
                holder.history_value_tv.text =
                    historyUnitInfo.delayBolus.toString() + mContext!!.getString(R.string.history_carbs_unit)
                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.color_fcdb32))
            }
            109 -> {
                holder.history_bolus_prolong_ll.visibility = View.GONE

                holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.color_29f2ff))
                holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.color_29f2ff))
                holder.history_type_tv.visibility = View.VISIBLE
                holder.history_type_tv.text = mContext!!.getText(R.string.history_bg)
                val glucoseUnit = 0
                if (glucoseUnit == 1) {
                    holder.history_value_tv.text =
                        AaruyNumberUtils.doubleToInt(historyUnitInfo.bgValue.times(AaruySetting.glucoseUnitTimes))
                            .toString() + "mg/dL"
                } else {
                    holder.history_value_tv.text = AaruyNumberUtils.getDotOneString(
                        historyUnitInfo.bgValue
                    ) + mContext!!.getString(R.string.unit_l)
                }

                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.color_29f2ff))
            }
            110 -> {
                holder.history_bolus_prolong_ll.visibility = View.GONE

                holder.history_type_tv.visibility = View.VISIBLE
                holder.history_type_tv.setTextColor(mContext!!.getColor(R.color.white))
                holder.history_value_tv.setTextColor(mContext!!.getColor(R.color.white))
                holder.history_type_tv.text = mContext!!.getText(R.string.change_pump)

                holder.history_prolong_time_tv.text = AaruyTimeUtil.get_H_m_String(injectTime)
                holder.history_value_tv.text = ""

                holder.history_time_tv.setTextColor(mContext!!.getColor(R.color.white))

                holder.history_value_tv.text = historyUnitInfo.blueName
            }
            else -> {
                holder.history_ll_item.visibility = View.GONE
            }
        }

    }

    fun setData(list: MutableList<HistoryUnitInfo>) {
        historyRecordList = list
        notifyDataSetChanged()
    }

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val history_ll_item: View = itemView.findViewById(R.id.ll_history_item)
        val history_time_tv: TextView = itemView.findViewById(R.id.history_time_tv)
        val history_type_tv: TextView = itemView.findViewById(R.id.history_type_tv)
        val history_value_tv: TextView = itemView.findViewById(R.id.history_value_tv)
        val history_divider_line: View = itemView.findViewById(R.id.history_divider_line)

        val history_bolus_prolong_ll: LinearLayout =
            itemView.findViewById(R.id.history_bolus_prolong_ll)
        val history_prolong_type_tv: TextView = itemView.findViewById(R.id.history_prolong_type_tv)
        val history_prolong_time_tv: TextView = itemView.findViewById(R.id.history_prolong_time_tv)

    }
}