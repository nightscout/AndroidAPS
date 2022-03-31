package info.nightscout.androidaps.utils

import android.widget.TextView
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.isOlderThan
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarnColors @Inject constructor(val rh: ResourceHelper) {


    fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) =
        view?.setTextColor( rh.gac( view.context ,when {
            value >= urgentLevel -> R.attr.urgentColor
            value >= warnLevel   -> R.attr.warnColor
            else                 -> R.attr.defaultTextColor
        }))

    fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) =
        view?.setTextColor( rh.gac( view.context , when {
            value <= urgentLevel -> R.attr.urgentColor
            value <= warnLevel   -> R.attr.warnColor
            else                 -> R.attr.defaultTextColor
        }))

    fun setColorByAge(view: TextView?, therapyEvent: TherapyEvent, warnThreshold: Double, urgentThreshold: Double) =
        view?.setTextColor( rh.gac( view.context , when {
            therapyEvent.isOlderThan(urgentThreshold) -> R.attr.lowColor
            therapyEvent.isOlderThan(warnThreshold)   -> R.attr.highColor
            else                                      -> R.attr.defaultTextColor
        }))
}