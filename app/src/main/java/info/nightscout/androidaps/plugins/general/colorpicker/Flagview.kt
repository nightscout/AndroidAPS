package info.nightscout.androidaps.plugins.general.colorpicker

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.flag.FlagView
import info.nightscout.androidaps.R

class CustomFlag(context: Context?, layout: Int) : FlagView(context, layout) {
    override fun onRefresh(colorEnvelope: ColorEnvelope) {
        findViewById<TextView>(R.id.flag_color_code).text = "#" + colorEnvelope.hexCode
        findViewById<LinearLayout>(R.id.flag_color_layout).setBackgroundColor(colorEnvelope.color)
    }
}