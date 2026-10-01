package app.aaps.ui.compose.maintenance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.maintenance.ExportConfig

@Preview(showBackground = true)
@Composable
internal fun MaintenanceBottomSheetContentPreview() {
    MaterialTheme {
        MaintenanceBottomSheetContent(
            exportConfig = ExportConfig(
                isCloudActive = true,
                isCloudError = false,
                hasCloudCredentials = true,
                settingsLocal = true,
                settingsCloud = true,
                logEmail = true,
                logCloud = false,
                csvLocal = true,
                csvCloud = false,
                cloudDisplayName = "Google Drive"
            ),
            isDirectoryAccessGranted = true
        )
    }
}
