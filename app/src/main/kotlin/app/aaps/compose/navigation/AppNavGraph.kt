package app.aaps.compose.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.navigation.LocalPluginNavigationRequest
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PluginPreferencesScreen
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.siteRotation.SiteLocationPickerScreen
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.configuration.setupwizard.SWDefinition
import app.aaps.plugins.configuration.setupwizard.SetupWizardScreen
import app.aaps.plugins.sync.nsclientV3.clientcontrol.compose.AuthorizedClientsScreen
import app.aaps.plugins.sync.nsclientV3.clientcontrol.compose.PairWithMasterScreen
import app.aaps.ui.compose.calibrationDialog.CalibrationDialogScreen
import app.aaps.ui.compose.carbsDialog.CarbsDialogScreen
import app.aaps.ui.compose.careDialog.CareDialogScreen
import app.aaps.ui.compose.configuration.ConfigurationScreen
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.extendedBolusDialog.ExtendedBolusDialogScreen
import app.aaps.ui.compose.fillDialog.FillDialogScreen
import app.aaps.ui.compose.history.HistoryScreen
import app.aaps.ui.compose.insulinDialog.InsulinDialogScreen
import app.aaps.ui.compose.insulinManagement.InsulinManagementScreen
import app.aaps.ui.compose.insulinManagement.InsulinManagementViewModel
import app.aaps.ui.compose.maintenance.ImportSettingsScreen
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.ImportViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.preferences.AllPreferencesScreen
import app.aaps.ui.compose.preferences.PreferenceScreenView
import app.aaps.ui.compose.profileHelper.ProfileHelperScreen
import app.aaps.ui.compose.profileManagement.ProfileActivationScreen
import app.aaps.ui.compose.profileManagement.ProfileEditorScreen
import app.aaps.ui.compose.profileManagement.ProfileManagementScreen
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileEditorViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileHelperViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import app.aaps.ui.compose.quickLaunch.QuickLauchConfigScreen
import app.aaps.ui.compose.quickLaunch.QuickLaunchConfigViewModel
import app.aaps.ui.compose.quickWizard.QuickWizardManagementScreen
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeScreen
import app.aaps.ui.compose.scenes.SceneListScreen
import app.aaps.ui.compose.scenes.wizard.SceneWizardScreen
import app.aaps.ui.compose.siteRotationDialog.SiteRotationManagementScreen
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel
import app.aaps.ui.compose.stats.StatsScreen
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempBasalDialog.TempBasalDialogScreen
import app.aaps.ui.compose.tempTarget.TempTargetManagementScreen
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import app.aaps.ui.compose.treatmentDialog.TreatmentDialogScreen
import app.aaps.ui.compose.treatments.TreatmentsScreen
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import app.aaps.ui.compose.wizardDialog.WizardDialogScreen
import app.aaps.ui.search.BuiltInSearchables
import kotlinx.coroutines.launch
import app.aaps.plugins.main.R as PluginsMainR

/**
 * Safe popBackStack that prevents double-navigation during transitions.
 * Only pops if the current entry is in RESUMED state (fully visible and interactive).
 */
fun NavHostController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

/**
 * All navigation routes except the Main route.
 * The Main route stays in the Activity because it has many Activity-context-dependent callbacks.
 */
@Suppress("LongMethod")
fun NavGraphBuilder.appNavGraph(
    navController: NavHostController,
    // ViewModels
    insulinManagementViewModel: InsulinManagementViewModel,
    profileManagementViewModel: ProfileManagementViewModel,
    profileEditorViewModel: ProfileEditorViewModel,
    profileHelperViewModel: ProfileHelperViewModel,
    tempTargetManagementViewModel: TempTargetManagementViewModel,
    quickWizardManagementViewModel: QuickWizardManagementViewModel,
    runningModeManagementViewModel: RunningModeManagementViewModel,
    importViewModel: ImportViewModel,
    configurationViewModel: ConfigurationViewModel,
    treatmentsViewModel: TreatmentsViewModel,
    statsViewModel: StatsViewModel,
    siteRotationManagementViewModel: SiteRotationManagementViewModel,
    graphViewModel: app.aaps.ui.compose.overview.graphs.GraphViewModel,
    chipsViewModel: ChipsViewModel,
    // Dependencies
    swDefinition: SWDefinition,
    rxBus: RxBus,
    activePlugin: ActivePlugin,
    automationRuntime: AutomationRuntime,
    preferences: Preferences,
    rh: ResourceHelper,
    builtInSearchables: BuiltInSearchables,
    prefFileList: FileListProvider,
    persistenceLayer: PersistenceLayer,
    visibilityContext: VisibilityContext,
    // Callbacks
    onNavigationRequest: (NavigationRequest, NavHostController) -> Unit,
    onShowDeliveryError: (comment: String, titleResId: Int) -> Unit,
    withProtection: (ProtectionCheck.Protection, () -> Unit) -> Unit,
    requestEditModeAuthorization: (onGranted: () -> Unit) -> Unit,
    onRefreshPermissions: () -> Unit,
    onExecuteQuickWizard: (guid: String) -> Unit,
    onRequestDirectoryAccess: () -> Unit,
    onRequestPermission: (PermissionGroup) -> Unit,
    findScreenDef: (key: String) -> PreferenceSubScreenDef?,
) {
    composable(
        AppRoute.InsulinManagement.route,
        arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "EDIT" })
    ) { backStackEntry ->
        val mode = ScreenMode.fromRoute(backStackEntry.arguments?.getString("mode"))
        InsulinManagementScreen(
            viewModel = insulinManagementViewModel,
            initialMode = mode,
            onNavigateBack = { navController.safePopBackStack() },
            onRequestEditMode = {
                requestEditModeAuthorization { insulinManagementViewModel.setScreenMode(ScreenMode.EDIT) }
            }
        )
    }

    composable(
        AppRoute.Profile.route,
        arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "EDIT" })
    ) { backStackEntry ->
        val mode = ScreenMode.fromRoute(backStackEntry.arguments?.getString("mode"))
        ProfileManagementScreen(
            viewModel = profileManagementViewModel,
            initialMode = mode,
            onNavigateBack = { navController.safePopBackStack() },
            onRequestEditMode = {
                requestEditModeAuthorization { profileManagementViewModel.setScreenMode(ScreenMode.EDIT) }
            },
            onEditProfile = { index ->
                profileEditorViewModel.selectProfile(index)
                navController.navigate(AppRoute.ProfileEditor.createRoute(index))
            },
            onActivateProfile = { index ->
                withProtection(ProtectionCheck.Protection.BOLUS) {
                    navController.navigate(AppRoute.ProfileActivation.createRoute(index))
                }
            },
            onAddProfile = { navController.navigate(AppRoute.ProfileEditorNew.route) },
            onInsulinManager = { navController.navigate(AppRoute.InsulinManagement.createRoute(mode)) }
        )
    }

    composable(
        AppRoute.TempTargetManagement.route,
        arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "EDIT" })
    ) { backStackEntry ->
        val mode = ScreenMode.fromRoute(backStackEntry.arguments?.getString("mode"))
        TempTargetManagementScreen(
            viewModel = tempTargetManagementViewModel,
            initialMode = mode,
            onNavigateBack = { navController.safePopBackStack() },
            onRequestEditMode = {
                requestEditModeAuthorization { tempTargetManagementViewModel.setScreenMode(ScreenMode.EDIT) }
            }
        )
    }

    composable(
        AppRoute.QuickWizardManagement.route,
        arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "EDIT" })
    ) { backStackEntry ->
        val mode = ScreenMode.fromRoute(backStackEntry.arguments?.getString("mode"))
        QuickWizardManagementScreen(
            viewModel = quickWizardManagementViewModel,
            initialMode = mode,
            onNavigateBack = { navController.safePopBackStack() },
            onRequestEditMode = {
                requestEditModeAuthorization { quickWizardManagementViewModel.setScreenMode(ScreenMode.EDIT) }
            },
            onExecuteClick = { guid ->
                withProtection(ElementType.QUICK_WIZARD.protection) {
                    onExecuteQuickWizard(guid)
                }
            }
        )
    }

    composable(AppRoute.RunningMode.route) {
        RunningModeScreen(
            viewModel = runningModeManagementViewModel,
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(
        route = AppRoute.CareDialog.route,
        arguments = listOf(navArgument("eventTypeOrdinal") { type = NavType.IntType })
    ) { backStackEntry ->
        val siteLocation = backStackEntry.savedStateHandle.get<String>("site_location")
        val siteArrow = backStackEntry.savedStateHandle.get<String>("site_arrow")
        val siteResult = if (siteLocation != null || siteArrow != null) Pair(siteLocation, siteArrow) else null

        CareDialogScreen(
            onNavigateBack = { navController.safePopBackStack() },
            onPickSiteLocation = {
                navController.navigate(AppRoute.SiteLocationPicker.createRoute(TE.Type.SENSOR_CHANGE))
            },
            siteLocationResult = siteResult
        )
    }

    composable(
        route = AppRoute.FillDialog.route,
        arguments = listOf(navArgument("preselect") { type = NavType.IntType })
    ) { backStackEntry ->
        val siteLocation = backStackEntry.savedStateHandle.get<String>("site_location")
        val siteArrow = backStackEntry.savedStateHandle.get<String>("site_arrow")
        val siteResult = if (siteLocation != null || siteArrow != null) Pair(siteLocation, siteArrow) else null

        FillDialogScreen(
            fillButtonsDef = builtInSearchables.fillButtons,
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.treatmentdeliveryerror)
            },
            onPickSiteLocation = {
                navController.navigate(AppRoute.SiteLocationPicker.createRoute(TE.Type.CANNULA_CHANGE))
            },
            siteLocationResult = siteResult
        )
    }

    composable(route = AppRoute.CarbsDialog.route) {
        CarbsDialogScreen(
            carbsButtonsDef = builtInSearchables.carbsButtons,
            bgInfoState = graphViewModel.bgInfoState,
            iobUiState = chipsViewModel.iobUiState,
            cobUiState = chipsViewModel.cobUiState,
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.treatmentdeliveryerror)
            }
        )
    }

    composable(route = AppRoute.InsulinDialog.route) {
        InsulinDialogScreen(
            insulinButtonsDef = builtInSearchables.insulinButtons,
            bgInfoState = graphViewModel.bgInfoState,
            iobUiState = chipsViewModel.iobUiState,
            cobUiState = chipsViewModel.cobUiState,
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.treatmentdeliveryerror)
            }
        )
    }

    composable(route = AppRoute.TreatmentDialog.route) {
        TreatmentDialogScreen(
            bgInfoState = graphViewModel.bgInfoState,
            iobUiState = chipsViewModel.iobUiState,
            cobUiState = chipsViewModel.cobUiState,
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.treatmentdeliveryerror)
            }
        )
    }

    composable(route = AppRoute.CalibrationDialog.route) {
        CalibrationDialogScreen(
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(route = AppRoute.TempBasalDialog.route) {
        TempBasalDialogScreen(
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.temp_basal_delivery_error)
            }
        )
    }

    composable(route = AppRoute.ExtendedBolusDialog.route) {
        ExtendedBolusDialogScreen(
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.treatmentdeliveryerror)
            }
        )
    }

    composable(
        route = AppRoute.WizardDialog.route,
        arguments = listOf(
            navArgument("carbs") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("notes") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        WizardDialogScreen(
            wizardSettingsDef = builtInSearchables.wizardSettings,
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.treatmentdeliveryerror)
            }
        )
    }

    composable(
        route = AppRoute.ImportSettings.route,
        arguments = listOf(navArgument("source") { type = NavType.StringType })
    ) { backStackEntry ->
        val source = try {
            ImportSource.valueOf(backStackEntry.arguments?.getString("source") ?: "LOCAL")
        } catch (_: IllegalArgumentException) {
            ImportSource.LOCAL
        }
        LaunchedEffect(source) { importViewModel.startImport(source) }
        ImportSettingsScreen(
            viewModel = importViewModel,
            prefFileList = prefFileList,
            rxBus = rxBus,
            onClose = { navController.safePopBackStack() }
        )
    }

    composable(
        route = AppRoute.ProfileActivation.route,
        arguments = listOf(navArgument("profileIndex") { type = NavType.IntType })
    ) { backStackEntry ->
        val profileIndex = backStackEntry.arguments?.getInt("profileIndex") ?: 0
        val profileName = profileManagementViewModel.uiState.value.profileNames.getOrNull(profileIndex) ?: ""
        val reuseValues = profileManagementViewModel.getReuseValues()
        val coroutineScope = rememberCoroutineScope()

        ProfileActivationScreen(
            profileName = profileName,
            currentPercentage = reuseValues?.first ?: 100,
            currentTimeshiftHours = reuseValues?.second ?: 0,
            hasReuseValues = reuseValues != null,
            showNotesField = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
            initialTimestamp = profileManagementViewModel.dateUtil.nowWithoutMilliseconds(),
            rh = rh,
            onNavigateBack = { navController.safePopBackStack() },
            checkPumpCompatible = { percentage -> profileManagementViewModel.isPumpCompatible(profileIndex, percentage) },
            onActivate = { duration, percentage, timeshift, withTT, notes, timestamp, timeChanged ->
                coroutineScope.launch {
                    profileManagementViewModel.activateProfile(
                        profileIndex = profileIndex,
                        durationMinutes = duration,
                        percentage = percentage,
                        timeshiftHours = timeshift,
                        withTT = withTT,
                        notes = notes,
                        timestamp = timestamp,
                        timeChanged = timeChanged,
                        // Close only AFTER the user confirms and the switch actually commits (not when the confirm
                        // dialog is merely shown). inclusive = true pops the Profile management screen too, so we
                        // return to the screen it was opened from (e.g. Overview).
                        onSuccess = { navController.popBackStack(AppRoute.Profile.route, inclusive = true) }
                    )
                }
            }
        )
    }

    composable(
        route = AppRoute.ProfileEditor.route,
        arguments = listOf(navArgument("profileIndex") { type = NavType.IntType })
    ) { backStackEntry ->
        val profileIndex = backStackEntry.arguments?.getInt("profileIndex") ?: 0
        val initialized = rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!initialized.value) {
                profileEditorViewModel.selectProfile(profileIndex)
                initialized.value = true
            }
        }
        ProfileEditorScreen(
            viewModel = profileEditorViewModel,
            onBackClick = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.ProfileEditorNew.route) {
        val initialized = rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!initialized.value) {
                profileEditorViewModel.startNewProfileDraft()
                initialized.value = true
            }
        }
        ProfileEditorScreen(
            viewModel = profileEditorViewModel,
            onBackClick = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.Treatments.route) {
        TreatmentsScreen(
            viewModel = treatmentsViewModel,
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.Stats.route) {
        StatsScreen(
            viewModel = statsViewModel,
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.ProfileHelper.route) {
        ProfileHelperScreen(
            viewModel = profileHelperViewModel,
            onBackClick = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.HistoryBrowser.route) {
        HistoryScreen(
            title = stringResource(PluginsMainR.string.nav_history_browser),
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.Preferences.route) {
        AllPreferencesScreen(
            activePlugin = activePlugin,
            rh = rh,
            builtInSearchables = builtInSearchables,
            onBackClick = { navController.safePopBackStack() }
        )
    }

    composable(
        route = AppRoute.PluginContent.route,
        arguments = listOf(navArgument("pluginIndex") { type = NavType.IntType })
    ) { backStackEntry ->
        val pluginIndex = backStackEntry.arguments?.getInt("pluginIndex") ?: -1
        val plugin = activePlugin.getPluginsList().getOrNull(pluginIndex)
        val composeContent = plugin?.getComposeContent()
        if (plugin != null && composeContent is ComposablePluginContent) {
            PluginContentRoute(
                navController = navController,
                plugin = plugin,
                composeContent = composeContent,
                onNavigationRequest = onNavigationRequest,
                withProtection = withProtection,
            )
        } else {
            NavigationErrorFallback(
                rxBus = rxBus,
                message = stringResource(app.aaps.core.ui.R.string.navigation_error_screen_not_found),
                onDismiss = { navController.safePopBackStack() }
            )
        }
    }

    composable(AppRoute.QuickLaunchConfig.route) {
        val quickLaunchConfigViewModel: QuickLaunchConfigViewModel = hiltViewModel()
        QuickLauchConfigScreen(
            viewModel = quickLaunchConfigViewModel,
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.AutomationList.route) {
        // remember so the content wrapper isn't re-allocated on every recomposition of the route.
        val automationContent = remember { automationRuntime.composeContent() }
        AutomationContentRoute(
            navController = navController,
            composeContent = automationContent,
            withProtection = withProtection,
        )
    }

    composable(AppRoute.SceneList.route) {
        SceneListScreen(
            onNavigateToWizard = {
                navController.navigate(AppRoute.SceneWizard.createRoute())
            },
            onNavigateToEditor = { sceneId ->
                navController.navigate(AppRoute.SceneWizard.createRoute(sceneId))
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(AppRoute.AuthorizedClients.route) {
        AuthorizedClientsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(AppRoute.PairWithMaster.route) {
        PairWithMasterScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(AppRoute.SceneWizard.route) {
        SceneWizardScreen(
            onFinished = { navController.popBackStack() },
            onCancel = { navController.popBackStack() }
        )
    }

    composable(AppRoute.Configuration.route) {
        val configState by configurationViewModel.uiState.collectAsStateWithLifecycle()
        ConfigurationScreen(
            categories = configState.categories,
            hardwarePumpConfirmation = configState.hardwarePumpConfirmation,
            onNavigateBack = { navController.safePopBackStack() },
            onNavigateToCategory = { type ->
                navController.navigate(AppRoute.PluginCategory.createRoute(type.ordinal))
            },
            onConfirmHardwarePump = {
                configurationViewModel.confirmHardwarePumpSwitch()
                onRefreshPermissions()
            },
            onDismissHardwarePump = { configurationViewModel.dismissHardwarePumpDialog() }
        )
    }

    composable(
        AppRoute.PluginCategory.route,
        arguments = listOf(navArgument("typeOrdinal") { type = NavType.IntType })
    ) { backStackEntry ->
        val typeOrdinal = backStackEntry.arguments?.getInt("typeOrdinal") ?: return@composable
        val configState by configurationViewModel.uiState.collectAsStateWithLifecycle()
        val category = configState.categories.find { it.type.ordinal == typeOrdinal }
        app.aaps.ui.compose.configuration.PluginCategoryScreen(
            category = category,
            hardwarePumpConfirmation = configState.hardwarePumpConfirmation,
            pluginSwitchConfirmation = configState.pluginSwitchConfirmation,
            onNavigateBack = { navController.safePopBackStack() },
            onNavigate = { request -> onNavigationRequest(request, navController) },
            onPluginEnableToggle = { pluginId, type, enabled ->
                configurationViewModel.togglePluginEnabled(pluginId, type, enabled)
                onRefreshPermissions()
            },
            onConfirmHardwarePump = {
                configurationViewModel.confirmHardwarePumpSwitch()
                onRefreshPermissions()
            },
            onDismissHardwarePump = { configurationViewModel.dismissHardwarePumpDialog() },
            onConfirmPluginSwitch = {
                configurationViewModel.confirmPluginSwitch()
                onRefreshPermissions()
            },
            onDismissPluginSwitch = { configurationViewModel.dismissPluginSwitchDialog() }
        )
    }

    composable(AppRoute.PluginPreferences.route) { backStackEntry ->
        val pluginKey = backStackEntry.arguments?.getString("pluginKey")
        val plugin = activePlugin.getPluginsList().find {
            it.javaClass.simpleName == pluginKey
        }
        if (plugin != null) {
            PluginPreferencesScreen(
                plugin = plugin,
                visibilityContext = visibilityContext,
                onBackClick = { navController.safePopBackStack() }
            )
        } else {
            NavigationErrorFallback(
                rxBus = rxBus,
                message = stringResource(app.aaps.core.ui.R.string.navigation_error_screen_not_found),
                onDismiss = { navController.safePopBackStack() }
            )
        }
    }

    composable(AppRoute.PreferenceScreen.route) { backStackEntry ->
        val screenKey = backStackEntry.arguments?.getString("screenKey")
        val highlightKey = backStackEntry.arguments?.getString("highlightKey")
        val screenDef = screenKey?.let { key -> findScreenDef(key) }
        if (screenDef != null) {
            PreferenceScreenView(
                screenDef = screenDef,
                highlightKey = highlightKey,
                onBackClick = { navController.safePopBackStack() }
            )
        } else {
            NavigationErrorFallback(
                rxBus = rxBus,
                message = stringResource(app.aaps.core.ui.R.string.navigation_error_screen_not_found),
                onDismiss = { navController.safePopBackStack() }
            )
        }
    }

    composable(
        AppRoute.SiteLocationPicker.route,
        arguments = listOf(navArgument("siteTypeOrdinal") { type = NavType.IntType })
    ) { backStackEntry ->
        val siteTypeOrdinal = backStackEntry.arguments?.getInt("siteTypeOrdinal") ?: 0
        val siteType = TE.Type.entries[siteTypeOrdinal]
        val entries by produceState(initialValue = emptyList<TE>()) {
            value = persistenceLayer.getTherapyEventDataFromTime(
                System.currentTimeMillis() - T.days(45).msecs(), false
            ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
        }
        SiteLocationPickerScreen(
            siteType = siteType,
            bodyType = app.aaps.core.ui.compose.siteRotation.BodyType.fromPref(
                preferences.get(IntKey.SiteRotationUserProfile)
            ),
            onClose = { navController.safePopBackStack() },
            onLocationConfirmed = { location, arrow ->
                navController.previousBackStackEntry?.savedStateHandle?.apply {
                    set("site_location", location.name)
                    set("site_arrow", arrow.name)
                }
                navController.safePopBackStack()
            },
            entries = entries
        )
    }

    composable(AppRoute.FoodManagement.route) {
        val foodManagementViewModel: app.aaps.ui.compose.foodManagement.FoodManagementViewModel = hiltViewModel()
        app.aaps.ui.compose.foodManagement.FoodManagementScreen(
            viewModel = foodManagementViewModel,
            onNavigateBack = { navController.safePopBackStack() },
            onNavigateToWizard = { carbs, name ->
                withProtection(ProtectionCheck.Protection.BOLUS) {
                    navController.navigate(AppRoute.WizardDialog.createRoute(carbs = carbs, notes = name))
                }
            }
        )
    }

    composable(AppRoute.SiteRotationManagement.route) {
        SiteRotationManagementScreen(
            viewModel = siteRotationManagementViewModel,
            onClose = { navController.safePopBackStack() },
            siteRotationDef = builtInSearchables.siteRotation
        )
    }

    composable(AppRoute.SetupWizard.route) {
        SetupWizardScreen(
            swDefinition = swDefinition,
            onFinish = {
                preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
                navController.safePopBackStack()
            },
            onBack = { navController.safePopBackStack() },
            onImportSettings = { navController.navigate(AppRoute.ImportSettings.createRoute("LOCAL")) },
            onPluginPreferences = { pluginId -> navController.navigate(AppRoute.PluginPreferences.createRoute(pluginId)) },
            onPluginOpen = { pluginId -> onNavigationRequest(NavigationRequest.Plugin(pluginId), navController) },
            onSetMasterPassword = { navController.navigate(AppRoute.PreferenceScreen.createRoute("protection", StringKey.ProtectionMasterPassword.key)) },
            onManageInsulin = { navController.navigate(AppRoute.InsulinManagement.createRoute()) },
            onManageProfile = { navController.navigate(AppRoute.Profile.createRoute()) },
            onProfileSwitch = { navController.navigate(AppRoute.ProfileActivation.createRoute(0)) },
            onOpenAuthorizedClients = { navController.navigate(AppRoute.AuthorizedClients.route) },
            onPairWithMaster = { navController.navigate(AppRoute.PairWithMaster.route) },
            onOpenNsReceiveSettings = { navController.navigate(AppRoute.PreferenceScreen.createRoute("ns_client_synchronization")) },
            onRunObjectives = {
                val index = activePlugin.getPluginsList().indexOfFirst { it is Objectives }
                if (index >= 0) navController.navigate(AppRoute.PluginContent.createRoute(index))
            },
            onRequestDirectoryAccess = onRequestDirectoryAccess,
            onRequestPermission = onRequestPermission,
            permissionItems = {
                val allGroups = activePlugin.collectAllPermissions(navController.context)
                val missingGroups = activePlugin.collectMissingPermissions(navController.context)
                val missingSets = missingGroups.map { it.permissions.toSet() }.toSet()
                allGroups.map { group -> group to (group.permissions.toSet() !in missingSets) }
            },
            isDirectoryAccessGranted = { prefFileList.isDirectoryAccessGranted() },
            rxBus = rxBus
        )
    }
}

@Composable
private fun PluginContentRoute(
    navController: NavHostController,
    plugin: PluginBase,
    composeContent: ComposablePluginContent,
    onNavigationRequest: (NavigationRequest, NavHostController) -> Unit,
    withProtection: (ProtectionCheck.Protection, () -> Unit) -> Unit,
) {
    val navigateBack: @Composable () -> Unit = {
        IconButton(onClick = { navController.safePopBackStack() }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(app.aaps.core.ui.R.string.back)
            )
        }
    }
    val settingsAction: @Composable RowScope.() -> Unit = {
        IconButton(onClick = {
            withProtection(ElementType.SETTINGS.protection) {
                navController.navigate(
                    AppRoute.PluginPreferences.createRoute(plugin.javaClass.simpleName)
                )
            }
        }) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(app.aaps.core.ui.R.string.settings)
            )
        }
    }
    var toolbarConfig by remember {
        mutableStateOf(
            ToolbarConfig(
                title = plugin.name,
                navigationIcon = navigateBack,
                actions = settingsAction
            )
        )
    }
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(toolbarConfig.title) },
                navigationIcon = { toolbarConfig.navigationIcon() },
                actions = { toolbarConfig.actions(this) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // remember so the lambda identity stays stable across recompositions —
            // otherwise CompositionLocalProvider invalidates every consumer in the subtree
            // on every PluginContentRoute recomposition.
            val navigationRequestLambda = remember(onNavigationRequest, navController) {
                { request: NavigationRequest -> onNavigationRequest(request, navController) }
            }
            CompositionLocalProvider(LocalPluginNavigationRequest provides navigationRequestLambda) {
                composeContent.Render(
                    setToolbarConfig = { config -> toolbarConfig = config },
                    onNavigateBack = { navController.safePopBackStack() },
                    onSettings = {
                        onNavigationRequest(
                            NavigationRequest.PluginPreferences(plugin.javaClass.simpleName),
                            navController
                        )
                    }
                )
            }
        }
    }
}

/**
 * Fallback for routes whose navigation target cannot be resolved (unknown preference key, missing
 * plugin index, …). Replaces the previous behaviour where such routes rendered nothing, leaving the
 * user on a blank, stuck screen: posts an error snackbar via [rxBus] and immediately pops back so
 * the dead route never stays on screen.
 */
@Composable
private fun NavigationErrorFallback(
    rxBus: RxBus,
    message: String,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        rxBus.send(EventShowSnackbar(message, EventShowSnackbar.Type.Error))
        onDismiss()
    }
}

/**
 * Host for the standalone Automation screen — mirrors [PluginContentRoute] but sources its content
 * from [AutomationRuntime.composeContent] instead of a plugin, and opens the settings subscreen via
 * the generic [AppRoute.PreferenceScreen] route. Automation does not use `LocalPluginNavigationRequest`.
 */
@Composable
private fun AutomationContentRoute(
    navController: NavHostController,
    composeContent: ComposablePluginContent,
    withProtection: (ProtectionCheck.Protection, () -> Unit) -> Unit,
) {
    val openSettings = {
        withProtection(ElementType.SETTINGS.protection) {
            navController.navigate(AppRoute.PreferenceScreen.createRoute("automation_settings"))
        }
    }
    val navigateBack: @Composable () -> Unit = {
        IconButton(onClick = { navController.safePopBackStack() }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(app.aaps.core.ui.R.string.back)
            )
        }
    }
    val settingsAction: @Composable RowScope.() -> Unit = {
        IconButton(onClick = openSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(app.aaps.core.ui.R.string.settings)
            )
        }
    }
    val title = stringResource(app.aaps.core.ui.R.string.automation)
    var toolbarConfig by remember {
        mutableStateOf(
            ToolbarConfig(
                title = title,
                navigationIcon = navigateBack,
                actions = settingsAction
            )
        )
    }
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(toolbarConfig.title) },
                navigationIcon = { toolbarConfig.navigationIcon() },
                actions = { toolbarConfig.actions(this) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composeContent.Render(
                setToolbarConfig = { config -> toolbarConfig = config },
                onNavigateBack = { navController.safePopBackStack() },
                onSettings = openSettings
            )
        }
    }
}
