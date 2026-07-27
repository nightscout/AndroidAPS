package app.aaps.ui.compose.insulinDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
import java.text.DecimalFormat

@Preview(showBackground = true)
@Composable
internal fun InsulinDialogScreenPreview() {
    MaterialTheme {
        InsulinDialogContent(
            uiState = InsulinDialogUiState(
                insulin = 2.5,
                selectedIcfg = null,
                insulins = ArrayList(),
                maxInsulin = 10.0,
                bolusStep = 0.1,
                insulinButtonIncrement1 = 0.5,
                insulinButtonIncrement2 = 1.0,
                insulinButtonIncrement3 = 2.0,
                showNotesFromPreferences = true
            ),
            bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
            iob = IobUiState(),
            cob = CobUiState(),
            dateString = "25/02/2026",
            timeString = "14:30",
            bolusFormat = DecimalFormat("0.0"),
            formatAmount = { DecimalFormat("0.0").format(it) },
            onEatingSoonChange = {},
            onRecordOnlyChange = {},
            onInsulinChange = {},
            onAddInsulin = {},
            onTimeOffsetChange = {},
            onNotesChange = {},
            onDateClick = {},
            onTimeClick = {},
            onSettingsClick = null,
            onNavigateBack = {},
            onConfirmClick = {},
            onInsulinTypeSelect = {}
        )
    }
}
