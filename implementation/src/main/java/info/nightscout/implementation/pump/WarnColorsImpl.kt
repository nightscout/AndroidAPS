package info.nightscout.implementation.pump

import android.widget.TextView
import info.nightscout.core.extensions.isOlderThan
import info.nightscout.core.main.R
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarnColorsImpl @Inject constructor(val rh: ResourceHelper): WarnColors {

    override fun setColor(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    value >= urgentLevel -> R.attr.urgentColor
                    value >= warnLevel   -> R.attr.warnColor
                    else                 -> R.attr.defaultTextColor
                }
            )
        )
    }

    override fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    value <= urgentLevel -> R.attr.urgentColor
                    value <= warnLevel   -> R.attr.warnColor
                    else                 -> R.attr.defaultTextColor
                }
            )
        )
    }

    override fun setColorByAge(view: TextView?, therapyEvent: TherapyEvent, warnThreshold: Double, urgentThreshold: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    therapyEvent.isOlderThan(urgentThreshold) -> R.attr.lowColor
                    therapyEvent.isOlderThan(warnThreshold)   -> R.attr.highColor
                    else                                      -> R.attr.defaultTextColor
                }
            )
        )
    }
}
