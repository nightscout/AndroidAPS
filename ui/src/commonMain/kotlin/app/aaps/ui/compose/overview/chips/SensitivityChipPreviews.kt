package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun SensitivityChipAbovePreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(asText = "112%", isfFrom = "5.5", isfTo = "6.8", ratio = 1.12, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SensitivityChipBelowDisabledPreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(asText = "88%", ratio = 0.88, isEnabled = false, hasData = true),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SensitivityChipIsfDownPreview() {
    MaterialTheme {
        SensitivityChip(
            state = SensitivityUiState(isfFrom = "6.0", isfTo = "4.2", ratio = 1.15, isEnabled = true, hasData = true),
            onClick = {}
        )
    }
}
