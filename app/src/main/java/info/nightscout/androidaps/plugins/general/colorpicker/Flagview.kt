package info.nightscout.androidaps.plugins.general.colorpicker

import android.content.Context
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.flag.FlagView
import kotlinx.android.synthetic.main.colorpicker_flagview.view.*

class CustomFlag(context: Context?, layout: Int) : FlagView(context, layout) {
    override fun onRefresh(colorEnvelope: ColorEnvelope) {
        flag_color_code.text = "#" + colorEnvelope.hexCode
        flag_color_layout.setBackgroundColor(colorEnvelope.color)
    }
}