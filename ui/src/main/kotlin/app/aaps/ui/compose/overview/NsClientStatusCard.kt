package app.aaps.ui.compose.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.NsClientLevel
import app.aaps.core.interfaces.overview.graph.NsClientStatusData
import app.aaps.core.interfaces.overview.graph.NsClientStatusItem
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.dialogs.OkDialog

@Composable
fun NsClientStatusCard(
    statusData: NsClientStatusData,
    flavorTint: Color,
    modifier: Modifier = Modifier
) {
    val items = listOfNotNull(statusData.pump, statusData.openAps, statusData.uploader)
    if (items.isEmpty()) return

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(flavorTint)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                NsClientStatusChip(item = item)
            }
        }
    }
}

@Composable
private fun NsClientStatusChip(
    item: NsClientStatusItem,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    val valueColor = when (item.level) {
        NsClientLevel.INFO -> MaterialTheme.colorScheme.onSurface
        NsClientLevel.WARN -> AapsTheme.generalColors.statusWarning
        NsClientLevel.URGENT -> AapsTheme.generalColors.statusCritical
    }

    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor)) {
            append("${item.label}: ")
        }
        withStyle(SpanStyle(color = valueColor)) {
            append(item.value)
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        OkDialog(
            title = item.dialogTitle,
            message = item.dialogText,
            onDismiss = { showDialog = false }
        )
    }
}
