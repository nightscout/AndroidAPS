package app.aaps.ui.widget

import androidx.annotation.DrawableRes
import app.aaps.core.data.model.TrendArrow
import app.aaps.ui.R

@DrawableRes
fun TrendArrow.directionToDrawableRes(): Int =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> R.drawable.ic_widget_arrow_invalid
        TrendArrow.DOUBLE_DOWN     -> R.drawable.ic_widget_arrow_doubledown
        TrendArrow.SINGLE_DOWN     -> R.drawable.ic_widget_arrow_singledown
        TrendArrow.FORTY_FIVE_DOWN -> R.drawable.ic_widget_arrow_fortyfivedown
        TrendArrow.FLAT            -> R.drawable.ic_widget_arrow_flat
        TrendArrow.FORTY_FIVE_UP   -> R.drawable.ic_widget_arrow_fortyfiveup
        TrendArrow.SINGLE_UP       -> R.drawable.ic_widget_arrow_singleup
        TrendArrow.DOUBLE_UP       -> R.drawable.ic_widget_arrow_doubleup
        TrendArrow.TRIPLE_UP       -> R.drawable.ic_widget_arrow_invalid
        TrendArrow.NONE            -> R.drawable.ic_widget_arrow_invalid
    }
