package app.aaps.ui.compose.manageSheet

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ManageBottomSheetContentPreview() {
    MaterialTheme {
        ManageBottomSheetContent(
            showTempTarget = true,
            showTempBasal = true,
            showCancelTempBasal = false,
            showExtendedBolus = true,
            showCancelExtendedBolus = false,
            cancelTempBasalText = "",
            cancelExtendedBolusText = "",
            customActions = emptyList()
        )
    }
}
