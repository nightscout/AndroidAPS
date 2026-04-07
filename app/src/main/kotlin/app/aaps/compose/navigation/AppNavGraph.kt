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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PluginPreferencesScreen
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.siteRotation.SiteLocationPickerScreen
import app.aaps.ui.compose.calibrationDialog.CalibrationDialogScreen
import app.aaps.ui.compose.carbsDialog.CarbsDialogScreen
import app.aaps.ui.compose.careDialog.CareDialogScreen
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.extendedBolusDialog.ExtendedBolusDialogScreen
import app.aaps.ui.compose.fillDialog.FillDialogScreen
import app.aaps.ui.compose.insulinDialog.InsulinDialogScreen
import app.aaps.ui.compose.insulinManagement.InsulinManagementScreen
import app.aaps.ui.compose.insulinManagement.InsulinManagementViewModel
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.maintenance.ImportSettingsScreen
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.ImportViewModel
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
import app.aaps.ui.compose.siteRotationDialog.SiteRotationManagementScreen
import app.aaps.ui.compose.siteRotationDialog.SiteRotationSettingsScreen
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
    mainViewModel: MainViewModel,
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
    // Dependencies
    activePlugin: ActivePlugin,
    preferences: Preferences,
    rh: ResourceHelper,
    builtInSearchables: BuiltInSearchables,
    prefFileList: FileListProvider,
    persistenceLayer: PersistenceLayer,
    visibilityContext: PreferenceVisibilityContext,
    // Callbacks
    onNavigationRequest: (NavigationRequest, NavHostController) -> Unit,
    onShowDeliveryError: (comment: String, titleResId: Int) -> Unit,
    withProtection: (ProtectionCheck.Protection, () -> Unit) -> Unit,
    requestEditModeAuthorization: (onGranted: () -> Unit) -> Unit,
    onRefreshPermissions: () -> Unit,
    onExecuteQuickWizard: (guid: String) -> Unit,
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
            showOkCancel = true,
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
            iobUiState = graphViewModel.iobUiState,
            cobUiState = graphViewModel.cobUiState,
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
            iobUiState = graphViewModel.iobUiState,
            cobUiState = graphViewModel.cobUiState,
            onNavigateBack = { navController.safePopBackStack() },
            onShowDeliveryError = { comment ->
                onShowDeliveryError(comment, app.aaps.core.ui.R.string.treatmentdeliveryerror)
            }
        )
    }

    composable(route = AppRoute.TreatmentDialog.route) {
        TreatmentDialogScreen(
            bgInfoState = graphViewModel.bgInfoState,
            iobUiState = graphViewModel.iobUiState,
            cobUiState = graphViewModel.cobUiState,
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
            onActivate = { duration, percentage, timeshift, withTT, notes, timestamp, timeChanged ->
                coroutineScope.launch {
                    val success = profileManagementViewModel.activateProfile(
                        profileIndex = profileIndex,
                        durationMinutes = duration,
                        percentage = percentage,
                        timeshiftHours = timeshift,
                        withTT = withTT,
                        notes = notes,
                        timestamp = timestamp,
                        timeChanged = timeChanged
                    )
                    if (success) {
                        navController.popBackStack(AppRoute.Profile.route, inclusive = false)
                    }
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
        val pluginIndex = backStackEntry.arguments?.getInt("pluginIndex") ?: return@composable
        val plugin = activePlugin.getPluginsList().getOrNull(pluginIndex) ?: return@composable
        val composeContent = plugin.getComposeContent()
        if (composeContent is ComposablePluginContent) {
            PluginContentRoute(
                navController = navController,
                plugin = plugin,
                composeContent = composeContent,
                onNavigationRequest = onNavigationRequest,
                withProtection = withProtection,
            )
        }
    }

    composable(AppRoute.QuickLaunchConfig.route) {
        val quickLaunchConfigViewModel: QuickLaunchConfigViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        QuickLauchConfigScreen(
            viewModel = quickLaunchConfigViewModel,
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.Configuration.route) {
        val configState by configurationViewModel.uiState.collectAsStateWithLifecycle()
        app.aaps.ui.compose.configuration.ConfigurationScreen(
            categories = configState.categories,
            hardwarePumpConfirmation = configState.hardwarePumpConfirmation,
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
            onDismissHardwarePump = { configurationViewModel.dismissHardwarePumpDialog() }
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

    composable(AppRoute.SiteRotationManagement.route) {
        SiteRotationManagementScreen(
            viewModel = siteRotationManagementViewModel,
            onClose = { navController.safePopBackStack() },
            onPreferenceClick = {
                navController.navigate(AppRoute.SiteRotationSettings.route)
            }
        )
    }

    composable(AppRoute.SiteRotationSettings.route) {
        SiteRotationSettingsScreen(
            viewModel = siteRotationManagementViewModel,
            onNavigateBack = { navController.safePopBackStack() }
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
    val pluginSnackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbarHostState provides pluginSnackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(pluginSnackbarHostState) },
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
