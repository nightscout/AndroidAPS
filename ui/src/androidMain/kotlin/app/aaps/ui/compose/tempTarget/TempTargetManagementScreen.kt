package app.aaps.ui.compose.tempTarget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import app.aaps.core.ui.compose.masterEditingEnabled
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.components.CarouselReorderConfig
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.components.ManagementCarousel
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Screen for managing temporary target presets and activating TTs.
 * Displays TT presets in a carousel with editor below and action buttons.
 *
 * @param viewModel ViewModel managing TT state and operations
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TempTargetManagementScreen(
    viewModel: TempTargetManagementViewModel,
    initialMode: ScreenMode = ScreenMode.EDIT,
    onNavigateBack: () -> Unit = {},
    onRequestEditMode: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    // On a client whose master is unreachable, force VIEW-ONLY so preset (synced config) edits can't be made
    // while they couldn't sync. DERIVED from current state (no setScreenMode side-effect) → deterministic at
    // startup and flips the instant reachability changes. Master is never gated. Both preset edits AND the
    // master-bound action FABs (Activate / Cancel) are gated by this on a client.
    val editingEnabled = masterEditingEnabled()
    val isPlayMode = uiState.screenMode == ScreenMode.PLAY || !editingEnabled

    // Set initial screen mode
    LaunchedEffect(initialMode) {
        viewModel.setScreenMode(initialMode)
    }

    // Refresh runtime data when screen resumes (handles preference changes, active TT updates)
    // Uses refreshData() instead of loadData() to preserve editor fields during rotation
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // State to trigger pager scroll (set by navigation event, consumed by pager)
    var scrollToPage by remember { mutableStateOf<Int?>(null) }

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is TempTargetManagementViewModel.SideEffect.ScrollToPreset -> {
                    scrollToPage = effect.index
                }
            }
        }
    }

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteDialog && uiState.selectedPreset != null) {
        val presetName = uiState.selectedPreset!!.getDisplayName()
        OkCancelDialog(
            title = viewModel.rh.gs(CoreUiStrings.removerecord),
            message = "${viewModel.rh.gs(CoreUiStrings.delete)} $presetName?",
            onConfirm = {
                viewModel.deleteCurrentPreset()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Date picker modal
    if (showDatePicker) {
        DatePickerModal(
            onDateSelected = { selectedMillis ->
                selectedMillis?.let {
                    val currentCalendar = Calendar.getInstance().apply {
                        timeInMillis = uiState.eventTime
                    }
                    val newCalendar = Calendar.getInstance().apply {
                        timeInMillis = it
                        set(Calendar.HOUR_OF_DAY, currentCalendar.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, currentCalendar.get(Calendar.MINUTE))
                    }
                    viewModel.updateEventTime(newCalendar.timeInMillis)
                }
            },
            onDismiss = { showDatePicker = false },
            initialDateMillis = uiState.eventTime
        )
    }

    // Time picker modal
    if (showTimePicker) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = uiState.eventTime
        }

        TimePickerModal(
            onTimeSelected = { hour, minute ->
                val newCalendar = Calendar.getInstance().apply {
                    timeInMillis = uiState.eventTime
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                }
                viewModel.updateEventTime(newCalendar.timeInMillis)
            },
            onDismiss = { showTimePicker = false },
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
    }

    // Track current page for floating toolbar actions
    var currentPage by remember { mutableIntStateOf(0) }

    // Non-null working order == reorder ("sort") mode is on.
    val reorderOrder by viewModel.reorderOrder.collectAsStateWithLifecycle()
    val isReorderMode = reorderOrder != null
    // Deliberately NOT gated on unsaved editor changes: the editor's values live in the view model,
    // so hiding it while sorting does not discard them — Save comes back with the edits intact.
    val canEnterReorder = !isPlayMode && editingEnabled && viewModel.canReorder()
    val scope = rememberCoroutineScope()
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Back leaves sort mode rather than the screen, so a reshuffle isn't silently discarded.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isReorderMode,
        onBackCompleted = { viewModel.cancelReorder() }
    )

    // The view model outlives the screen, so an uncommitted order would otherwise survive until the
    // screen is reopened — dropping the user straight back into sort mode with a stale order.
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelReorder() }
    }

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
                        title = { Text((stringResourceOrNull(ElementType.TEMP_TARGET_MANAGEMENT.label()) ?: "")) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
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
                                if (uiState.selectedPreset != null && viewModel.hasUnsavedChanges()) {
                                    IconButton(onClick = {
                                        focusManager.clearFocus()
                                        viewModel.saveCurrentPreset()
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                MasterOfflineBanner(editingEnabled = editingEnabled)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clearFocusOnTap(focusManager)
                ) {
                    ContentContainer(
                        isLoading = uiState.isLoading,
                        isEmpty = uiState.presets.isEmpty()
                    ) {
                        // Standalone active card only when active TT doesn't match any preset
                        val hasStandaloneActiveTT = uiState.activeTT != null && uiState.activePresetIndex == null
                        val cardCount = if (hasStandaloneActiveTT) {
                            uiState.presets.size + 1
                        } else {
                            uiState.presets.size
                        }

                        // Use saved card index from ViewModel (survives rotation via @Singleton)
                        val pagerState = rememberPagerState(
                            initialPage = uiState.currentCardIndex.coerceIn(0, (cardCount - 1).coerceAtLeast(0)),
                            pageCount = { cardCount }
                        )

                        // Handle scroll to page request (e.g., after adding new preset)
                        // Depends on cardCount so it retries when pager updates with new page count
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

                        // Update selected preset when pager changes
                        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                            currentPage = pagerState.currentPage
                            // A reorder step moves the carousel too; treating that as a selection
                            // would reload the editor from whichever preset slid past.
                            if (isReorderMode) return@LaunchedEffect
                            if (!pagerState.isScrollInProgress) {
                                viewModel.updateCurrentCardIndex(pagerState.currentPage)
                                val presetIndex = if (hasStandaloneActiveTT && pagerState.currentPage > 0) {
                                    pagerState.currentPage - 1
                                } else if (!hasStandaloneActiveTT) {
                                    pagerState.currentPage
                                } else {
                                    null // Standalone active TT card selected
                                }

                                if (presetIndex != null) viewModel.selectPreset(presetIndex)
                                else viewModel.selectActiveTT()
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // TT Preset Carousel
                            //
                            // Carousel pages are not preset indices: page 0 is the standalone
                            // active-TT card when there is one, and that card is not a preset, so it
                            // can never be moved or displaced.
                            val presetOffset = if (hasStandaloneActiveTT) 1 else 0
                            val workingOrder = reorderOrder
                            val moveEarlierLabel = stringResource(CoreUiStrings.carousel_move_earlier)
                            val moveLaterLabel = stringResource(CoreUiStrings.carousel_move_later)
                            val reorderLabel = stringResource(CoreUiStrings.reorder)
                            val selectLabel = stringResource(CoreUiStrings.carousel_show_card)

                            ManagementCarousel(
                                state = pagerState,
                                reorder = workingOrder?.let { order ->
                                    CarouselReorderConfig(
                                        isActive = true,
                                        itemCount = order.size + presetOffset,
                                        canMove = { page ->
                                            viewModel.isReorderPositionMovable(page - presetOffset)
                                        },
                                        onMove = { fromPage, toPage ->
                                            viewModel.moveReorderItem(fromPage - presetOffset, toPage - presetOffset)
                                        },
                                        moveEarlierLabel = moveEarlierLabel,
                                        moveLaterLabel = moveLaterLabel,
                                        positionLabel = { page ->
                                            viewModel.rh.gs(
                                                CoreUiStrings.carousel_position,
                                                page + 1,
                                                order.size + presetOffset
                                            )
                                        },
                                        positionDescription = { page ->
                                            viewModel.rh.gs(
                                                CoreUiStrings.carousel_position_description,
                                                page + 1,
                                                order.size + presetOffset
                                            )
                                        }
                                    )
                                }
                            ) { itemState ->
                                val page = itemState.page
                                val isStandaloneActiveCard = hasStandaloneActiveTT && page == 0
                                // While sorting, the card at a position shows the preset the working
                                // order puts there — not the one at that index in the stored list.
                                val presetIndex = when {
                                    isStandaloneActiveCard -> null
                                    else                   -> (page - presetOffset).let { position ->
                                        workingOrder?.getOrNull(position) ?: position
                                    }
                                }
                                val preset = presetIndex?.let { uiState.presets.getOrNull(it) }
                                val isActivePreset = presetIndex != null && presetIndex == uiState.activePresetIndex

                                TempTargetCarouselCard(
                                    preset = preset,
                                    activeTT = if (isStandaloneActiveCard || isActivePreset) uiState.activeTT else null,
                                    remainingTimeMs = uiState.remainingTimeMs,
                                    isSelected = itemState.isSelected,
                                    units = viewModel.units,
                                    onExpired = { viewModel.refreshData() },
                                    // While sorting the card carries no gestures: the move buttons sit
                                    // below the row and a tap or long-press here would only compete.
                                    modifier = if (itemState.isReordering) Modifier else Modifier.combinedClickable(
                                        onClickLabel = selectLabel,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
                                        onLongClickLabel = reorderLabel,
                                        onLongClick = if (canEnterReorder) {
                                            {
                                                // A long press does not fire onClick, so without this
                                                // a peeking card would open the mode centred on — and
                                                // acting on — a different preset.
                                                pagerState.requestScrollToPage(page)
                                                viewModel.updateCurrentCardIndex(page)
                                                presetIndex?.let { viewModel.selectPreset(it) }
                                                viewModel.enterReorderMode()
                                            }
                                        } else null
                                    )
                                )
                            }

                            // TT Editor — hidden when offline on a client: tweaking is pointless when you can
                            // neither Save (toolbar hidden) nor Activate (FAB hidden). Stays for the tweak-and-
                            // activate one-off-TT workflow when the master is reachable (or on master).
                            // Also hidden while sorting: Save has given up its slot to Done, so an edit made
                            // there would have nowhere to go.
                            if (editingEnabled && !isReorderMode) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    TempTargetEditor(
                                        selectedPreset = uiState.selectedPreset,
                                        editorName = uiState.editorName,
                                        editorTarget = uiState.editorTarget,
                                        editorDuration = (uiState.editorDuration / 60000L).toInt(),
                                        eventTime = uiState.eventTime,
                                        eventTimeChanged = uiState.eventTimeChanged,
                                        notes = uiState.notes,
                                        showNotesField = uiState.showNotesField,
                                        units = viewModel.units,
                                        rh = viewModel.rh,
                                        onNameChange = viewModel::updateEditorName,
                                        onTargetChange = viewModel::updateEditorTarget,
                                        onDurationChange = { duration -> viewModel.updateEditorDuration(duration) },
                                        onDateClick = { showDatePicker = true },
                                        onTimeClick = { showTimePicker = true },
                                        onNotesChange = viewModel::updateNotes,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    // Extra space for floating toolbar
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    }

                    // Mini FAB for Cancel (only visible when TT is active). Hidden on a client whose master is
                    // unreachable — canceling a TT is a master/NS action that couldn't be delivered.
                    if (editingEnabled && uiState.activeTT != null && !isReorderMode) {
                        SmallFloatingActionButton(
                            onClick = { viewModel.cancelActive(onSuccess = onNavigateBack) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 88.dp),
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(CoreUiStrings.cancel)
                            )
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
                                    IconButton(onClick = { viewModel.addNewPreset() }) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Add preset"
                                        )
                                    }
                                    // Revert button (only for fixed presets when editor values differ from defaults)
                                    val showRevert = uiState.selectedPreset?.isDeletable == false &&
                                        viewModel.isEditorDifferentFromDefaults()
                                    if (showRevert) {
                                        IconButton(onClick = { viewModel.revertToDefaults() }) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = stringResource(CoreUiStrings.revert_to_defaults)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showDeleteDialog = true },
                                        enabled = uiState.selectedPreset?.isDeletable == true
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(UiStrings.remove_label),
                                            tint = if (uiState.selectedPreset?.isDeletable == true)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                }
                            }
                        }

                        // FAB for primary action (Activate). Hidden on a client whose master is unreachable —
                        // activating a TT is a master/remote action that couldn't be delivered.
                        if (editingEnabled) {
                            AapsFab(
                                onClick = { viewModel.activateWithEditorValues(onSuccess = onNavigateBack) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(UiStrings.activate_label)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// (TT activation confirmation is now built by the master — see TempTargetManagementViewModel.activateWithEditorValues.)
