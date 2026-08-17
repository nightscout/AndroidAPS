package app.aaps.core.ui.compose

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * AndroidAPS FAB component with proper elevation visibility in dark mode.
 *
 * Uses explicit shadow elevation that is visible in both light and dark themes.
 *
 * @param onClick Called when the FAB is clicked
 * @param modifier Modifier to be applied to the FAB
 * @param content The content of the FAB (typically an Icon)
 *
 * @see AapsFabPreview
 */
@Composable
fun AapsFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp,
            focusedElevation = 8.dp,
            hoveredElevation = 8.dp
        ),
        content = content
    )
}

/**
 * AndroidAPS small FAB component with proper elevation visibility in dark mode.
 *
 * Uses explicit shadow elevation that is visible in both light and dark themes.
 *
 * @param onClick Called when the FAB is clicked
 * @param modifier Modifier to be applied to the FAB
 * @param content The content of the FAB (typically an Icon)
 *
 * @see AapsSmallFabPreview
 */
@Composable
fun AapsSmallFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp,
            focusedElevation = 8.dp,
            hoveredElevation = 8.dp
        ),
        content = content
    )
}
