package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import app.aaps.core.ui.CoreUiStrings

/**
 * "Move up" / "Move down" screen-reader actions for one item of a drag-to-reorder list.
 *
 * The lists are reordered by dragging a handle, which a screen reader cannot do: double-tapping the
 * handle does nothing. Put these on the handle (`semantics { customActions = ... }`) and TalkBack
 * offers them in its actions menu. [onMove] gets the same (from, to) positions a drag would give.
 */
@Composable
fun reorderActions(index: Int, lastIndex: Int, onMove: (from: Int, to: Int) -> Unit): List<CustomAccessibilityAction> {
    val up = stringResource(CoreUiStrings.a11y_move_up)
    val down = stringResource(CoreUiStrings.a11y_move_down)
    return buildList {
        if (index > 0) add(CustomAccessibilityAction(up) { onMove(index, index - 1); true })
        if (index < lastIndex) add(CustomAccessibilityAction(down) { onMove(index, index + 1); true })
    }
}
