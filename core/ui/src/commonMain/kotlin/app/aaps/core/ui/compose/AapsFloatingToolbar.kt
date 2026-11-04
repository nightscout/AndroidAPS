package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Same as the default elevation of [AapsFab], so the toolbar and the FAB beside it cast the same shadow. */
private val toolbarElevation = 6.dp

/**
 * The bottom row of a screen with a [AapsFloatingToolbar]: the toolbar, and the screen's FAB beside it.
 *
 * M3 pairs a floating toolbar with one FAB. The FAB is the main action of the screen, every other
 * action goes into the toolbar, also a secondary one like Cancel.
 *
 * Put it in a Box over the content with `Modifier.align(Alignment.BottomCenter)`, and end the content
 * with [AapsSpacing.fabClearance] of space, so the row never covers the last field.
 *
 * @param modifier Modifier to be applied to the row
 * @param content The toolbar first, then the FAB
 */
@Composable
fun AapsFloatingToolbarRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.padding(AapsSpacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * M3 floating toolbar: a pill of icon buttons that floats over the content.
 *
 * @param modifier Modifier to be applied to the toolbar
 * @param content The buttons, usually `IconButton`s
 */
@Composable
fun AapsFloatingToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = toolbarElevation,
        tonalElevation = toolbarElevation,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Delete button for a [AapsFloatingToolbar]: red while it can be used, grey while it cannot.
 *
 * @param onClick Called when the button is clicked. It should ask before it deletes anything.
 * @param enabled Whether the selected item can be deleted
 * @param contentDescription What is deleted, for screen readers
 */
@Composable
fun AapsFloatingToolbarDeleteButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = contentDescription,
            // A tint replaces the button's own disabled colour, so the grey has to be set here as well
            tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
