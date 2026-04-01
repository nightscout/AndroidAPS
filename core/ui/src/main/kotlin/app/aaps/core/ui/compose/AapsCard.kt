package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * AndroidAPS card component with proper elevation visibility in dark mode.
 *
 * Uses [ElevatedCard] which provides shadow-based elevation that is visible
 * in both light and dark themes.
 *
 * @param modifier Modifier to be applied to the card
 * @param selected Whether the card is in selected state (uses secondaryContainer color)
 * @param content The content of the card
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

@Preview(showBackground = true)
@Composable
private fun AapsCardPreview() {
    AapsTheme {
        AapsCard(modifier = Modifier.padding(16.dp)) {
            Text("Card content", modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AapsCardSelectedPreview() {
    AapsTheme {
        AapsCard(modifier = Modifier.padding(16.dp), selected = true) {
            Text("Selected card", modifier = Modifier.padding(16.dp))
        }
    }
}
