package info.nightscout.core.extensions

import info.nightscout.core.main.R
import info.nightscout.database.entities.GlucoseValue

fun GlucoseValue.TrendArrow.directionToIcon(): Int {
    return when {
        this == GlucoseValue.TrendArrow.DOUBLE_DOWN     -> R.drawable.ic_doubledown
        this == GlucoseValue.TrendArrow.SINGLE_DOWN     -> R.drawable.ic_singledown
        this == GlucoseValue.TrendArrow.FORTY_FIVE_DOWN -> R.drawable.ic_fortyfivedown
        this == GlucoseValue.TrendArrow.FLAT            -> R.drawable.ic_flat
        this == GlucoseValue.TrendArrow.FORTY_FIVE_UP   -> R.drawable.ic_fortyfiveup
        this == GlucoseValue.TrendArrow.SINGLE_UP       -> R.drawable.ic_singleup
        this == GlucoseValue.TrendArrow.DOUBLE_UP       -> R.drawable.ic_doubleup
        this == GlucoseValue.TrendArrow.NONE            -> R.drawable.ic_invalid
        else                                            -> R.drawable.ic_invalid
    }
}
