package app.aaps.ui.compose.tempTarget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import app.aaps.core.ui.compose.formatMinutesAsDuration
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.R
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.components.PageIndicatorDots
import java.util.Calendar
import kotlin.math.absoluteValue

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
    val isPlayMode = uiState.screenMode == ScreenMode.PLAY

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
    var showActivateDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteDialog && uiState.selectedPreset != null) {
        val presetName = uiState.selectedPreset!!.getDisplayName(viewModel.rh)
        OkCancelDialog(
            title = viewModel.rh.gs(app.aaps.core.ui.R.string.removerecord),
            message = "${viewModel.rh.gs(app.aaps.core.ui.R.string.delete)} $presetName?",
            onConfirm = {
                viewModel.deleteCurrentPreset()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Activate confirmation dialog
    if (showActivateDialog) {
        val confirmMessage = buildActivationMessage(viewModel, uiState)
        OkCancelDialog(
            title = viewModel.rh.gs(app.aaps.core.ui.R.string.activate_profile).removeSuffix(" profile"),
            message = confirmMessage,
            onConfirm = {
                viewModel.activateWithEditorValues(onSuccess = onNavigateBack)
                showActivateDialog = false
            },
            onDismiss = { showActivateDialog = false }
        )
    }

    // Cancel active TT confirmation dialog
    if (showCancelDialog) {
        OkCancelDialog(
            title = viewModel.rh.gs(app.aaps.core.ui.R.string.cancel),
            message = viewModel.rh.gs(app.aaps.core.ui.R.string.confirm_cancel_temp_target),
            onConfirm = {
                viewModel.cancelActive()
                showCancelDialog = false
            },
            onDismiss = { showCancelDialog = false }
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

    AapsTheme {
        Scaffold(
            topBar = {
                AapsTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = ElementType.TEMP_TARGET_MANAGEMENT.icon(),
                                contentDescription = null,
                                tint = ElementType.TEMP_TARGET_MANAGEMENT.color(),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(stringResource(ElementType.TEMP_TARGET_MANAGEMENT.labelResId()))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (isPlayMode) {
                            // Edit mode button (shown in PLAY mode)
                            IconButton(onClick = onRequestEditMode) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.switch_to_edit)
                                )
                            }
                        } else {
                            // Save button (shown when editor has unsaved changes in EDIT mode)
                            if (uiState.selectedPreset != null && viewModel.hasUnsavedChanges()) {
                                IconButton(onClick = { viewModel.saveCurrentPreset() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = stringResource(app.aaps.core.ui.R.string.save),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                            if (page < cardCount) {
                                pagerState.animateScrollToPage(page)
                                scrollToPage = null
                            }
                        }
                    }

                    // Update selected preset when pager changes
                    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                        if (!pagerState.isScrollInProgress) {
                            viewModel.updateCurrentCardIndex(pagerState.currentPage)
                            val presetIndex = if (hasStandaloneActiveTT && pagerState.currentPage > 0) {
                                pagerState.currentPage - 1
                            } else if (!hasStandaloneActiveTT) {
                                pagerState.currentPage
                            } else {
                                null // Standalone active TT card selected
                            }

                            presetIndex?.let { viewModel.selectPreset(it) }
                        }
                        currentPage = pagerState.currentPage
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // TT Preset Carousel
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentPadding = PaddingValues(horizontal = 64.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            val isStandaloneActiveCard = hasStandaloneActiveTT && page == 0
                            val presetIndex = when {
                                isStandaloneActiveCard -> null
                                hasStandaloneActiveTT  -> page - 1
                                else                   -> page
                            }
                            val preset = presetIndex?.let { uiState.presets.getOrNull(it) }
                            val isActivePreset = presetIndex != null && presetIndex == uiState.activePresetIndex

                            TempTargetCarouselCard(
                                preset = preset,
                                activeTT = if (isStandaloneActiveCard || isActivePreset) uiState.activeTT else null,
                                remainingTimeMs = uiState.remainingTimeMs,
                                isSelected = pagerState.currentPage == page,
                                units = viewModel.units,
                                onExpired = { viewModel.refreshData() },
                                modifier = Modifier
                                    .graphicsLayer {
                                        val pageOffset = (
                                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                            ).absoluteValue
                                        // Scale effect for carousel
                                        lerp(
                                            start = 0.85f,
                                            stop = 1f,
                                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                        ).also { scale ->
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        // Alpha effect
                                        alpha = lerp(
                                            start = 0.5f,
                                            stop = 1f,
                                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                        )
                                    }
                            )
                        }

                        // Page indicator dots
                        PageIndicatorDots(
                            pageCount = cardCount,
                            currentPage = pagerState.currentPage
                        )

                        // TT Editor (always visible)
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

                // Mini FAB for Cancel (only visible when TT is active)
                if (uiState.activeTT != null) {
                    SmallFloatingActionButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 88.dp),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.cancel)
                        )
                    }
                }

                // Floating Toolbar with FAB (M3 style)
                Row(
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
                                            contentDescription = stringResource(app.aaps.core.ui.R.string.revert_to_defaults)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showDeleteDialog = true },
                                    enabled = uiState.selectedPreset?.isDeletable == true
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.remove_label),
                                        tint = if (uiState.selectedPreset?.isDeletable == true)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        }
                    }

                    // FAB for primary action (Activate) — always visible
                    AapsFab(
                        onClick = { showActivateDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.activate_label)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Build activation confirmation message showing all details
 */
private fun buildActivationMessage(
    viewModel: TempTargetManagementViewModel,
    uiState: TempTargetManagementUiState
): String {
    return buildString {
        append(viewModel.rh.gs(app.aaps.core.ui.R.string.temporary_target))
        append(": ")
        // editorTarget is already in user units, convert to mg/dL for proper formatting
        val targetMgdl = viewModel.profileUtil.convertToMgdl(uiState.editorTarget, viewModel.units)
        append(viewModel.profileUtil.fromMgdlToStringInUnits(targetMgdl))
        append("<br/>")
        append(viewModel.rh.gs(app.aaps.core.ui.R.string.duration))
        append(": ")
        val durationMinutes = (uiState.editorDuration / 60000L).toInt()
        append(formatMinutesAsDuration(durationMinutes, viewModel.rh))

        if (uiState.selectedPreset != null) {
            append("<br/>")
            append(viewModel.rh.gs(app.aaps.core.ui.R.string.reason))
            append(": ")
            append(uiState.selectedPreset.getDisplayName(viewModel.rh))
        }

        if (uiState.eventTimeChanged) {
            append("<br/>")
            append(viewModel.rh.gs(app.aaps.core.ui.R.string.time))
            append(": ")
            append(viewModel.dateUtil.dateAndTimeString(uiState.eventTime))
        }

        if (uiState.notes.isNotBlank()) {
            append("<br/>")
            append(viewModel.rh.gs(app.aaps.core.ui.R.string.notes_label))
            append(": ")
            append(uiState.notes)
        }
    }
}
