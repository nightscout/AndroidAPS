package app.aaps.plugins.source.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.directionToIcon
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.Ns
import app.aaps.ui.compose.components.ContentContainer
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BgSourceScreen(
    viewModel: BgSourceViewModel,
    title: String,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit = { },
    onSettings: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogMessage by remember { mutableStateOf("") }

    // Update toolbar configuration whenever state changes
    LaunchedEffect(uiState.isRemovingMode, uiState.selectedItems.size) {
        setToolbarConfig(
            SelectableListToolbar(
                isRemovingMode = uiState.isRemovingMode,
                selectedCount = uiState.selectedItems.size,
                onExitRemovingMode = { viewModel.exitSelectionMode() },
                onNavigateBack = onNavigateBack,
                onDelete = {
                    if (uiState.selectedItems.isNotEmpty()) {
                        deleteDialogMessage = viewModel.getDeleteConfirmationMessage()
                        showDeleteDialog = true
                    }
                },
                rh = viewModel.rh,
                title = title,
                onSettings = onSettings
            )
        )
    }

    // Delete confirmation dialog
    val removeRecordTitle = stringResource(app.aaps.core.ui.R.string.removerecord)
    if (showDeleteDialog) {
        OkCancelDialog(
            title = removeRecordTitle,
            message = deleteDialogMessage,
            onConfirm = {
                viewModel.deleteSelected()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    AapsTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ContentContainer(
                isLoading = uiState.isLoading,
                isEmpty = uiState.glucoseValues.isEmpty()
            ) {
                val haptic = LocalHapticFeedback.current

                BgSourceLazyColumn(
                    items = uiState.glucoseValues,
                    duplicateIds = uiState.duplicateIds,
                    dateUtil = viewModel.dateUtil,
                    rh = viewModel.rh,
                    onLoadMore = { viewModel.loadMoreData() },
                    itemContent = { gv, isDuplicate ->
                        GlucoseValueItem(
                            glucoseValue = gv,
                            isRemovingMode = uiState.isRemovingMode,
                            isSelected = gv in uiState.selectedItems,
                            isDuplicate = isDuplicate,
                            onClick = {
                                if (uiState.isRemovingMode && gv.isValid) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelection(gv)
                                }
                            },
                            onLongPress = {
                                if (gv.isValid && !uiState.isRemovingMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.enterSelectionMode(gv)
                                }
                            },
                            dateUtil = viewModel.dateUtil,
                            formatGlucoseValue = viewModel::formatGlucoseValue
                        )
                    }
                )
            }

            // Error display
            AapsSnackbarHost(
                message = uiState.snackbarMessage,
                onDismiss = { viewModel.clearSnackbar() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * LazyColumn with sticky date headers and infinite scroll support.
 * Loads more data when scrolling near the bottom of the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BgSourceLazyColumn(
    items: List<GV>,
    duplicateIds: Set<Long>,
    dateUtil: DateUtil,
    rh: ResourceHelper,
    onLoadMore: () -> Unit,
    itemContent: @Composable (GV, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Group items by day
    val groupedByDay by remember(items) {
        derivedStateOf {
            items.groupBy { gv ->
                dateUtil.dateString(gv.timestamp)
            }
        }
    }

    // Detect when scrolled near bottom and load more data
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItemsCount - 3 && totalItemsCount > 0
        }
            .distinctUntilChanged()
            .collect { isNearBottom ->
                if (isNearBottom) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
    ) {
        groupedByDay.forEach { (dateString, itemsForDay) ->
            stickyHeader(key = dateString) {
                Text(
                    text = dateUtil.dateStringRelative(itemsForDay.first().timestamp, rh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = AapsSpacing.medium, vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(
                items = itemsForDay,
                key = { it.id }
            ) { gv ->
                Box(modifier = Modifier.animateItem()) {
                    itemContent(gv, gv.id in duplicateIds)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlucoseValueItem(
    glucoseValue: GV,
    isRemovingMode: Boolean,
    isSelected: Boolean,
    isDuplicate: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    dateUtil: DateUtil,
    formatGlucoseValue: (Double) -> String
) {
    val duplicateColor = AapsTheme.generalColors.invalidatedRecord
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isDuplicate -> duplicateColor.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraSmall)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.medium, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time
            Text(
                text = dateUtil.timeStringWithSeconds(glucoseValue.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )

            // Glucose value
            Text(
                text = formatGlucoseValue(glucoseValue.value),
                modifier = Modifier.padding(start = AapsSpacing.large),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            // Trend arrow
            Icon(
                painter = painterResource(id = glucoseValue.trendArrow.directionToIcon()),
                contentDescription = glucoseValue.trendArrow.name,
                modifier = Modifier
                    .padding(start = AapsSpacing.small)
                    .size(20.dp)
            )

            // Source sensor
            Text(
                text = glucoseValue.sourceSensor.text,
                modifier = Modifier.padding(start = AapsSpacing.large),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            // Spacer to push badges to the right
            Box(modifier = Modifier.weight(1f))

            // NS badge
            if (glucoseValue.ids.nightscoutId != null) {
                Icon(
                    imageVector = Ns,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.ns),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp)
                )
            }

            // Invalid badge (shown if record was invalidated)
            if (!glucoseValue.isValid) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.invalid),
                    modifier = Modifier
                        .size(21.dp)
                        .padding(start = 5.dp),
                    tint = AapsTheme.generalColors.invalidatedRecord
                )
            }

            // Checkbox for removal mode
            if (isRemovingMode && glucoseValue.isValid) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(AapsSpacing.xxLarge)
                )
            }
        }
    }
}
