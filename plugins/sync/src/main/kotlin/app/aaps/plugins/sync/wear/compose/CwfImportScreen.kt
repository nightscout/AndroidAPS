package app.aaps.plugins.sync.wear.compose

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing

@Composable
internal fun CwfImportContent(
    items: List<CwfImportItemState>,
    onItemClick: (CwfImportItemState) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(AapsSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        items(items, key = { it.fileName }) { item ->
            CwfImportItemCard(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun CwfImportItemCard(
    item: CwfImportItemState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Watchface preview image
            item.watchfaceImage?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = item.name,
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(AapsSpacing.medium))
            }

            // Metadata column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = item.createdAt,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
                ) {
                    Text(
                        text = item.version,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isVersionOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (item.prefCount > 0) {
                        Text(
                            text = "${item.prefCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.hasPrefAuthorization) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (item.hasPrefAuthorization) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (item.hasPrefAuthorization) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CwfImportContentPreview() {
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
