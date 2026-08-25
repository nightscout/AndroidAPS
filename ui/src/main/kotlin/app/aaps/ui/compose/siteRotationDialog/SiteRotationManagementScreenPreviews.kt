package app.aaps.ui.compose.siteRotationDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.siteRotation.SiteEntryDisplayData
import app.aaps.core.ui.compose.siteRotation.directionToComposeIcon
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationUiState

@Preview(showBackground = true)
@Composable
internal fun SiteRotationManagementPreview() {
    MaterialTheme {
        SiteRotationManagementContent(
            uiState = SiteRotationUiState(
                isLoading = false,
                showPumpSites = true,
                showCgmSites = true
            ),
            displayEntries = listOf(
                SiteEntryDisplayData(
                    typeIcon = IcCannulaChange,
                    dateString = "10/03/2026",
                    locationString = "Front Right Upper Abdomen",
                    arrowIcon = TE.Arrow.UP.directionToComposeIcon(),
                    note = "Rotated clockwise",
                    timestamp = 1741600000000L,
                    location = TE.Location.FRONT_RIGHT_UPPER_ABDOMEN
                ),
                SiteEntryDisplayData(
                    typeIcon = IcCgmInsert,
                    dateString = "08/03/2026",
                    locationString = "Side Right Upper Arm",
                    arrowIcon = TE.Arrow.NONE.directionToComposeIcon(),
                    note = null,
                    timestamp = 1741400000000L,
                    location = TE.Location.SIDE_RIGHT_UPPER_ARM
                )
            ),
            onClose = {},
            onPreferenceClick = {},
            onShowPumpSites = {},
            onShowCgmSites = {},
            onZoneClick = {},
            onEntryClick = {},
            onEditEntry = {},
            onCancelEdit = {},
            onConfirmEdit = {},
            onArrowClick = {},
            onNoteChange = {},
            editedTeDate = "",
            editedTeLocation = ""
        )
    }
}
