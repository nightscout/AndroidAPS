package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AndroidAPS card component with proper elevation visibility in dark mode.
 *
 * Uses [ElevatedCard] which provides shadow-based elevation that is visible
 * in both light and dark themes.
 *
 * @param modifier Modifier to be applied to the card
 * @param selected Whether the card is in selected state (uses secondaryContainer color)
 * @param content The content of the card
 *
 * @see AapsCardPreview
 * @see AapsCardSelectedPreview
 */
@Composable
fun AapsCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        content = content
    )
}
