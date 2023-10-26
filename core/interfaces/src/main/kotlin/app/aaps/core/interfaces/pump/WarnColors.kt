package app.aaps.core.interfaces.pump

import android.widget.TextView
import app.aaps.core.data.model.TE

interface WarnColors {

    fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double)
    fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double)
    fun setColorByAge(view: TextView?, therapyEvent: TE, warnThreshold: Double, urgentThreshold: Double)
}