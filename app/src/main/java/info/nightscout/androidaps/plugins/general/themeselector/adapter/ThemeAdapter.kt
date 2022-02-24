package info.nightscout.androidaps.plugins.general.themeselector.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.themeselector.ScrollingActivity
import info.nightscout.androidaps.plugins.general.themeselector.adapter.ThemeAdapter.MyViewHolder
import info.nightscout.androidaps.plugins.general.themeselector.model.Theme
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.getThemeName
import info.nightscout.androidaps.plugins.general.themeselector.view.ThemeView
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP

class ThemeAdapter(private val sp: SP, private val themeList: List<Theme>, private val mRecyclerViewClickListener: RecyclerViewClickListener) : RecyclerView.Adapter<MyViewHolder>() {
    private val resourceHelper: ResourceHelper? = null

    inner class MyViewHolder(view: View, private val mListener: RecyclerViewClickListener) : RecyclerView.ViewHolder(view), View.OnClickListener {
        var themeView: ThemeView
        var textView: TextView
        override fun onClick(view: View) {
            mListener.onClick(view, getBindingAdapterPosition())
            ScrollingActivity.selectedTheme = getBindingAdapterPosition()
            val mTheme = ScrollingActivity.mThemeList[getBindingAdapterPosition()].id
            Log.d("TAG", "theme id: $mTheme")
            themeView.isActivated = true
            notifyDataSetChanged()
        }

        init {
            themeView = view.findViewById(R.id.themeView)
            textView = view.findViewById(R.id.themeLabel)
            view.setOnClickListener(this)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.themeselector_list_row_theme, parent, false)
        return MyViewHolder(itemView, mRecyclerViewClickListener)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val theme = themeList[position]
        holder.textView.text = getThemeName(position, sp.getBoolean(R.string.key_use_dark_mode, true))
        holder.themeView.setTheme(theme, position)
        holder.themeView.isActivated = ScrollingActivity.selectedTheme == position
    }

    override fun getItemCount(): Int {
        return themeList.size
    }

}