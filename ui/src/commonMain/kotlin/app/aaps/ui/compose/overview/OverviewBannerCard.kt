package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.ui.compose.AapsSpacing

/**
 * The full-width card at the top of the overview, for something running now: the active scene and the
 * "time to eat" timer. One look for both, so they read as the same kind of thing.
 *
 * @param lines smaller text under the title, one line each
 * @param progress 0..1 for the bar under the header, or null for no bar
 * @param trailing at the end of the header row: a button, or a value to see at a glance
 * @param actions buttons in a row under everything, at the end; null for none
 */
@Composable
fun OverviewBannerCard(
    icon: ImageVector,
    color: Color,
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    trailing: @Composable () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
    ) {
        Column(modifier = Modifier.padding(AapsSpacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = color
                    )
                    lines.forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing()
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AapsSpacing.medium)
                )
            }
            if (actions != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AapsSpacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}
