package app.aaps.ui.compose.profileManagement

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.R
import app.aaps.ui.compose.components.ContentContainer
import app.aaps.ui.compose.components.PageIndicatorDots
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import kotlin.math.absoluteValue

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
    onActivateProfile: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlayMode = uiState.screenMode == ScreenMode.PLAY

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

    AapsTheme {
        Scaffold(
            topBar = {
                AapsTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = ElementType.PROFILE_MANAGEMENT.icon(),
                                contentDescription = null,
                                tint = ElementType.PROFILE_MANAGEMENT.color(),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(stringResource(ElementType.PROFILE_MANAGEMENT.labelResId()))
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
                            IconButton(onClick = onRequestEditMode) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.switch_to_edit)
                                )
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
            ) {
                ContentContainer(
                    isLoading = uiState.isLoading,
                    isEmpty = uiState.profileNames.isEmpty()
                ) {
                    // Calculate initial page based on active profile - store stable value once computed
                    var stableInitialPage by remember { mutableIntStateOf(-1) }
                    if (stableInitialPage == -1 && uiState.activeProfileName != null && uiState.profileNames.isNotEmpty()) {
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

                        // Sync pager with selected profile (for programmatic selection)
                        LaunchedEffect(uiState.currentProfileIndex) {
                            if (pagerState.currentPage != uiState.currentProfileIndex) {
                                pagerState.animateScrollToPage(uiState.currentProfileIndex)
                            }
                        }

                        // Update selected profile when pager changes
                        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                            if (!pagerState.isScrollInProgress && pagerState.currentPage != uiState.currentProfileIndex) {
                                viewModel.selectProfile(pagerState.currentPage)
                            }
                            currentPage = pagerState.currentPage
                        }

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Profile Carousel
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentPadding = PaddingValues(horizontal = 64.dp),
                                pageSpacing = 16.dp
                            ) { page ->
                                val name = uiState.profileNames.getOrNull(page) ?: ""
                                val basalSum = uiState.basalSums.getOrNull(page) ?: 0.0
                                val isActive = name == uiState.activeProfileName
                                val hasErrors = uiState.profileErrors.getOrNull(page)?.isNotEmpty() == true

                                ProfileCarouselCard(
                                    profileName = name,
                                    basalSum = basalSum,
                                    isActive = isActive,
                                    hasErrors = hasErrors,
                                    activeProfileSwitch = if (isActive) uiState.activeProfileSwitch else null,
                                    nextProfileName = if (isActive) uiState.nextProfileName else null,
                                    formatBasalSum = viewModel::formatBasalSum,
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
                                pageCount = uiState.profileNames.size,
                                currentPage = pagerState.currentPage
                            )

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
                                            shortHourUnit = compareData.shortHourUnit,
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
                                            formatBasalSum = viewModel::formatBasalSum
                                        )
                                    }
                                    // Extra space for floating toolbar
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    } // end key
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
                                IconButton(onClick = { viewModel.addNewProfile() }) {
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

                    // FAB for primary action (Activate) — always visible
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

