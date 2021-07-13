package info.nightscout.androidaps.utils

import android.graphics.Color
import android.widget.TextView
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.isOlderThan
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarnColors @Inject constructor(val resourceHelper: ResourceHelper) {

    fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double, colorNormal: Int, colorWarning: Int, colorAlarm: Int) =
        view?.setTextColor(when {
            value >= urgentLevel -> colorAlarm
            value >= warnLevel   -> colorWarning
            else                 -> colorNormal
        })

    fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double, colorNormal: Int, colorWarning: Int, colorAlarm: Int) =
        view?.setTextColor(when {
            value <= urgentLevel -> colorAlarm
            value <= warnLevel   -> colorWarning
            else                 -> colorNormal
        })

    fun setColorByAge(view: TextView?, therapyEvent: TherapyEvent, warnThreshold: Double, urgentThreshold: Double, colorNormal: Int, colorWarning: Int, colorAlarm: Int) =
        view?.setTextColor(when {
            therapyEvent.isOlderThan(urgentThreshold) -> colorAlarm
            therapyEvent.isOlderThan(warnThreshold)   -> colorWarning
            else                                         -> colorNormal
        })
}