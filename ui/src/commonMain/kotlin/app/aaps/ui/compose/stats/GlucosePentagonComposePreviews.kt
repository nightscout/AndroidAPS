package app.aaps.ui.compose.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
internal fun GlucosePentagonPreview() {
    val sampleData = CgpData(
        torPct = 28.0,
        cvPct = 27.3,
        hypoPct = 5.7,
        hyperPct = 22.3,
        meanGlucose = 154.0,
        normalizedValues = listOf(0.26, 0.31, 0.20, 0.25, 0.27),
        referenceValues = listOf(0.18, 0.18, 0.18, 0.18, 0.18),
        pgr = 1.9
    )

    MaterialTheme {
        GlucosePentagonCard(
            cgpData = sampleData,
            meanGlucoseFormatted = "154",
            modifier = Modifier.fillMaxWidth()
        )
    }
}
