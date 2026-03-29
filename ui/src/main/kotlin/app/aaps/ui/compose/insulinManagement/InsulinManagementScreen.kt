package app.aaps.ui.compose.insulinManagement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.graph.InsulinGraphCompose
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.icons.IcPluginInsulin
import app.aaps.core.ui.compose.insulin.ConcentrationDropdown
import app.aaps.ui.R
import kotlin.math.absoluteValue
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

/**
 * Main insulin management screen with carousel and editor.
 * Follows the same carousel pattern as TempTargetManagementScreen and ProfileManagementScreen.
 *
 * @param viewModel ViewModel managing state and business logic
 * @param initialMode Initial screen mode (PLAY for activation, EDIT for management)
 * @param onNavigateBack Lambda to handle back navigation
 * @param onRequestEditMode Lambda to request switching to EDIT mode (triggers protection check)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinManagementScreen(
    viewModel: InsulinManagementViewModel,
    initialMode: ScreenMode = ScreenMode.EDIT,
    onNavigateBack: () -> Unit,
    onRequestEditMode: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val isPlayMode = uiState.screenMode == ScreenMode.PLAY

    // Set initial mode
    LaunchedEffect(initialMode) {
        viewModel.setScreenMode(initialMode)
    }

    val suffixDisplay = viewModel.insulinManager.buildDisplaySuffix(
        nickname = uiState.editorNickname.trim(),
        peak = uiState.editorPeakMinutes,
        dia = uiState.editorDiaHours,
        concentration = uiState.editorConcentration.value,
        excludeIndex = uiState.currentCardIndex
    )

    // Derived state (reactive — recomputed when uiState changes)
    val stored = uiState.insulins.getOrNull(uiState.currentCardIndex)
    val hasUnsavedChanges = viewModel.hasUnsavedChanges()
    val isCurrentActive = stored?.insulinLabel == uiState.activeInsulinLabel
    val canDelete = uiState.insulins.size > 1 && !isCurrentActive

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Scroll-to-page state for programmatic scrolling
    var scrollToPage by remember { mutableStateOf<Int?>(null) }

    // Lifecycle-aware refresh
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Collect side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is InsulinManagementViewModel.SideEffect.ScrollToInsulin -> {
                    scrollToPage = effect.index
                }

                is InsulinManagementViewModel.SideEffect.NavigateBack   -> {
                    onNavigateBack()
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        val insulinName = uiState.insulins.getOrNull(uiState.currentCardIndex)?.insulinLabel ?: ""
        OkCancelDialog(
            title = stringResource(CoreUiR.string.removerecord),
            message = insulinName,
            onConfirm = {
                viewModel.deleteCurrentInsulin()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Activate confirmation dialog
    uiState.activationMessage?.let { message ->
        OkCancelDialog(
            title = stringResource(CoreUiR.string.activate_insulin),
            message = message,
            icon = IcPluginInsulin,
            onConfirm = { viewModel.executeActivation() },
            onDismiss = { viewModel.dismissActivation() }
        )
    }

    // Unsaved changes dialog
    if (uiState.pendingNavigation != null) {
        UnsavedChangesDialog(
            onSave = { viewModel.saveAndProceed() },
            onDiscard = { viewModel.discardAndProceed() },
            onCancel = { viewModel.dismissPendingNavigation() }
        )
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(CoreUiR.string.insulin_label)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.requestBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (!isPlayMode) {
                        IconButton(
                            onClick = { viewModel.saveCurrentInsulin() },
                            enabled = hasUnsavedChanges
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        val insulins = uiState.insulins
        val cardCount = insulins.size

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clearFocusOnTap(focusManager)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (cardCount > 0) {
                    // Carousel
                    val pagerState = rememberPagerState(
                        initialPage = uiState.currentCardIndex.coerceIn(0, (cardCount - 1).coerceAtLeast(0)),
                        pageCount = { cardCount }
                    )

                    // Sync pager to ViewModel
                    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                        if (!pagerState.isScrollInProgress) {
                            viewModel.updateCurrentCardIndex(pagerState.currentPage)
                        }
                    }

                    // Snap pager back when pending navigation is dismissed (Cancel)
                    // pendingNavigation becoming null means dialog was dismissed without switching
                    LaunchedEffect(uiState.pendingNavigation) {
                        if (uiState.pendingNavigation == null && pagerState.currentPage != uiState.currentCardIndex) {
                            pagerState.animateScrollToPage(uiState.currentCardIndex)
                        }
                    }

                    // Handle programmatic scroll
                    LaunchedEffect(scrollToPage, cardCount) {
                        scrollToPage?.let { page ->
                            if (page < cardCount) {
                                pagerState.animateScrollToPage(page)
                                scrollToPage = null
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentPadding = PaddingValues(horizontal = 64.dp),
                        pageSpacing = 16.dp
                    ) { page ->
                        val iCfg = insulins[page]
                        val isActive = iCfg.insulinLabel == uiState.activeInsulinLabel
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                        InsulinCarouselCard(
                            iCfg = iCfg,
                            isActive = isActive,
                            isSelected = pagerState.currentPage == page,
                            modifier = Modifier.graphicsLayer {
                                scaleX = lerp(0.85f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                                scaleY = lerp(0.85f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                                alpha = lerp(0.5f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                            }
                        )
                    }

                    // Page indicator dots
                    PageIndicatorDots(
                        pageCount = cardCount,
                        currentPage = pagerState.currentPage
                    )

                    // Editor section (only in EDIT mode)
                    if (!isPlayMode) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            val editorEnabled = !isCurrentActive

                            // Name field
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = uiState.editorNickname,
                                    onValueChange = { viewModel.updateEditorNickname(it) },
                                    label = { Text(stringResource(CoreUiR.string.insulin_nickname_label)) },
                                    singleLine = true,
                                    enabled = editorEnabled,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = suffixDisplay,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (viewModel.concentrationEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))

                                // Concentration dropdown
                                ConcentrationDropdown(
                                    selected = uiState.editorConcentration,
                                    concentrations = viewModel.concentrationList(),
                                    onSelect = { viewModel.updateEditorConcentration(it) },
                                    enabled = editorEnabled
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Peak editor
                            NumberInputRow(
                                labelResId = CoreUiR.string.peak_label,
                                value = uiState.editorPeakMinutes.toDouble(),
                                onValueChange = { viewModel.updateEditorPeak(it.toInt()) },
                                valueRange = viewModel.peakRange(),
                                step = 1.0,
                                unitLabelResId = KeysR.string.units_min,
                                enabled = editorEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // "Load peak from" preset chips
                            if (editorEnabled) {
                                PeakPresetChips(
                                    presets = viewModel.presetList(),
                                    onPresetClick = { viewModel.loadPeakFromPreset(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // DIA editor
                            NumberInputRow(
                                labelResId = CoreUiR.string.dia_label,
                                value = uiState.editorDiaHours,
                                onValueChange = { viewModel.updateEditorDia(it) },
                                valueRange = viewModel.diaRange(),
                                step = 0.1,
                                decimalPlaces = 1,
                                unitLabelResId = KeysR.string.units_hours,
                                enabled = editorEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Insulin activity graph
                            val editorICfg = viewModel.buildEditorICfg()
                            InsulinGraphCompose(
                                iCfg = editorICfg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            )

                            // Bottom spacing for floating toolbar
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    } else {
                        // In PLAY mode, fill the remaining space
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Floating toolbar + FAB at bottom
            if (cardCount > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isPlayMode) {
                        // Toolbar pill (only in EDIT mode)
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            tonalElevation = 3.dp,
                            shadowElevation = 3.dp
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                                // Add
                                IconButton(onClick = { viewModel.addNewInsulin() }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.a11y_add_new_insulin)
                                    )
                                }
                                // Delete
                                IconButton(
                                    onClick = { showDeleteDialog = true },
                                    enabled = canDelete
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.a11y_delete_current_insulin)
                                    )
                                }
                            }
                        }
                    }
                    val currentIcfgConcentration = uiState.insulins.getOrNull(uiState.currentCardIndex)?.concentration ?: 0.0
                    if (currentIcfgConcentration == uiState.activeConcentration)
                    // Activate FAB
                        AapsFab(
                            onClick = {
                                if (!hasUnsavedChanges) {
                                    viewModel.prepareActivation()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        }
                }
            }

            // Snackbar
            AapsSnackbarHost(
                message = uiState.snackbarMessage,
                onDismiss = { viewModel.clearSnackbar() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// --- Unsaved Changes Dialog ---

@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(CoreUiR.string.unsaved_changes)) },
        text = { Text(stringResource(CoreUiR.string.unsaved_changes_message)) },
        confirmButton = {
            FilledTonalButton(onClick = onSave) {
                Text(stringResource(CoreUiR.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(CoreUiR.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDiscard) {
                    Text(stringResource(CoreUiR.string.discard))
                }
            }
        }
    )
}

// --- "Load peak from" preset chips ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeakPresetChips(
    presets: List<InsulinType>,
    onPresetClick: (InsulinType) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        val parts = presets.map { preset ->
            Triple(stringResource(preset.label), stringResource(preset.comment), stringResource(CoreUiR.string.format_mins, preset.iCfg.peak))
        }
        val message = parts.joinToString("\n\n") { (label, comment, peak) ->
            "<b>$label</b>\n$comment — $peak"
        }
        OkDialog(
            title = stringResource(R.string.load_peak_from),
            message = message,
            icon = IcPluginInsulin,
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.load_peak_from),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { showInfo = true },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                AssistChip(
                    onClick = { onPresetClick(preset) },
                    label = { Text(stringResource(preset.label)) }
                )
            }
        }
    }
}

// --- Local PageIndicatorDots ---

@Composable
private fun PageIndicatorDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(if (isSelected) 24.dp else 8.dp)
                    .height(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = if (isSelected) selectedColor else unselectedColor,
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }
    }
}

// --- Concentration Dropdown ---

