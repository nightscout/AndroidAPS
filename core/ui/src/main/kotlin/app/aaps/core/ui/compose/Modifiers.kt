package app.aaps.core.ui.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Modifier that clears focus when tapping outside of focused text fields.
 *
 * Compose does not automatically clear focus when clicking outside text fields
 * (unlike Android Views). This modifier should be applied to the root container
 * of screens that contain text input fields.
 *
 * Usage:
 * ```
 * val focusManager = LocalFocusManager.current
 *
 * Box(modifier = Modifier.clearFocusOnTap(focusManager)) {
 *     // Content with text fields
 * }
 * ```
 *
 * @param focusManager The FocusManager from LocalFocusManager.current
 */
fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }

/**
 * Consumes leftover scroll so it doesn't reach a parent ModalBottomSheet.
 *
 * Without this, scrolling to the end of content inside a ModalBottomSheet
 * causes the sheet to interpret overscroll as a dismiss gesture, leading to
 * jitter and crashes. Apply this modifier **before** `.verticalScroll(…)`.
 */
private val ConsumeOverscrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
}

fun Modifier.consumeOverscroll(): Modifier = this.nestedScroll(ConsumeOverscrollConnection)
