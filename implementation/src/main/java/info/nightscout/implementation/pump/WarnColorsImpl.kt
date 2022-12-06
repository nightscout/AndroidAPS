package info.nightscout.implementation.pump

import android.widget.TextView
import info.nightscout.core.extensions.isOlderThan
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
                    value >= urgentLevel -> info.nightscout.core.ui.R.attr.urgentColor
                    value >= warnLevel   -> info.nightscout.core.ui.R.attr.warnColor
                    else                 -> info.nightscout.core.ui.R.attr.defaultTextColor
                }
            )
        )
    }

    override fun setColorInverse(view: TextView?, value: Double, warnLevel: Double, urgentLevel: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    value <= urgentLevel -> info.nightscout.core.ui.R.attr.urgentColor
                    value <= warnLevel   -> info.nightscout.core.ui.R.attr.warnColor
                    else                 -> info.nightscout.core.ui.R.attr.defaultTextColor
                }
            )
        )
    }

    override fun setColorByAge(view: TextView?, therapyEvent: TherapyEvent, warnThreshold: Double, urgentThreshold: Double) {
        view?.setTextColor(
            rh.gac(
                view.context, when {
                    therapyEvent.isOlderThan(urgentThreshold) -> info.nightscout.core.ui.R.attr.lowColor
                    therapyEvent.isOlderThan(warnThreshold)   -> info.nightscout.core.ui.R.attr.highColor
                    else                                      -> info.nightscout.core.ui.R.attr.defaultTextColor
                }
            )
        )
    }
}
