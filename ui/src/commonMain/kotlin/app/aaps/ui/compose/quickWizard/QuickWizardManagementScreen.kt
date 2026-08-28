package app.aaps.ui.compose.quickWizard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.MasterOfflineBanner
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.masterEditingEnabled
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.components.CarouselReorderConfig
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.components.ManagementCarousel
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import kotlinx.coroutines.launch

/**
 * Screen for managing QuickWizard entries.
 * Displays entries in a carousel with editor below and action buttons.
 *
 * @param viewModel ViewModel managing QuickWizard state and operations
 * @param onNavigateBack Callback to navigate back
 * @param onExecuteClick Callback to execute the current QuickWizard entry
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickWizardManagementScreen(
    viewModel: QuickWizardManagementViewModel,
    initialMode: ScreenMode = ScreenMode.EDIT,
    onNavigateBack: () -> Unit = {},
    onRequestEditMode: () -> Unit = {},
    onExecuteClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    // On a client whose master is unreachable, force VIEW-ONLY so QuickWizard (synced config) edits can't be
    // made while they couldn't sync. DERIVED from current state (no setScreenMode side-effect) → deterministic
    // at startup and flips the instant reachability changes. Master is never gated. Both edits AND the
    // master-bound Execute FAB are gated by this on a client.
    val editingEnabled = masterEditingEnabled()
    val isPlayMode = uiState.screenMode == ScreenMode.PLAY || !editingEnabled

    // Set initial screen mode
    LaunchedEffect(initialMode) {
        viewModel.setScreenMode(initialMode)
    }

    // State to trigger pager scroll (set by navigation event, consumed by pager)
    var scrollToPage by remember { mutableStateOf<Int?>(null) }

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is QuickWizardManagementViewModel.SideEffect.ScrollToEntry -> {
                    scrollToPage = effect.index
                }
            }
        }
    }

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var pendingPageChange by remember { mutableIntStateOf(-1) }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        val entry = uiState.entries.getOrNull(uiState.selectedIndex)
        OkCancelDialog(
            title = stringResource(CoreUiStrings.removerecord),
            message = "${stringResource(CoreUiStrings.delete)} ${entry?.buttonText() ?: ""}?",
            onConfirm = {
                viewModel.deleteCurrentEntry()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Unsaved changes confirmation dialog
    if (showUnsavedChangesDialog) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.unsaved_changes),
            message = stringResource(UiStrings.save_changes_question),
            onConfirm = {
                viewModel.saveCurrentEntry()
                showUnsavedChangesDialog = false
                if (pendingPageChange >= 0) {
                    scrollToPage = pendingPageChange
                    pendingPageChange = -1
                } else {
                    onNavigateBack()
                }
            },
            onDismiss = {
                showUnsavedChangesDialog = false
                if (pendingPageChange >= 0) {
                    // User chose not to save, proceed with page change anyway
                    // Reset unsaved changes flag and navigate
                    viewModel.selectEntry(pendingPageChange)
                    scrollToPage = pendingPageChange
                    pendingPageChange = -1
                } else {
                    onNavigateBack()
                }
            }
        )
    }

    // Non-null working order == reorder ("sort") mode is on.
    val reorderOrder by viewModel.reorderOrder.collectAsStateWithLifecycle()
    val isReorderMode = reorderOrder != null
    val canEnterReorder = !isPlayMode && editingEnabled && viewModel.canReorder()
    val scope = rememberCoroutineScope()
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Back leaves sort mode rather than the screen, so a reshuffle isn't silently discarded. Takes
    // precedence over the unsaved-changes handler below: sorting does not touch the editor.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isReorderMode,
        onBackCompleted = { viewModel.cancelReorder() }
    )

    // Back button handler - check for unsaved changes (skip in PLAY mode)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !isReorderMode,
        onBackCompleted = {
            if (!isPlayMode && viewModel.hasUnsavedChanges()) {
                showUnsavedChangesDialog = true
            } else {
                onNavigateBack()
            }
        }
    )

    // The view model outlives the screen, so an uncommitted order would otherwise resurrect the mode.
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelReorder() }
    }

    // Track current page for floating toolbar actions
    var currentPage by remember { mutableIntStateOf(0) }

    AapsTheme {
        Scaffold(
            topBar = {
                if (isReorderMode) {
                    AapsTopAppBar(
                        title = { Text(stringResource(CoreUiStrings.reorder)) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.cancelReorder() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(CoreUiStrings.cancel)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { scope.launch { viewModel.commitReorder() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(CoreUiStrings.ok)
                                )
                            }
                        }
                    )
                } else {
                    AapsTopAppBar(
                        title = { Text((stringResourceOrNull(ElementType.QUICK_WIZARD_MANAGEMENT.label()) ?: "")) },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (!isPlayMode && viewModel.hasUnsavedChanges()) {
                                    showUnsavedChangesDialog = true
                                } else {
                                    onNavigateBack()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(CoreUiStrings.back)
                                )
                            }
                        },
                        actions = {
                            if (isPlayMode) {
                                // Edit mode button (shown in PLAY mode)
                                IconButton(onClick = onRequestEditMode, enabled = editingEnabled) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(CoreUiStrings.switch_to_edit)
                                    )
                                }
                            } else {
                                // Save button (shown when editor has unsaved changes in EDIT mode)
                                if (uiState.entries.isNotEmpty() && uiState.hasUnsavedChanges) {
                                    IconButton(onClick = {
                                        focusManager.clearFocus()
                                        viewModel.saveCurrentEntry()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = stringResource(CoreUiStrings.save),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                // Menu entry as well as the long-press: the long-press is
                                // undiscoverable on its own, and this is the only route a screen
                                // reader can take.
                                Box {
                                    IconButton(onClick = { showOverflowMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(CoreUiStrings.more_options)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(CoreUiStrings.reorder)) },
                                            enabled = canEnterReorder,
                                            onClick = {
                                                showOverflowMenu = false
                                                focusManager.clearFocus()
                                                viewModel.enterReorderMode()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)) {
                MasterOfflineBanner(editingEnabled = editingEnabled)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clearFocusOnTap(focusManager)
                ) {
                    ContentContainer(
                        isLoading = uiState.isLoading,
                        isEmpty = uiState.entries.isEmpty()
                    ) {
                        val cardCount = uiState.entries.size

                        // Use saved card index from ViewModel (survives rotation via @Singleton)
                        val pagerState = rememberPagerState(
                            initialPage = uiState.currentCardIndex.coerceIn(0, (cardCount - 1).coerceAtLeast(0)),
                            pageCount = { cardCount }
                        )

                        // Handle scroll to page request (e.g., after adding new entry)
                        LaunchedEffect(scrollToPage, cardCount) {
                            scrollToPage?.let { page ->
                                if (page < cardCount && !isReorderMode) {
                                    pagerState.animateScrollToPage(page)
                                    scrollToPage = null
                                }
                            }
                        }

                        // Settle on the card that was moved once sorting is over. Keyed on the stored
                        // index rather than the mode, so toggling the mode never starts or cancels a
                        // scroll of its own.
                        LaunchedEffect(uiState.currentCardIndex) {
                            if (!isReorderMode && pagerState.currentPage != uiState.currentCardIndex) {
                                pagerState.animateScrollToPage(uiState.currentCardIndex.coerceIn(0, (cardCount - 1).coerceAtLeast(0)))
                            }
                        }

                        // Update selected entry when pager changes
                        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                            // A reorder step moves the carousel too. It changes no editor field, so
                            // the unsaved-changes prompt below must not fire — it would interrupt the
                            // sort with a modal and scroll the moved card back.
                            if (isReorderMode) {
                                currentPage = pagerState.currentPage
                                return@LaunchedEffect
                            }
                            if (!pagerState.isScrollInProgress) {
                                val newPage = pagerState.currentPage
                                // Check if we have unsaved changes before switching
                                if (newPage != currentPage && viewModel.hasUnsavedChanges()) {
                                    // Show dialog and save pending page change
                                    pendingPageChange = newPage
                                    showUnsavedChangesDialog = true
                                    // Scroll back to current page
                                    pagerState.scrollToPage(currentPage)
                                } else {
                                    viewModel.updateCurrentCardIndex(newPage)
                                    viewModel.selectEntry(newPage)
                                    currentPage = newPage
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // QuickWizard Entry Carousel
                            val workingOrder = reorderOrder
                            val moveEarlierLabel = stringResource(CoreUiStrings.carousel_move_earlier)
                            val moveLaterLabel = stringResource(CoreUiStrings.carousel_move_later)

                            ManagementCarousel(
                                state = pagerState,
                                // Swiping stays available while sorting even with unsaved changes:
                                // it is how you reach the card you want to move, and it cannot lose
                                // an edit because the veto above is skipped in this mode.
                                userScrollEnabled = isReorderMode || !viewModel.hasUnsavedChanges(),
                                reorder = workingOrder?.let { order ->
                                    CarouselReorderConfig(
                                        isActive = true,
                                        itemCount = order.size,
                                        onMove = viewModel::moveReorderItem,
                                        moveEarlierLabel = moveEarlierLabel,
                                        moveLaterLabel = moveLaterLabel,
                                        positionLabel = { page ->
                                            viewModel.rh.gs(CoreUiStrings.carousel_position, page + 1, order.size)
                                        },
                                        positionDescription = { page ->
                                            viewModel.rh.gs(CoreUiStrings.carousel_position_description, page + 1, order.size)
                                        }
                                    )
                                }
                            ) { itemState ->
                                // While sorting, the card at a position shows the entry the working
                                // order puts there — not the one at that index in the stored list.
                                val dataIndex = workingOrder?.getOrNull(itemState.page) ?: itemState.page
                                val entry = uiState.entries.getOrNull(dataIndex)
                                if (entry != null) {
                                    QuickWizardCarouselCard(
                                        entry = entry,
                                        isSelected = itemState.isSelected
                                    )
                                }
                            }

                            // QuickWizard Editor (hidden in PLAY mode)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Editor hidden while sorting: Save has given up its toolbar slot to
                                // Done, so an edit made there would have nowhere to go.
                                if (!isPlayMode && !isReorderMode && uiState.selectedIndex >= 0 && uiState.selectedIndex < uiState.entries.size) {
                                    QuickWizardEditor(
                                        mode = uiState.editorMode,
                                        buttonText = uiState.editorButtonText,
                                        insulin = uiState.editorInsulin,
                                        carbs = uiState.editorCarbs,
                                        carbTime = uiState.editorCarbTime,
                                        validFrom = uiState.editorValidFrom,
                                        validTo = uiState.editorValidTo,
                                        useBG = uiState.editorUseBG,
                                        useCOB = uiState.editorUseCOB,
                                        useIOB = uiState.editorUseIOB,
                                        usePositiveIOBOnly = uiState.editorUsePositiveIOBOnly,
                                        useTrend = uiState.editorUseTrend,
                                        useSuperBolus = uiState.editorUseSuperBolus,
                                        useTempTarget = uiState.editorUseTempTarget,
                                        useAlarm = uiState.editorUseAlarm,
                                        percentage = uiState.editorPercentage,
                                        devicePhone = uiState.editorDevicePhone,
                                        deviceWatch = uiState.editorDeviceWatch,
                                        useEcarbs = uiState.editorUseEcarbs,
                                        time = uiState.editorTime,
                                        duration = uiState.editorDuration,
                                        carbs2 = uiState.editorCarbs2,
                                        showSuperBolusOption = uiState.showSuperBolusOption,
                                        showWearOptions = uiState.showWearOptions,
                                        maxCarbs = viewModel.getMaxCarbs(),
                                        maxInsulin = viewModel.getMaxInsulin(),
                                        rh = viewModel.rh,
                                        onModeChange = viewModel::updateMode,
                                        onButtonTextChange = viewModel::updateButtonText,
                                        onInsulinChange = viewModel::updateInsulin,
                                        onCarbsChange = viewModel::updateCarbs,
                                        onCarbTimeChange = viewModel::updateCarbTime,
                                        onValidFromChange = viewModel::updateValidFrom,
                                        onValidToChange = viewModel::updateValidTo,
                                        onUseBGChange = viewModel::updateUseBG,
                                        onUseCOBChange = viewModel::updateUseCOB,
                                        onUseIOBChange = viewModel::updateUseIOB,
                                        onUsePositiveIOBOnlyChange = viewModel::updateUsePositiveIOBOnly,
                                        onUseTrendChange = viewModel::updateUseTrend,
                                        onUseSuperBolusChange = viewModel::updateUseSuperBolus,
                                        onUseTempTargetChange = viewModel::updateUseTempTarget,
                                        onUseAlarmChange = viewModel::updateUseAlarm,
                                        onPercentageChange = viewModel::updatePercentage,
                                        onDevicePhoneChange = viewModel::updateDevicePhone,
                                        onDeviceWatchChange = viewModel::updateDeviceWatch,
                                        onUseEcarbsChange = viewModel::updateUseEcarbs,
                                        onTimeChange = viewModel::updateTime,
                                        onDurationChange = viewModel::updateDuration,
                                        onCarbs2Change = viewModel::updateCarbs2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                // Extra space for floating toolbar
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }

                    // Floating Toolbar with FAB (M3 style) — both are absolutely positioned over the
                    // cards, so they are hidden while sorting rather than left floating over the sort controls.
                    if (!isReorderMode) Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Floating Toolbar — hidden in PLAY mode
                        if (!isPlayMode) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 6.dp,
                                tonalElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Add button
                                    IconButton(onClick = { viewModel.addNewEntry() }) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = stringResource(CoreUiStrings.add)
                                        )
                                    }
                                    // Clone button
                                    IconButton(
                                        onClick = { viewModel.cloneCurrentEntry() },
                                        enabled = uiState.entries.isNotEmpty()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = "Clone",
                                            tint = if (uiState.entries.isNotEmpty())
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                    // Delete button
                                    IconButton(
                                        onClick = { showDeleteDialog = true },
                                        enabled = uiState.entries.isNotEmpty()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(UiStrings.remove_label),
                                            tint = if (uiState.entries.isNotEmpty())
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                }
                            }
                        }

                        // FAB for Execute/Play. Hidden on a client whose master is unreachable — executing a
                        // QuickWizard is a (remote) action that couldn't be delivered.
                        if (editingEnabled && uiState.entries.isNotEmpty()) {
                            AapsFab(
                                onClick = { onExecuteClick(uiState.selectedGuid) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Execute"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
