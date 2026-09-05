package app.aaps.core.ui.compose

import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT

/**
 * Single source of truth for state→color mapping shared across overview and the widget.
 * Non-composable so the widget (which has no CompositionLocal access) can call these
 * with a static palette (e.g. [DarkGeneralColors]). Overview passes
 * `AapsTheme.generalColors` to stay theme-aware.
 */

fun RM.Mode.loopColor(c: GeneralColors): Color = when (this) {
    RM.Mode.CLOSED_LOOP,
    RM.Mode.RESUME            -> c.loopClosed

    RM.Mode.CLOSED_LOOP_LGS   -> c.loopLgs
    RM.Mode.OPEN_LOOP         -> c.loopOpened
    RM.Mode.DISABLED_LOOP,
    RM.Mode.SUSPENDED_BY_PUMP,
    RM.Mode.SUSPENDED_BY_USER,
    RM.Mode.SUSPENDED_BY_DST  -> c.loopDisabled

    RM.Mode.SUPER_BOLUS       -> c.loopSuperBolus
    RM.Mode.DISCONNECTED_PUMP -> c.loopDisconnected
}

/**
 * Color for the temp-target chip icon based on the reason of the active TT.
 * Custom / Automation / Wear / null → [GeneralColors.ttCustom].
 */
fun TT.Reason?.ttReasonColor(c: GeneralColors): Color = when (this) {
    TT.Reason.EATING_SOON  -> c.ttEatingSoon
    TT.Reason.ACTIVITY     -> c.ttActivity
    TT.Reason.HYPOGLYCEMIA -> c.ttHypoglycemia
    else                   -> c.ttCustom
}
