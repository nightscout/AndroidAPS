package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.dialogs.QueryAnyPasswordDialog
import app.aaps.ui.compose.alertDialogs.AboutAlertDialog
import app.aaps.ui.compose.alertDialogs.AboutDialogData
import app.aaps.ui.compose.management.CloudDirectorySheet
import app.aaps.ui.compose.management.ImportSource
import app.aaps.ui.compose.management.LogSettingBottomSheet
import app.aaps.ui.compose.management.MaintenanceBottomSheet
import app.aaps.ui.compose.management.MaintenanceEvent
import app.aaps.ui.compose.management.MaintenanceViewModel
import app.aaps.ui.compose.management.MaintenanceViewModel.ExportState
import app.aaps.ui.compose.overview.OverviewScreen
import app.aaps.ui.compose.overview.automation.AutomationActionItem
import app.aaps.ui.compose.overview.automation.AutomationBottomSheet
import app.aaps.ui.compose.overview.automation.AutomationViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.manage.ManageBottomSheet
import app.aaps.ui.compose.overview.manage.ManageViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.overview.treatments.TreatmentBottomSheet
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchResults
import app.aaps.ui.search.SearchUiState
import kotlinx.coroutines.launch
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    versionName: String,
    appIcon: Int,
    aboutDialogData: AboutDialogData?,
    manageViewModel: ManageViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    statusViewModel: StatusViewModel,
    treatmentViewModel: app.aaps.ui.compose.overview.treatments.TreatmentViewModel,
    automationViewModel: AutomationViewModel,
    // Search
    searchUiState: SearchUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchResultClick: (SearchIndexEntry) -> Unit,
    // Menu/navigation
    onMenuClick: () -> Unit,
    onProfileManagementClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onMenuItemClick: (MainMenuItem) -> Unit,
    onCategoryClick: (DrawerCategory) -> Unit,
    onCategoryExpand: (DrawerCategory) -> Unit,
    onCategorySheetDismiss: () -> Unit,
    onPluginClick: (PluginBase) -> Unit,
    onPluginEnableToggle: (PluginBase, PluginType, Boolean) -> Unit,
    onPluginPreferencesClick: (PluginBase) -> Unit,
    onDrawerClosed: () -> Unit,
    onSwitchToClassicUi: () -> Unit,
    onAboutDialogDismiss: () -> Unit,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    // Overview status callbacks
    onSensorInsertClick: () -> Unit,
    onFillClick: () -> Unit,
    onInsulinChangeClick: () -> Unit,
    onBatteryChangeClick: () -> Unit,
    // Actions callbacks
    onRunningModeClick: () -> Unit,
    onTempTargetClick: () -> Unit,
    onTempBasalClick: () -> Unit,
    onExtendedBolusClick: () -> Unit,
    onHistoryBrowserClick: () -> Unit,
    onQuickWizardManagementClick: () -> Unit,
    onBgCheckClick: () -> Unit,
    onNoteClick: () -> Unit,
    onExerciseClick: () -> Unit,
    onQuestionClick: () -> Unit,
    onAnnouncementClick: () -> Unit,
    onSiteRotationClick: () -> Unit,
    onCarbsClick: () -> Unit,
    onInsulinClick: () -> Unit,
    onTreatmentClick: () -> Unit,
    onCgmClick: (() -> Unit)?,
    onCalibrationClick: (() -> Unit)?,
    onQuickWizardClick: ((String) -> Unit)? = null,
    onActionsError: (String, String) -> Unit,
    // Notifications
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    dateUtil: DateUtil,
    // Permissions
    permissionsMissing: Boolean = false,
    onPermissionsClick: () -> Unit = {},
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    statusLightsDef: app.aaps.core.ui.compose.preference.PreferenceSubScreenDef,
    treatmentButtonsDef: app.aaps.core.ui.compose.preference.PreferenceSubScreenDef,
    preferences: app.aaps.core.keys.interfaces.Preferences,
    config: app.aaps.core.interfaces.configuration.Config,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showTreatmentSheet by remember { mutableStateOf(false) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showAutomationSheet by remember { mutableStateOf(false) }
    var confirmAutomationItem by remember { mutableStateOf<AutomationActionItem?>(null) }
    var showLogSettings by remember { mutableStateOf(false) }

    // Confirmation dialog states for maintenance destructive actions
    var showConfirmResetAps by remember { mutableStateOf(false) }
    var showConfirmResetDb by remember { mutableStateOf(false) }
    var showConfirmCleanupDb by remember { mutableStateOf(false) }
    var showConfirmExportCsv by remember { mutableStateOf(false) }
    var showConfirmSendLogs by remember { mutableStateOf(false) }
    var cleanupResultText by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Export dialog state
    val exportState by maintenanceViewModel.exportState.collectAsStateWithLifecycle()

    // Cloud directory dialog state
    val cloudDirectoryState by maintenanceViewModel.cloudDirectoryState.collectAsStateWithLifecycle()

    // Export config for dynamic labels and cloud error badge
    val exportConfig by maintenanceViewModel.exportConfig.collectAsStateWithLifecycle()

    // Collect maintenance events
    LaunchedEffect(Unit) {
        maintenanceViewModel.events.collect { event ->
            when (event) {
                is MaintenanceEvent.RecreateActivity  -> onRecreateActivity()
                is MaintenanceEvent.CleanupResult     -> cleanupResultText = event.result
                is MaintenanceEvent.Snackbar          -> snackbarHostState.showSnackbar(event.message)
                is MaintenanceEvent.Error             -> snackbarHostState.showSnackbar(event.message)
                is MaintenanceEvent.LaunchBrowser     -> onLaunchBrowser(event.url)
                is MaintenanceEvent.BringToForeground -> onBringToForeground()
            }
        }
    }

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

    // Show bottom sheet when category is selected
    LaunchedEffect(uiState.selectedCategoryForSheet) {
        if (uiState.selectedCategoryForSheet != null) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawer(
                categories = uiState.drawerCategories,
                versionName = versionName,
                appIcon = appIcon,
                onCategoryClick = { category ->
                    scope.launch {
                        drawerState.close()
                        onDrawerClosed()
                    }
                    onCategoryClick(category)
                },
                onCategoryExpand = onCategoryExpand,
                onMenuItemClick = { menuItem ->
                    scope.launch {
                        drawerState.close()
                        onDrawerClosed()
                    }
                    onMenuItemClick(menuItem)
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
                    onPreferencesClick = onPreferencesClick,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchClear = onSearchClear,
                    onSearchActiveChange = onSearchActiveChange
                )
            },
            bottomBar = {
                MainNavigationBar(
                    onManageClick = {
                        manageViewModel.refreshState()
                        showManageSheet = true
                    },
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
                    permissionsMissing = permissionsMissing,
                    onPermissionsClick = onPermissionsClick,
                )
            },
            floatingActionButton = {
                SwitchUiFab(onClick = onSwitchToClassicUi)
            }
        ) { paddingValues ->
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
                    onProfileManagementClick = onProfileManagementClick,
                    onTempTargetClick = onTempTargetClick,
                    onRunningModeClick = onRunningModeClick,
                    onSensorInsertClick = onSensorInsertClick,
                    onFillClick = onFillClick,
                    onInsulinChangeClick = onInsulinChangeClick,
                    onBatteryChangeClick = onBatteryChangeClick,
                    notifications = notifications,
                    onDismissNotification = onDismissNotification,
                    onNotificationActionClick = onNotificationActionClick,
                    autoShowNotificationSheet = autoShowNotificationSheet,
                    onAutoShowConsumed = onAutoShowConsumed,
                    dateUtil = dateUtil,
                    paddingValues = paddingValues,
                    preferences = preferences,
                    config = config
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
                if (config.APS || config.PUMPCONTROL) {
                    val colors = AapsTheme.generalColors
                    val versionColor = when {
                        config.COMMITTED                                                          -> colors.versionCommitted
                        preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME) != 0L -> colors.versionWarning
                        else                                                                      -> colors.versionUncommitted
                    }
                    Text(
                        text = "${config.VERSION_NAME} (${config.HEAD.substring(0, minOf(4, config.HEAD.length))})",
                        color = versionColor,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(paddingValues)
                            .padding(top = 4.dp, end = 4.dp)
                    )
                }
            }
        }
    }

    // Plugin selection bottom sheet
    uiState.selectedCategoryForSheet?.let { category ->
        PluginSelectionSheet(
            category = category,
            isSimpleMode = uiState.isSimpleMode,
            pluginStateVersion = uiState.pluginStateVersion,
            sheetState = sheetState,
            onDismiss = onCategorySheetDismiss,
            onPluginClick = { plugin ->
                onCategorySheetDismiss()
                onPluginClick(plugin)
            },
            onPluginEnableToggle = onPluginEnableToggle,
            onPluginPreferencesClick = { plugin ->
                onCategorySheetDismiss()
                onPluginPreferencesClick(plugin)
            }
        )
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
            onCarbsClick = onCarbsClick,
            onInsulinClick = onInsulinClick,
            onTreatmentClick = onTreatmentClick,
            onCgmClick = onCgmClick,
            onCalibrationClick = onCalibrationClick,
            onQuickWizardClick = onQuickWizardClick,
            treatmentButtonsDef = treatmentButtonsDef,
            preferences = preferences,
            config = config
        )
    }

    // Automation bottom sheet
    if (showAutomationSheet) {
        val automationState by automationViewModel.uiState.collectAsStateWithLifecycle()
        AutomationBottomSheet(
            onDismiss = { showAutomationSheet = false },
            automationItems = automationState.items,
            onItemClick = { item -> confirmAutomationItem = item }
        )
    }

    // Automation confirmation dialog
    confirmAutomationItem?.let { item ->
        val message = item.actionsDescription.joinToString("\n") { "• $it" }
        OkCancelDialog(
            title = item.title,
            message = message,
            onConfirm = {
                automationViewModel.processAutomationEvent(item.eventHashCode)
                confirmAutomationItem = null
            },
            onDismiss = { confirmAutomationItem = null }
        )
    }

    // Manage bottom sheet
    if (showManageSheet) {
        val manageState by manageViewModel.uiState.collectAsStateWithLifecycle()
        ManageBottomSheet(
            onDismiss = { showManageSheet = false },
            isSimpleMode = uiState.isSimpleMode,
            showTempTarget = manageState.showTempTarget,
            showTempBasal = manageState.showTempBasal,
            showCancelTempBasal = manageState.showCancelTempBasal,
            showExtendedBolus = manageState.showExtendedBolus,
            showCancelExtendedBolus = manageState.showCancelExtendedBolus,
            cancelTempBasalText = manageState.cancelTempBasalText,
            cancelExtendedBolusText = manageState.cancelExtendedBolusText,
            customActions = manageState.customActions,
            onProfileManagementClick = onProfileManagementClick,
            onTempTargetClick = onTempTargetClick,
            onTempBasalClick = onTempBasalClick,
            onCancelTempBasalClick = {
                manageViewModel.cancelTempBasal { success, comment ->
                    if (!success) {
                        onActionsError(comment, "Temp basal delivery error")
                    }
                }
            },
            onExtendedBolusClick = onExtendedBolusClick,
            onCancelExtendedBolusClick = {
                manageViewModel.cancelExtendedBolus { success, comment ->
                    if (!success) {
                        onActionsError(comment, "Extended bolus delivery error")
                    }
                }
            },
            onBgCheckClick = onBgCheckClick,
            onNoteClick = onNoteClick,
            onExerciseClick = onExerciseClick,
            onQuestionClick = onQuestionClick,
            onAnnouncementClick = onAnnouncementClick,
            onSiteRotationClick = onSiteRotationClick,
            onQuickWizardClick = onQuickWizardManagementClick,
            onCustomActionClick = { manageViewModel.executeCustomAction(it.customActionType) }
        )
    }

    // Maintenance bottom sheet
    if (uiState.showMaintenanceSheet) {
        // Refresh export config when sheet opens
        LaunchedEffect(Unit) {
            maintenanceViewModel.refreshExportConfig()
        }

        MaintenanceBottomSheet(
            onDismiss = onMaintenanceSheetDismiss,
            onLogSettingsClick = { showLogSettings = true },
            onSendLogsClick = { showConfirmSendLogs = true },
            onDeleteLogsClick = { maintenanceViewModel.deleteLogs() },
            onDirectoryClick = {
                maintenanceViewModel.logSelectDirectory()
                onDirectoryClick()
            },
            onCloudDirectoryClick = { maintenanceViewModel.showCloudDirectory() },
            onClearCloudClick = { maintenanceViewModel.requestClearCloud() },
            onExportSettingsClick = {
                maintenanceViewModel.startExport()
            },
            onImportSettingsClick = { source ->
                maintenanceViewModel.logImportSettings()
                onImportSettingsNavigate(source)
            },
            onExportCsvClick = { showConfirmExportCsv = true },
            onResetApsResultsClick = { showConfirmResetAps = true },
            onCleanupDbClick = { showConfirmCleanupDb = true },
            onResetDbClick = { showConfirmResetDb = true },
            exportConfig = exportConfig,
            onToggleSettingsLocal = { maintenanceViewModel.toggleSettingsLocal(it) },
            onToggleSettingsCloud = { maintenanceViewModel.toggleSettingsCloud(it) },
            onToggleLogEmail = { maintenanceViewModel.toggleLogEmail(it) },
            onToggleLogCloud = { maintenanceViewModel.toggleLogCloud(it) },
            onToggleCsvLocal = { maintenanceViewModel.toggleCsvLocal(it) },
            onToggleCsvCloud = { maintenanceViewModel.toggleCsvCloud(it) }
        )
    }

    // Log settings bottom sheet
    if (showLogSettings) {
        LogSettingBottomSheet(
            logElements = maintenanceViewModel.logElements,
            onDismiss = { showLogSettings = false },
            onToggle = { element, enabled -> maintenanceViewModel.toggleLogElement(element, enabled) },
            onResetToDefaults = { maintenanceViewModel.resetLogDefaults() }
        )
    }

    // Cloud directory dialog
    CloudDirectorySheet(
        state = cloudDirectoryState,
        onConnectGoogleDrive = { maintenanceViewModel.connectGoogleDrive() },
        onConfirmClear = { maintenanceViewModel.confirmClearCloud() },
        onCancelClear = { maintenanceViewModel.cancelClearCloud() },
        onReauthorize = { maintenanceViewModel.reauthorize() },
        onDismiss = { maintenanceViewModel.dismissCloudDirectory() }
    )

    // About dialog
    if (uiState.showAboutDialog && aboutDialogData != null) {
        AboutAlertDialog(
            data = aboutDialogData,
            onDismiss = onAboutDialogDismiss
        )
    }

    // Maintenance confirmation dialogs
    if (showConfirmResetAps) {
        OkCancelDialog(
            title = stringResource(CoreUiR.string.confirmation),
            message = stringResource(CoreUiR.string.reset_aps_results_confirm),
            onConfirm = {
                showConfirmResetAps = false
                maintenanceViewModel.resetApsResults()
            },
            onDismiss = { showConfirmResetAps = false }
        )
    }

    if (showConfirmResetDb) {
        OkCancelDialog(
            title = stringResource(CoreUiR.string.confirmation),
            message = stringResource(CoreUiR.string.reset_db_confirm),
            onConfirm = {
                showConfirmResetDb = false
                maintenanceViewModel.resetDatabases()
            },
            onDismiss = { showConfirmResetDb = false }
        )
    }

    if (showConfirmCleanupDb) {
        OkCancelDialog(
            title = stringResource(CoreUiR.string.confirmation),
            message = stringResource(CoreUiR.string.cleanup_db_confirm),
            onConfirm = {
                showConfirmCleanupDb = false
                maintenanceViewModel.cleanupDatabases()
            },
            onDismiss = { showConfirmCleanupDb = false }
        )
    }

    if (showConfirmSendLogs) {
        OkCancelDialog(
            title = stringResource(CoreUiR.string.confirmation),
            message = stringResource(CoreUiR.string.send_logs) + "?",
            onConfirm = {
                showConfirmSendLogs = false
                maintenanceViewModel.sendLogs()
            },
            onDismiss = { showConfirmSendLogs = false }
        )
    }

    if (showConfirmExportCsv) {
        OkCancelDialog(
            title = stringResource(CoreUiR.string.confirmation),
            message = stringResource(CoreUiR.string.ue_export_to_csv) + "?",
            onConfirm = {
                showConfirmExportCsv = false
                maintenanceViewModel.exportCsv()
            },
            onDismiss = { showConfirmExportCsv = false }
        )
    }

    // Export settings dialog chain
    when (exportState) {
        is ExportState.MasterPasswordMissing -> {
            OkDialog(
                title = stringResource(CoreUiR.string.nav_export),
                message = stringResource(CoreUiR.string.master_password_missing, stringResource(CoreUiR.string.protection)),
                onDismiss = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.ConfirmExport         -> {
            val confirmState = exportState as ExportState.ConfirmExport
            OkCancelDialog(
                title = stringResource(CoreUiR.string.export_to),
                message = confirmState.fileName + "?\n\n" +
                    stringResource(CoreUiR.string.password_preferences_encrypt_prompt),
                onConfirm = { maintenanceViewModel.onExportConfirmed() },
                onDismiss = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.AskPassword           -> {
            QueryAnyPasswordDialog(
                title = stringResource(KeysR.string.master_password),
                passwordExplanation = stringResource(CoreUiR.string.password_preferences_encrypt_prompt),
                onConfirm = { password -> maintenanceViewModel.onExportPasswordEntered(password) },
                onCancel = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.Idle                  -> { /* no dialog */
        }
    }

    // Cleanup result dialog (result contains HTML with <br> tags)
    cleanupResultText?.let { result ->
        OkDialog(
            title = stringResource(CoreUiR.string.result),
            message = "<b>" + stringResource(CoreUiR.string.cleared_entries) + "</b><br>" + result,
            onDismiss = { cleanupResultText = null }
        )
    }
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
