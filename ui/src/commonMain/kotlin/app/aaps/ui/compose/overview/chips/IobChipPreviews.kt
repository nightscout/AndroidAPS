package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun IobChipPreview() {
    MaterialTheme {
        IobChip(state = IobUiState(text = "1.25 U", iobTotal = 1.25), onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
internal fun IobChipZeroPreview() {
    MaterialTheme {
        IobChip(state = IobUiState(text = "0.00 U", iobTotal = 0.0), onClick = {})
    }
}
