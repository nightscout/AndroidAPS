package app.aaps.core.ui.compose.banner

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ErrorBannerPreview() {
    ErrorBanner(message = "Communication error. Please retry.")
}

@Preview(showBackground = true)
@Composable
internal fun WarningBannerPreview() {
    WarningBanner(message = "Bolus will be recorded only (not delivered by pump)")
}
