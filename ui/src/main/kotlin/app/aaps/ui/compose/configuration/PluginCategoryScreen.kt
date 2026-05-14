package app.aaps.ui.compose.configuration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ConfigPluginCard
import app.aaps.core.ui.compose.SelectionMode
import app.aaps.core.ui.compose.navigation.NavigationRequest

@Composable
fun PluginCategoryScreen(
    category: ConfigCategoryUiModel?,
    hardwarePumpConfirmation: HardwarePumpConfirmation?,
    onNavigateBack: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    onPluginEnableToggle: (pluginId: String, PluginType, Boolean) -> Unit,
    onConfirmHardwarePump: () -> Unit,
    onDismissHardwarePump: () -> Unit,
) {
    if (hardwarePumpConfirmation != null) {
        AlertDialog(
            onDismissRequest = onDismissHardwarePump,
            title = { Text(stringResource(R.string.confirmation)) },
            text = { Text(hardwarePumpConfirmation.message) },
            confirmButton = {
                TextButton(onClick = onConfirmHardwarePump) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissHardwarePump) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val title = category?.titleRes?.let { stringResource(it) }.orEmpty()

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(title) },
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
        if (category == null) return@Scaffold

        val selectionMode =
            if (category.isMultiSelect) SelectionMode.MULTI_SELECT else SelectionMode.SINGLE_SELECT
        val hintRes = if (category.isMultiSelect) R.string.configbuilder_pick_many_hint
        else R.string.configbuilder_pick_one_hint
        val noneSelected = !category.isMultiSelect && category.plugins.none { it.isEnabled }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item(key = "hint") {
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = AapsSpacing.xxLarge,
                        vertical = AapsSpacing.large
                    )
                )
            }
            if (noneSelected) {
                item(key = "warning") {
                    NoSelectionWarning()
                }
            }
            items(items = category.plugins, key = { it.id }) { plugin ->
                ConfigPluginCard(
                    plugin = plugin,
                    selectionMode = selectionMode,
                    showOpenPlugin = true,
                    onCardClick = {
                        onPluginEnableToggle(plugin.id, category.type, !plugin.isEnabled)
                    },
                    onSettingsClick = { onNavigate(NavigationRequest.PluginPreferences(plugin.id)) },
                    onOpenPluginClick = { onNavigate(NavigationRequest.Plugin(plugin.id)) }
                )
            }
        }
    }
}

@Composable
private fun NoSelectionWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(
            horizontal = AapsSpacing.medium,
            vertical = AapsSpacing.small
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(AapsSpacing.large)
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                modifier = Modifier.padding(end = AapsSpacing.large)
            )
            Column {
                Text(
                    text = stringResource(R.string.configbuilder_no_selection_warning),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
