package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.GlucoseValue

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
