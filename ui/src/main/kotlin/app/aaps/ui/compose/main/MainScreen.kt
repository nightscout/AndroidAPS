package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.alertDialogs.AboutAlertDialog
import app.aaps.ui.compose.alertDialogs.AboutDialogData
import app.aaps.ui.compose.automationSheet.AutomationBottomSheet
import app.aaps.ui.compose.automationSheet.AutomationViewModel
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.MaintenanceDialogs
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.OverviewScreen
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchToolbar
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import app.aaps.ui.compose.treatmentsSheet.TreatmentBottomSheet
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchResults
import app.aaps.ui.search.SearchUiState
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    aboutDialogData: AboutDialogData?,
    manageSheetState: ManageSheetState,
    manageViewModel: ManageViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    statusViewModel: StatusViewModel,
    treatmentViewModel: TreatmentViewModel,
    automationViewModel: AutomationViewModel,
    // Search
    searchUiState: SearchUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchResultClick: (SearchIndexEntry) -> Unit,
    // Menu/navigation
    onMenuClick: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    onDrawerClosed: () -> Unit,
    onSwitchToClassicUi: () -> Unit,
    onAboutDialogDismiss: () -> Unit,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    // Notifications
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    // Pump setup
    pumpSetupClassName: String? = null,
    pumpSetupIcon: ImageVector? = null,
    pumpSetupLabel: String? = null,
    // Permissions
    permissionsMissing: Boolean = false,
    onPermissionsClick: () -> Unit = {},
    // Toolbar
    quickLaunchItems: List<ResolvedQuickLaunchItem> = emptyList(),
    onQuickLaunchActionClick: (QuickLaunchAction) -> Unit = {},
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    treatmentButtonsDef: PreferenceSubScreenDef,
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showTreatmentSheet by remember { mutableStateOf(false) }
    var showAutomationSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sync drawer state with ui state
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed && uiState.isDrawerOpen) {
            onDrawerClosed()
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MainDrawer(
                    versionName = mainViewModel.versionName,
                    appIcon = mainViewModel.appIcon,
                    onNavigate = { request ->
                        scope.launch { drawerState.close() }
                        onDrawerClosed()
                        onNavigate(request)
                    },
                    isTreatmentsEnabled = uiState.isProfileLoaded
                )
            },
            gesturesEnabled = true,
            modifier = modifier
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    MainTopBar(
                        searchUiState = searchUiState,
                        onMenuClick = {
                            scope.launch {
                                drawerState.open()
                                onMenuClick()
                            }
                        },
                        onPreferencesClick = { onNavigate(NavigationRequest.Element(ElementType.SETTINGS)) },
                        onSearchQueryChange = onSearchQueryChange,
                        onSearchClear = onSearchClear,
                        onSearchActiveChange = onSearchActiveChange
                    )
                },
                bottomBar = {
                    MainNavigationBar(
                        onManageClick = { manageSheetState.show() },
                        onTreatmentClick = {
                            treatmentViewModel.refreshState()
                            showTreatmentSheet = true
                        },
                        quickWizardCount = uiState.quickWizardItems.size,
                        onAutomationClick = {
                            automationViewModel.refreshState()
                            showAutomationSheet = true
                        },
                        automationCount = automationViewModel.uiState.collectAsStateWithLifecycle().value.items.size,
                        pumpSetupClassName = pumpSetupClassName,
                        pumpSetupIcon = pumpSetupIcon,
                        pumpSetupLabel = pumpSetupLabel,
                        onNavigate = onNavigate,
                        permissionsMissing = permissionsMissing,
                        onPermissionsClick = onPermissionsClick,
                    )
                },
            ) { paddingValues ->
                val hasToolbar = quickLaunchItems.isNotEmpty()
                Box(modifier = Modifier.fillMaxSize()) {
                    OverviewScreen(
                        profileName = uiState.profileName,
                        isProfileModified = uiState.isProfileModified,
                        profileProgress = uiState.profileProgress,
                        tempTargetText = uiState.tempTargetText,
                        tempTargetState = uiState.tempTargetState,
                        tempTargetProgress = uiState.tempTargetProgress,
                        tempTargetReason = uiState.tempTargetReason,
                        runningMode = uiState.runningMode,
                        runningModeText = uiState.runningModeText,
                        runningModeProgress = uiState.runningModeProgress,
                        isSimpleMode = uiState.isSimpleMode,
                        calcProgress = calcProgress,
                        graphViewModel = graphViewModel,
                        manageViewModel = manageViewModel,
                        statusViewModel = statusViewModel,
                        statusLightsDef = statusLightsDef,
                        onNavigate = onNavigate,
                        notifications = notifications,
                        onDismissNotification = onDismissNotification,
                        onNotificationActionClick = onNotificationActionClick,
                        autoShowNotificationSheet = autoShowNotificationSheet,
                        onAutoShowConsumed = onAutoShowConsumed,
                        paddingValues = paddingValues,
                        fabBottomOffset = if (hasToolbar) 56.dp else 0.dp
                    )

                    // Search results overlay
                    if (searchUiState.isSearchActive) {
                        SearchResults(
                            results = searchUiState.results,
                            wikiResults = searchUiState.wikiResults,
                            isSearching = searchUiState.isSearching,
                            isSearchingWiki = searchUiState.isSearchingWiki,
                            wikiOffline = searchUiState.wikiOffline,
                            onResultClick = onSearchResultClick,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        )
                    }

                    // Version overlay (always visible for screenshots)
                    VersionOverlay(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(paddingValues)
                    )

                    // Floating quick-action toolbar
                    if (hasToolbar) {
                        QuickLaunchToolbar(
                            items = quickLaunchItems,
                            onActionClick = onQuickLaunchActionClick,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = paddingValues.calculateBottomPadding() + 8.dp)
                        )
                    }

                    // FABs — positioned above the toolbar when it's visible
                    val fabBottomPadding = paddingValues.calculateBottomPadding() +
                        if (hasToolbar) 64.dp else 16.dp
                    SwitchUiFab(
                        onClick = onSwitchToClassicUi,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = fabBottomPadding, end = 16.dp)
                    )
                }
            }
        }

        // Treatment bottom sheet
        if (showTreatmentSheet) {
            val treatmentState by treatmentViewModel.uiState.collectAsStateWithLifecycle()
            TreatmentBottomSheet(
                onDismiss = { showTreatmentSheet = false },
                showCgm = treatmentState.showCgm,
                showCalibration = treatmentState.showCalibration,
                showTreatment = treatmentState.showTreatment,
                showInsulin = treatmentState.showInsulin,
                showCarbs = treatmentState.showCarbs,
                showCalculator = treatmentState.showCalculator,
                isDexcomSource = treatmentState.isDexcomSource,
                showSettingsIcon = treatmentState.showSettingsIcon,
                quickWizardItems = treatmentState.quickWizardItems,
                onNavigate = onNavigate,
                treatmentButtonsDef = treatmentButtonsDef,
            )
        }

        // Automation bottom sheet
        if (showAutomationSheet) {
            val automationState by automationViewModel.uiState.collectAsStateWithLifecycle()
            AutomationBottomSheet(
                onDismiss = { showAutomationSheet = false },
                automationItems = automationState.items,
                onItemClick = { item -> mainViewModel.requestAutomationConfirmation(item.eventId) }
            )
        }

        // Shared confirmation dialog (automation actions, TT presets — from toolbar or bottom sheets)
        val actionConfirmation by mainViewModel.actionConfirmation.collectAsStateWithLifecycle()
        actionConfirmation?.let { confirmation ->
            OkCancelDialog(
                title = confirmation.title,
                message = confirmation.message,
                onConfirm = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        }

        // Maintenance dialogs (sheets, confirmations, export chain)
        MaintenanceDialogs(
            maintenanceViewModel = maintenanceViewModel,
            showMaintenanceSheet = uiState.showMaintenanceSheet,
            onMaintenanceSheetDismiss = onMaintenanceSheetDismiss,
            onDirectoryClick = onDirectoryClick,
            onImportSettingsNavigate = onImportSettingsNavigate,
            onRecreateActivity = onRecreateActivity,
            onLaunchBrowser = onLaunchBrowser,
            onBringToForeground = onBringToForeground,
            onSnackbar = { snackbarHostState.showSnackbar(it) }
        )

        // About dialog
        if (uiState.showAboutDialog && aboutDialogData != null) {
            AboutAlertDialog(
                data = aboutDialogData,
                onDismiss = onAboutDialogDismiss
            )
        }
    } // CompositionLocalProvider
}

@Composable
private fun SwitchUiFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AapsFab(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = "Switch to classic UI"
        )
    }
}
