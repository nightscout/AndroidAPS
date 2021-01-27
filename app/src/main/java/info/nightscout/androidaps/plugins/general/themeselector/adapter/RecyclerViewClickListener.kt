package info.nightscout.androidaps.plugins.general.themeselector.adapter

import android.view.View

interface RecyclerViewClickListener {
    fun onClick(view: View?, position: Int)
}