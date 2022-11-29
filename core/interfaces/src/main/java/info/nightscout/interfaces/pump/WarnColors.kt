package info.nightscout.interfaces.pump

import android.widget.TextView
import info.nightscout.database.entities.TherapyEvent

interface WarnColors {
    fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double)
    fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double)
    fun setColorByAge(view: TextView?, therapyEvent: TherapyEvent, warnThreshold: Double, urgentThreshold: Double)
}