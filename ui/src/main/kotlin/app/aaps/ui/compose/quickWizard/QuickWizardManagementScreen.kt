package app.aaps.ui.compose.quickWizard

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.R
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.components.PageIndicatorDots
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import kotlin.math.absoluteValue
import app.aaps.core.ui.R as CoreR

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
    val isPlayMode = uiState.screenMode == ScreenMode.PLAY

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
            title = stringResource(CoreR.string.removerecord),
            message = "${stringResource(CoreR.string.delete)} ${entry?.buttonText() ?: ""}?",
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
            title = stringResource(CoreR.string.unsaved_changes),
            message = stringResource(R.string.save_changes_question),
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

    // Back button handler - check for unsaved changes (skip in PLAY mode)
    BackHandler {
        if (!isPlayMode && viewModel.hasUnsavedChanges()) {
            showUnsavedChangesDialog = true
        } else {
            onNavigateBack()
        }
    }

    // Track current page for floating toolbar actions
    var currentPage by remember { mutableIntStateOf(0) }

    AapsTheme {
        Scaffold(
            topBar = {
                AapsTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = ElementType.QUICK_WIZARD_MANAGEMENT.icon(),
                                contentDescription = null,
                                tint = ElementType.QUICK_WIZARD_MANAGEMENT.color(),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(stringResource(ElementType.QUICK_WIZARD_MANAGEMENT.labelResId()))
                        }
                    },
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
                                contentDescription = stringResource(CoreR.string.back)
                            )
                        }
                    },
                    actions = {
                        if (isPlayMode) {
                            // Edit mode button (shown in PLAY mode)
                            IconButton(onClick = onRequestEditMode) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(CoreR.string.switch_to_edit)
                                )
                            }
                        } else {
                            // Save button (shown when editor has unsaved changes in EDIT mode)
                            if (uiState.entries.isNotEmpty() && uiState.hasUnsavedChanges) {
                                IconButton(onClick = { viewModel.saveCurrentEntry() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Save,
                                        contentDescription = stringResource(CoreR.string.save),
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
                            if (page < cardCount) {
                                pagerState.animateScrollToPage(page)
                                scrollToPage = null
                            }
                        }
                    }

                    // Update selected entry when pager changes
                    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
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
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentPadding = PaddingValues(horizontal = 64.dp),
                            pageSpacing = 16.dp,
                            userScrollEnabled = !viewModel.hasUnsavedChanges()  // Disable swipe if unsaved changes
                        ) { page ->
                            val entry = uiState.entries.getOrNull(page)
                            if (entry != null) {
                                QuickWizardCarouselCard(
                                    entry = entry,
                                    isSelected = pagerState.currentPage == page,
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
                        }

                        // Page indicator dots
                        PageIndicatorDots(
                            pageCount = cardCount,
                            currentPage = pagerState.currentPage
                        )

                        // QuickWizard Editor (hidden in PLAY mode)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (!isPlayMode && uiState.selectedIndex >= 0 && uiState.selectedIndex < uiState.entries.size) {
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
                                // Add button
                                IconButton(onClick = { viewModel.addNewEntry() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(CoreR.string.add)
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
                                        contentDescription = stringResource(R.string.remove_label),
                                        tint = if (uiState.entries.isNotEmpty())
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        }
                    }

                    // FAB for Execute/Play — always visible
                    if (uiState.entries.isNotEmpty()) {
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
