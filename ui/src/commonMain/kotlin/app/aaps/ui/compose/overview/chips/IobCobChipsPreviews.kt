package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun IobCobChipsRowPreview() {
    MaterialTheme {
        IobCobChipsRow(
            iobUiState = IobUiState(text = "1.25 U", iobTotal = 1.25),
            cobUiState = CobUiState(text = "24g", cobValue = 24.0),
            onIobChipClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun IobCobChipsRowCarbsReqPreview() {
    MaterialTheme {
        IobCobChipsRow(
            iobUiState = IobUiState(text = "1.25 U", iobTotal = 1.25),
            cobUiState = CobUiState(text = "12g\n45 required", carbsReq = 45, cobValue = 12.0),
            onIobChipClick = {}
        )
    }
}
