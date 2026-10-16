package app.aaps.core.ui.extensions

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
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

/**
 * The spoken name of a [TrendArrow], for a screen reader.
 *
 * Lives beside [directionToIcon] for the same reason: naming the trend is the drawing layer's job.
 * Returns a [TextRef] rather than a String so both sides can use it - a Composable resolves it with
 * `stringResource(...)`, other code with `rh.gs(...)`. That is what keeps this the single mapping;
 * the alternative was a copy per caller, because the older `TrendCalculator.getTrendDescription`
 * needs a whole `AutosensDataStore` and so cannot be called with a plain arrow in hand.
 *
 * The `when` is exhaustive on purpose, with no `else`: a new [TrendArrow] must fail to compile here
 * rather than silently start announcing "unknown".
 */
fun TrendArrow?.directionToDescription(): TextRef =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> CoreUiStrings.a11y_arrow_triple_down
        TrendArrow.DOUBLE_DOWN     -> CoreUiStrings.a11y_arrow_double_down
        TrendArrow.SINGLE_DOWN     -> CoreUiStrings.a11y_arrow_single_down
        TrendArrow.FORTY_FIVE_DOWN -> CoreUiStrings.a11y_arrow_forty_five_down
        TrendArrow.FLAT            -> CoreUiStrings.a11y_arrow_flat
        TrendArrow.FORTY_FIVE_UP   -> CoreUiStrings.a11y_arrow_forty_five_up
        TrendArrow.SINGLE_UP       -> CoreUiStrings.a11y_arrow_single_up
        TrendArrow.DOUBLE_UP       -> CoreUiStrings.a11y_arrow_double_up
        TrendArrow.TRIPLE_UP       -> CoreUiStrings.a11y_arrow_triple_up
        TrendArrow.NONE            -> CoreUiStrings.a11y_arrow_none
        null                       -> CoreUiStrings.a11y_arrow_unknown
    }
