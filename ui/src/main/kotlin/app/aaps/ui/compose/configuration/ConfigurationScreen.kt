package app.aaps.ui.compose.configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.preference.SyncBadge
import app.aaps.ui.plugin.HardwarePumpConfirmation

@Composable
fun ConfigurationScreen(
    categories: List<ConfigCategoryUiModel>,
    hardwarePumpConfirmation: HardwarePumpConfirmation?,
    onNavigateBack: () -> Unit,
    onNavigateToCategory: (PluginType) -> Unit,
    onConfirmHardwarePump: () -> Unit,
    onDismissHardwarePump: () -> Unit,
) {
    if (hardwarePumpConfirmation != null) {
        OkCancelDialog(
            title = stringResource(R.string.confirmation),
            message = hardwarePumpConfirmation.message,
            onConfirm = onConfirmHardwarePump,
            onDismiss = onDismissHardwarePump
        )
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.nav_configuration)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            categories.forEach { category ->
                item(key = "cat_${category.type}") {
                    CategoryRow(
                        category = category,
                        onClick = { onNavigateToCategory(category.type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: ConfigCategoryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryName = stringResource(category.titleRes)
    val iconPainter = rememberVectorPainter(category.categoryIcon ?: Icons.Default.Settings)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Synced (master-linked) category: the same PhonelinkRing badge used on synced preference rows.
        SyncBadge(visible = category.synced, modifier = Modifier.padding(end = 8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}
