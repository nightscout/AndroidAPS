package app.aaps.plugins.sync.wear.compose

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun CwfImportContentPreview() {
    MaterialTheme {
        CwfImportContent(
            items = listOf(
                previewImportItem("AAPS V2", "AAPS_V2", true, 3, true),
                previewImportItem("Digital Style", "digital_style", false, 0, false),
                previewImportItem("Steampunk", "steampunk", true, 2, false)
            ),
            onItemClick = {}
        )
    }
}

@Suppress("SameParameterValue")
private fun previewImportItem(
    name: String,
    fileName: String,
    versionOk: Boolean,
    prefCount: Int,
    hasPrefAuth: Boolean
) = CwfImportItemState(
    cwfFile = app.aaps.core.interfaces.rx.weardata.CwfFile(
        cwfData = app.aaps.core.interfaces.rx.weardata.CwfData("", mutableMapOf(), mutableMapOf()),
        zipByteArray = ByteArray(0)
    ),
    name = name,
    fileName = "Filename: $fileName.cwf",
    author = "Author: Someone",
    createdAt = "Created: 2025-01-15",
    version = "Version: 1.0",
    isVersionOk = versionOk,
    prefCount = prefCount,
    hasPrefAuthorization = hasPrefAuth,
    watchfaceImage = null
)
