package app.aaps.ui.compose.configuration

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.ui.compose.main.DrawerCategory

@Composable
fun ConfigurationScreen(
    categories: List<DrawerCategory>,
    isSimpleMode: Boolean,
    pluginStateVersion: Int,
    hardwarePumpConfirmation: HardwarePumpConfirmation?,
    onNavigateBack: () -> Unit,
    onPluginClick: (PluginBase) -> Unit,
    onPluginEnableToggle: (PluginBase, PluginType, Boolean) -> Unit,
    onPluginPreferencesClick: (PluginBase) -> Unit,
    onConfirmHardwarePump: () -> Unit,
    onDismissHardwarePump: () -> Unit,
) {
    var selectedTypeOrdinal by rememberSaveable { mutableStateOf(-1) }
    val selectedCategory = if (selectedTypeOrdinal >= 0) {
        categories.find { it.type.ordinal == selectedTypeOrdinal }
    } else null

    if (selectedCategory != null) {
        BackHandler { selectedTypeOrdinal = -1 }
    }

    if (hardwarePumpConfirmation != null) {
        AlertDialog(
            onDismissRequest = onDismissHardwarePump,
            title = { Text(stringResource(app.aaps.core.ui.R.string.confirmation)) },
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

    Crossfade(
        targetState = selectedCategory,
        label = "configNav"
    ) { category ->
        if (category != null) {
            CategoryDetailScreen(
                category = category,
                isSimpleMode = isSimpleMode,
                pluginStateVersion = pluginStateVersion,
                onNavigateBack = { selectedTypeOrdinal = -1 },
                onPluginClick = onPluginClick,
                onPluginEnableToggle = onPluginEnableToggle,
                onPluginPreferencesClick = onPluginPreferencesClick
            )
        } else {
            CategoryListScreen(
                categories = categories,
                onCategoryClick = { selectedTypeOrdinal = it.type.ordinal },
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
private fun CategoryListScreen(
    categories: List<DrawerCategory>,
    onCategoryClick: (DrawerCategory) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(app.aaps.core.ui.R.string.nav_configuration)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
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
            items(categories, key = { it.type }) { category ->
                CategoryRow(
                    category = category,
                    onClick = { onCategoryClick(category) }
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: DrawerCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryName = stringResource(category.titleRes)
    val subtitle = if (category.enabledCount == 1) {
        category.enabledPlugins.firstOrNull()?.name ?: "-"
    } else if (category.isMultiSelect) {
        if (category.enabledCount > 0) "${category.enabledCount}" else "-"
    } else {
        category.activePluginName ?: "-"
    }

    val plugin = if (category.enabledCount == 1) category.enabledPlugins.firstOrNull() else null
    val composeIcon = plugin?.pluginDescription?.icon
    val defaultCategoryIcon = when (category.type) {
        PluginType.SYNC -> Icons.Default.Sync
        PluginType.GENERAL -> Icons.Default.Extension
        else -> Icons.Default.Settings
    }
    val iconPainter =
        if (composeIcon != null) rememberVectorPainter(composeIcon)
        else if (plugin?.menuIcon != null && plugin.menuIcon != -1) painterResource(plugin.menuIcon)
        else rememberVectorPainter(defaultCategoryIcon)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 16.dp)
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
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CategoryDetailScreen(
    category: DrawerCategory,
    isSimpleMode: Boolean,
    pluginStateVersion: Int,
    onNavigateBack: () -> Unit,
    onPluginClick: (PluginBase) -> Unit,
    onPluginEnableToggle: (PluginBase, PluginType, Boolean) -> Unit,
    onPluginPreferencesClick: (PluginBase) -> Unit,
) {
    val enabledIndex = category.plugins
        .indexOfFirst { it.isEnabled(category.type) }
        .coerceAtLeast(0)
    val listState = remember(category.type) {
        LazyListState(firstVisibleItemIndex = enabledIndex)
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(category.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        key(pluginStateVersion) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(category.plugins, key = { it.javaClass.simpleName }) { plugin ->
                    val pluginEnabled = plugin.isEnabled(category.type)
                    val hasPreferences = plugin.preferencesId != PluginDescription.PREFERENCE_NONE
                    val showPrefs = hasPreferences && pluginEnabled && (!isSimpleMode || plugin.pluginDescription.preferencesVisibleInSimpleMode)

                    val canToggle = !plugin.pluginDescription.alwaysEnabled &&
                        (category.isMultiSelect || !pluginEnabled)

                    ConfigPluginItem(
                        plugin = plugin,
                        isEnabled = pluginEnabled,
                        canToggle = canToggle,
                        showPreferences = showPrefs,
                        onPluginClick = { onPluginClick(plugin) },
                        onEnableToggle = { enabled ->
                            onPluginEnableToggle(plugin, category.type, enabled)
                        },
                        onPreferencesClick = { onPluginPreferencesClick(plugin) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigPluginItem(
    plugin: PluginBase,
    isEnabled: Boolean,
    canToggle: Boolean,
    showPreferences: Boolean,
    onPluginClick: () -> Unit,
    onEnableToggle: (Boolean) -> Unit,
    onPreferencesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = MaterialTheme.colorScheme.primary
    val disabledAlpha = 0.38f

    val composeIcon = plugin.pluginDescription.icon
    val iconPainter = if (composeIcon != null) {
        rememberVectorPainter(composeIcon)
    } else {
        val iconRes = if (plugin.menuIcon != -1) plugin.menuIcon else app.aaps.core.ui.R.drawable.ic_settings
        painterResource(id = iconRes)
    }

    val containerColor = if (isEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.surface

    ListItem(
        headlineContent = {
            Text(
                text = plugin.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = plugin.description?.let { desc ->
            {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isEnabled) iconColor.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = if (isEnabled) iconColor
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPreferences) {
                    IconButton(onClick = onPreferencesClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = if (canToggle) {
                        { onEnableToggle(it) }
                    } else null,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (isEnabled) Modifier.clickable { onPluginClick() } else Modifier)
    )
}
