package app.aaps.core.ui.compose.banner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSpacing

/**
 * High-visibility banner for failure / error conditions (Material3 errorContainer).
 * Use for: pump pairing errors, deactivation failures, communication errors, password errors, etc.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) = BannerCore(
    message = message,
    icon = Icons.Default.Error,
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    modifier = modifier
)

/**
 * Notice banner for state warnings that aren't failures (Material3 tertiaryContainer).
 * Use for: "this entry will be recorded only", reduced-functionality notices, advisory states.
 */
@Composable
fun WarningBanner(
    message: String,
    modifier: Modifier = Modifier
) = BannerCore(
    message = message,
    icon = Icons.Default.Warning,
    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    modifier = modifier
)

@Composable
private fun BannerCore(
    message: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(AapsSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorBannerPreview() {
    ErrorBanner(message = "Communication error. Please retry.")
}

@Preview(showBackground = true)
@Composable
private fun WarningBannerPreview() {
    WarningBanner(message = "Bolus will be recorded only (not delivered by pump)")
}
