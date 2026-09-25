package app.aaps.pump.eopatch.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner

@Preview(showBackground = true, name = "Overview - Activated")
@Composable
internal fun EopatchOverviewActivatedPreview() {
    MaterialTheme {
        PumpOverviewScreen(
            state = PumpOverviewUiState(
                infoRows = listOf(
                    PumpInfoRow(label = "Status", value = "Running"),
                    PumpInfoRow(label = "Basal Rate", value = "1.00 U/h"),
                    PumpInfoRow(label = "Reservoir", value = "185 U"),
                    PumpInfoRow(label = "Serial", value = "EO00-AB12")
                ),
                primaryActions = listOf(
                    PumpAction(label = "Suspend pump", icon = IcLoopPaused, onClick = {})
                ),
                managementActions = listOf(
                    PumpAction(label = "Discard Patch", icon = Icons.Filled.SwapHoriz, category = ActionCategory.MANAGEMENT, onClick = {})
                )
            )
        )
    }
}

@Preview(showBackground = true, name = "Overview - Not Activated")
@Composable
internal fun EopatchOverviewNotActivatedPreview() {
    MaterialTheme {
        PumpOverviewScreen(
            state = PumpOverviewUiState(
                statusBanner = StatusBanner(text = "Patch not activated", level = StatusLevel.WARNING),
                primaryActions = listOf(
                    PumpAction(label = "Activate Patch", icon = Icons.Filled.SwapHoriz, onClick = {})
                )
            )
        )
    }
}
