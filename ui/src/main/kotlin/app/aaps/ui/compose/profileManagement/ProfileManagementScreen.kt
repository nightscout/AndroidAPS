package app.aaps.ui.compose.profileManagement

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.graph.profile.ProfileCompareContent
import app.aaps.core.graph.profile.ProfileSingleContent
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.R
import app.aaps.ui.compose.components.CarouselReorderConfig
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.components.ManagementCarousel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import kotlinx.coroutines.launch

/**
 * Profile cards carry less content than the other management screens, so they sit lower than
 * the shared `CarouselDefaults.CardHeight`.
 */
private val PROFILE_CARD_HEIGHT = 140.dp

/**
 * Screen for managing local profiles.
 * Displays profiles in a carousel with profile viewer below and action buttons.
 *
 * @param viewModel ViewModel managing profile state and operations
 * @param onNavigateBack Callback to navigate back
 * @param onEditProfile Callback when user wants to edit a profile (receives profile index)
 * @param onActivateProfile Callback when user wants to activate a profile (receives profile index)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileManagementScreen(
    viewModel: ProfileManagementViewModel,
    initialMode: ScreenMode = ScreenMode.EDIT,
    onNavigateBack: () -> Unit = {},
    onRequestEditMode: () -> Unit = {},
    onEditProfile: (Int) -> Unit = {},
    onActivateProfile: (Int) -> Unit = {},
    onAddProfile: () -> Unit = {},
    onInsulinManager: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlayMode = uiState.screenMode == ScreenMode.PLAY

    // Non-null working order == reorder ("sort") mode is on. Modelled as a flag rather than a third
    // ScreenMode, which is documented as a one-way PLAY -> EDIT switch.
    val reorderOrder by viewModel.reorderOrder.collectAsStateWithLifecycle()
    val isReorderMode = reorderOrder != null
    val canReorder = !isPlayMode && uiState.profileNames.size > 1
    val scope = rememberCoroutineScope()
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Back leaves sort mode rather than the screen, so a reshuffle isn't silently discarded.
    BackHandler(enabled = isReorderMode) { viewModel.cancelReorder() }

    // The view model is activity-scoped, so an uncommitted order would otherwise survive until the
    // screen is reopened — dropping the user straight back into sort mode with a stale order.
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelReorder() }
    }

    // Set initial screen mode
    LaunchedEffect(initialMode) {
        viewModel.setScreenMode(initialMode)
    }

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<Int?>(null) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var profileToClone by remember { mutableStateOf<Int?>(null) }

    // Delete confirmation dialog
    if (showDeleteDialog && profileToDelete != null) {
        val profileName = uiState.profileNames.getOrNull(profileToDelete!!) ?: ""
        OkCancelDialog(
            title = viewModel.rh.gs(app.aaps.core.ui.R.string.removerecord),
            message = viewModel.rh.gs(R.string.confirm_remove_profile, profileName),
            onConfirm = {
                profileToDelete?.let { viewModel.removeProfile(it) }
                showDeleteDialog = false
                profileToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                profileToDelete = null
            }
        )
    }

    // Clone confirmation dialog
    if (showCloneDialog && profileToClone != null) {
        val profileName = uiState.profileNames.getOrNull(profileToClone!!) ?: ""
        OkCancelDialog(
            title = viewModel.rh.gs(R.string.clone_label),
            message = viewModel.rh.gs(R.string.confirm_clone_profile, profileName),
            onConfirm = {
                profileToClone?.let { viewModel.cloneProfile(it) }
                showCloneDialog = false
                profileToClone = null
            },
            onDismiss = {
                showCloneDialog = false
                profileToClone = null
            }
        )
    }

    // Track current page for floating toolbar actions
    var currentPage by remember { mutableStateOf(uiState.currentProfileIndex) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    AapsTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isReorderMode) {
                    AapsTopAppBar(
                        title = { Text(stringResource(app.aaps.core.ui.R.string.reorder)) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.cancelReorder() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.cancel)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { scope.launch { viewModel.commitReorder() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.ok)
                                )
                            }
                        }
                    )
                } else {
                    AapsTopAppBar(
                        title = { Text(stringResource(ElementType.PROFILE_MANAGEMENT.labelResId())) },
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
                                // The switch-to-edit action belongs to the auth-gated PLAY mode. An
                                // unpaired client is read-only for a different reason (no master to send
                                // edits to) and the view model refuses the switch, so it gets no action
                                // here at all.
                                if (uiState.commandsAllowed) {
                                    IconButton(onClick = onRequestEditMode) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = stringResource(app.aaps.core.ui.R.string.switch_to_edit)
                                        )
                                    }
                                }
                            } else {
                                // Menu entry as well as the long-press: the long-press is undiscoverable
                                // on its own, and this is the only route a screen reader can take.
                                Box {
                                    IconButton(onClick = { showOverflowMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(app.aaps.core.ui.R.string.more_options)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(app.aaps.core.ui.R.string.reorder)) },
                                            enabled = canReorder,
                                            onClick = {
                                                showOverflowMenu = false
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ContentContainer(
                    isLoading = uiState.isLoading,
                    isEmpty = uiState.profileNames.isEmpty()
                ) {
                    // Calculate initial page based on active profile - store stable value once computed
                    var stableInitialPage by remember { mutableIntStateOf(-1) }
                    // Never latch it during a sort: changing the key below would dispose the pager
                    // and its effects mid-session, jumping the carousel to an unrelated card while
                    // the working order lives on in the view model.
                    if (stableInitialPage == -1 && !isReorderMode && uiState.activeProfileName != null && uiState.profileNames.isNotEmpty()) {
                        val index = uiState.profileNames.indexOfFirst { it == uiState.activeProfileName }
                        stableInitialPage = if (index >= 0) index else 0
                    }
                    val initialPage = if (stableInitialPage >= 0) stableInitialPage else 0

                    // Use key to force pager recreation when we first get valid initial page
                    key(stableInitialPage) {
                        val pagerState = rememberPagerState(
                            initialPage = initialPage,
                            pageCount = { uiState.profileNames.size }
                        )

                        // Sync pager with selected profile (for programmatic selection).
                        //
                        // isReorderMode is deliberately NOT a key: toggling the mode would restart
                        // this effect, cancelling any in-flight scroll and — on the way out —
                        // animating back to the pre-sort index. It is the sole writer of the
                        // programmatic page, and commitReorder settles the selection itself, so a
                        // committed reorder arrives here as an ordinary index change.
                        LaunchedEffect(uiState.currentProfileIndex) {
                            if (!isReorderMode && pagerState.currentPage != uiState.currentProfileIndex) {
                                pagerState.animateScrollToPage(uiState.currentProfileIndex)
                            }
                        }

                        // Update selected profile when pager changes
                        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                            // Tracked even while sorting so the toolbar acts on the right card the
                            // moment the mode is left.
                            currentPage = pagerState.currentPage
                            // A reorder step moves the carousel too; treating that as a selection
                            // would fight the working order.
                            if (isReorderMode) return@LaunchedEffect
                            if (!pagerState.isScrollInProgress && pagerState.currentPage != uiState.currentProfileIndex) {
                                viewModel.selectProfile(pagerState.currentPage)
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Profile Carousel
                            val moveEarlierLabel = stringResource(app.aaps.core.ui.R.string.carousel_move_earlier)
                            val moveLaterLabel = stringResource(app.aaps.core.ui.R.string.carousel_move_later)
                            val reorderLabel = stringResource(app.aaps.core.ui.R.string.reorder)
                            val selectLabel = stringResource(app.aaps.core.ui.R.string.carousel_show_card)
                            val workingOrder = reorderOrder

                            ManagementCarousel(
                                state = pagerState,
                                cardHeight = PROFILE_CARD_HEIGHT,
                                reorder = workingOrder?.let { order ->
                                    CarouselReorderConfig(
                                        isActive = true,
                                        // The working order, not the live list: a profile arriving
                                        // mid-sort must not enable a move past its end.
                                        itemCount = order.size,
                                        onMove = viewModel::moveReorderItem,
                                        moveEarlierLabel = moveEarlierLabel,
                                        moveLaterLabel = moveLaterLabel,
                                        // rh rather than stringResource: these are read inside a
                                        // non-composable lambda, per position.
                                        positionLabel = { page ->
                                            viewModel.rh.gs(app.aaps.core.ui.R.string.carousel_position, page + 1, order.size)
                                        },
                                        positionDescription = { page ->
                                            viewModel.rh.gs(app.aaps.core.ui.R.string.carousel_position_description, page + 1, order.size)
                                        }
                                    )
                                }
                            ) { itemState ->
                                // While sorting, the card at a position shows the profile the working
                                // order puts there — not the one at that index in the stored list.
                                val dataIndex = workingOrder?.getOrNull(itemState.page) ?: itemState.page
                                val name = uiState.profileNames.getOrNull(dataIndex) ?: ""
                                val basalSum = uiState.basalSums.getOrNull(dataIndex) ?: 0.0
                                val isActive = name == uiState.activeProfileName
                                val hasErrors = uiState.profileErrors.getOrNull(dataIndex)?.isNotEmpty() == true
                                val pumpIncompatible = uiState.pumpWarnings.getOrNull(dataIndex) == true

                                ProfileCarouselCard(
                                    profileName = name,
                                    basalSum = basalSum,
                                    isActive = isActive,
                                    hasErrors = hasErrors,
                                    pumpIncompatible = pumpIncompatible,
                                    activeProfileSwitch = if (isActive) uiState.activeProfileSwitch else null,
                                    nextProfileName = if (isActive) uiState.nextProfileName else null,
                                    formatBasalSum = viewModel::formatBasalSum,
                                    // While sorting, the card carries the move buttons instead: a tap
                                    // that re-centres and a long-press that re-enters the mode would
                                    // both be noise, and they sit under the same fingers.
                                    modifier = if (itemState.isReordering) Modifier else Modifier.combinedClickable(
                                        // Tapping a peeking neighbour centres it, which also gives
                                        // the long-press something honest to hang off.
                                        onClickLabel = selectLabel,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(itemState.page) } },
                                        onLongClickLabel = reorderLabel,
                                        onLongClick = if (canReorder) {
                                            {
                                                // A long press does not fire onClick, so a peeking
                                                // card would otherwise open the mode centred on —
                                                // and acting on — a different profile. Snap without
                                                // animating: an in-progress scroll would arrive with
                                                // the move buttons already disabled.
                                                pagerState.requestScrollToPage(itemState.page)
                                                viewModel.selectProfile(itemState.page)
                                                viewModel.enterReorderMode()
                                            }
                                        } else null
                                    )
                                )
                            }

                            // Profile Viewer
                            uiState.selectedProfile?.let { profile ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    val compareData = uiState.compareData
                                    if (compareData != null) {
                                        ProfileCompareContent(
                                            profile1 = compareData.baseProfile,
                                            profile2 = compareData.effectiveProfile,
                                            icsRows = compareData.icRows,
                                            icUnits = compareData.icUnits,
                                            isfsRows = compareData.isfRows,
                                            isfUnits = compareData.isfUnits,
                                            basalsRows = compareData.basalRows,
                                            basalUnits = compareData.basalUnits,
                                            targetsRows = compareData.targetRows,
                                            targetUnits = compareData.targetUnits,
                                            profileName1 = compareData.baseName,
                                            profileName2 = compareData.effectiveName
                                        )
                                    } else {
                                        ProfileSingleContent(
                                            profile = profile,
                                            getIcList = viewModel::getIcList,
                                            getIsfList = viewModel::getIsfList,
                                            getBasalList = viewModel::getBasalList,
                                            getTargetList = viewModel::getTargetList,
                                            formatBasalSum = viewModel::formatBasalSum,
                                            onInsulinManager = onInsulinManager
                                        )
                                    }
                                    // Extra space for floating toolbar
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    } // end key
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
                                IconButton(onClick = onAddProfile) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.add_new_profile)
                                    )
                                }
                                IconButton(onClick = { onEditProfile(currentPage) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.edit_label)
                                    )
                                }
                                IconButton(onClick = {
                                    profileToClone = currentPage
                                    showCloneDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.clone_label)
                                    )
                                }
                                val canDelete = uiState.profileNames.size > 1
                                    && uiState.profileNames.getOrNull(currentPage) != uiState.activeProfileName
                                IconButton(
                                    onClick = {
                                        profileToDelete = currentPage
                                        showDeleteDialog = true
                                    },
                                    enabled = canDelete
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.remove_label),
                                        tint = if (canDelete)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        }
                    }

                    // FAB for primary action (Activate). Hidden on an unpaired client — activating is a
                    // command the master has to run, and there is no master to send it to.
                    if (uiState.commandsAllowed) {
                        AapsFab(
                            onClick = { onActivateProfile(currentPage) }
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
}

