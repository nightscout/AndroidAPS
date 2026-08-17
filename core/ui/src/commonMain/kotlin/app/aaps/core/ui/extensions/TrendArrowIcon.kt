package app.aaps.core.ui.extensions

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.ui.compose.icons.IcArrowDoubleDown
import app.aaps.core.ui.compose.icons.IcArrowDoubleUp
import app.aaps.core.ui.compose.icons.IcArrowFlat
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveDown
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveUp
import app.aaps.core.ui.compose.icons.IcArrowInvalid
import app.aaps.core.ui.compose.icons.IcArrowSimpleDown
import app.aaps.core.ui.compose.icons.IcArrowSimpleUp

/**
 * The icon that draws a [TrendArrow].
 *
 * Lives here and not next to the model, for the same reason a model does not carry a colour:
 * [TrendArrow] is the classification, and picking a picture for it is the job of the layer that
 * draws. It used to sit in `:core:objects`, which made a domain module depend on this one.
 */
fun TrendArrow.directionToIcon(): ImageVector =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> IcArrowInvalid
        TrendArrow.DOUBLE_DOWN     -> IcArrowDoubleDown
        TrendArrow.SINGLE_DOWN     -> IcArrowSimpleDown
        TrendArrow.FORTY_FIVE_DOWN -> IcArrowFortyfiveDown
        TrendArrow.FLAT            -> IcArrowFlat
        TrendArrow.FORTY_FIVE_UP   -> IcArrowFortyfiveUp
        TrendArrow.SINGLE_UP       -> IcArrowSimpleUp
        TrendArrow.DOUBLE_UP       -> IcArrowDoubleUp
        TrendArrow.TRIPLE_UP       -> IcArrowInvalid
        TrendArrow.NONE            -> IcArrowInvalid
    }
