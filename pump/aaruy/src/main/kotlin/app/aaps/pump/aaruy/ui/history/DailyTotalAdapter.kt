package app.aaps.pump.aaruy.ui.history

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.common.util.AaruyNumberUtils
import app.aaps.pump.aaruy.common.util.AaruyTimeUtil

class DailyTotalAdapter(
    private var mContext: Context,
    private var dailyItems: MutableList<DailyItem>
) : RecyclerView.Adapter<DailyTotalAdapter.MyViewHolder>() {

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tv_daily_date: TextView = itemView.findViewById(R.id.tv_daily_date)
        val tv_daily_total: TextView = itemView.findViewById(R.id.tv_daily_total)
        val tv_pump_name:TextView = itemView.findViewById(R.id.tv_pump_name)
        val ll_date_value:LinearLayout = itemView.findViewById(R.id.ll_date_value)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(mContext).inflate(R.layout.daily_total_item_view, parent,false)
        return MyViewHolder(itemView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val dailyItem = dailyItems[position]
        if (dailyItem.isReplace){
            holder.tv_pump_name.visibility = View.VISIBLE
            holder.ll_date_value.visibility = View.GONE
            holder.tv_pump_name.text = dailyItem.pumpName
        }else{
            holder.tv_pump_name.visibility = View.GONE
            holder.ll_date_value.visibility = View.VISIBLE

            val detailTime = AaruyTimeUtil.getWholeDetail(dailyItem.time)
            holder.tv_daily_date.text = "${detailTime!![0]}-${
                String.format(
                    mContext.getString(R.string.time_two_num_format),
                    detailTime!![1]
                )
            }-${
                String.format(
                    mContext.getString(R.string.time_two_num_format),
                    detailTime!![2]
                )
            }"

            if (dailyItem.total == 0.0){
                holder.tv_daily_total.text = "---"
            }else{
                holder.tv_daily_total.text = AaruyNumberUtils.getDotTwoString(dailyItem.total)+"U"
            }
        }


    }

    override fun getItemCount(): Int {
        return dailyItems.size
    }
}