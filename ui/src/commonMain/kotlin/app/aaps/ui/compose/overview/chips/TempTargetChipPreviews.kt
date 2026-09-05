package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TT
import app.aaps.ui.compose.main.TempTargetChipState

@Preview(showBackground = true)
@Composable
internal fun TempTargetChipActivePreview() {
    MaterialTheme {
        TempTargetChip(
            targetText = "5.5 - 5.5 (30 min)",
            state = TempTargetChipState.Active,
            progress = 0.5f,
            reason = TT.Reason.EATING_SOON,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun TempTargetChipNonePreview() {
    MaterialTheme {
        TempTargetChip(
            targetText = "5.0 - 7.0",
            state = TempTargetChipState.None,
            progress = 0f,
            reason = null,
            onClick = {}
        )
    }
}
