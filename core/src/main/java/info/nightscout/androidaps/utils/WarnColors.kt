package info.nightscout.androidaps.utils

import android.graphics.Color
import android.widget.TextView
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarnColors @Inject constructor(val resourceHelper: ResourceHelper) {

    fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) =
        view?.setTextColor(when {
            value >= urgentLevel -> ColorAlarm
            value >= warnLevel   -> ColorWarning
            else                 -> ColorNormal
        })

    fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) =
        view?.setTextColor(when {
            value <= urgentLevel -> ColorAlarm
            value <= warnLevel   -> ColorWarning
            else                 -> ColorNormal
        })

    fun setColorByAge(view: TextView?, careportalEvent: CareportalEvent, warnThreshold: Double, urgentThreshold: Double, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) =
        view?.setTextColor(when {
            careportalEvent.isOlderThan(urgentThreshold) -> ColorAlarm
            careportalEvent.isOlderThan(warnThreshold)   -> ColorWarning
            else                                         -> ColorNormal
        })
}