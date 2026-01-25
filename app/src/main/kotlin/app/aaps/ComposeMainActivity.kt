package app.aaps

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.compose.navigation.AppRoute
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.LocalRxBus
import app.aaps.core.ui.compose.ProtectionHost
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.core.ui.compose.preference.LocalCheckPassword
import app.aaps.core.ui.compose.preference.LocalHashPassword
import app.aaps.core.ui.compose.preference.LocalVisibilityContext
import app.aaps.core.ui.compose.preference.PluginPreferencesScreen
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.search.SearchableItem
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.protection.BiometricCheck
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.activities.RequestDexcomPermissionActivity
import app.aaps.ui.compose.carbsDialog.CarbsDialogScreen
import app.aaps.ui.compose.carbsDialog.CarbsDialogViewModel
import app.aaps.ui.compose.careDialog.CareDialogScreen
import app.aaps.ui.compose.careDialog.CareDialogViewModel
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.fillDialog.FillDialogScreen
import app.aaps.ui.compose.fillDialog.FillDialogViewModel
import app.aaps.ui.compose.fillDialog.FillPreselect
import app.aaps.ui.compose.insulinDialog.InsulinDialogScreen
import app.aaps.ui.compose.insulinDialog.InsulinDialogViewModel
import app.aaps.ui.compose.main.MainMenuItem
import app.aaps.ui.compose.main.MainScreen
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.maintenance.ImportSettingsScreen
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.ImportViewModel
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.overview.automation.AutomationViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.manage.ManageSheetHost
import app.aaps.ui.compose.overview.manage.ManageViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.overview.treatments.TreatmentViewModel
import app.aaps.ui.compose.permissions.PermissionsSheet
import app.aaps.ui.compose.permissions.PermissionsSideEffect
import app.aaps.ui.compose.permissions.PermissionsViewModel
import app.aaps.ui.compose.preferences.AllPreferencesScreen
import app.aaps.ui.compose.preferences.PreferenceScreenView
import app.aaps.ui.compose.profileHelper.ProfileHelperScreen
import app.aaps.ui.compose.profileManagement.ProfileActivationScreen
import app.aaps.ui.compose.profileManagement.ProfileEditorScreen
import app.aaps.ui.compose.profileManagement.ProfileManagementScreen
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileEditorViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileHelperViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import app.aaps.ui.compose.quickWizard.QuickWizardManagementScreen
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeScreen
import app.aaps.ui.compose.stats.StatsScreen
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempTarget.TempTargetManagementScreen
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import app.aaps.ui.compose.treatmentDialog.TreatmentDialogScreen
import app.aaps.ui.compose.treatmentDialog.TreatmentDialogViewModel
import app.aaps.ui.compose.treatments.TreatmentsScreen
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import app.aaps.ui.compose.wizardDialog.WizardDialogScreen
import app.aaps.ui.compose.wizardDialog.WizardDialogViewModel
import app.aaps.ui.search.BuiltInSearchables
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.launch
import javax.inject.Inject

class ComposeMainActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var passwordCheck: PasswordCheck
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var config: Config
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var visibilityContext: PreferenceVisibilityContext
    @Inject lateinit var xDripSource: XDripSource
    @Inject lateinit var dexcomBoyda: DexcomBoyda
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var prefFileList: FileListProvider
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var builtInSearchables: BuiltInSearchables

    // ViewModels
    @Inject lateinit var mainViewModel: MainViewModel
    @Inject lateinit var manageViewModel: ManageViewModel
    @Inject lateinit var maintenanceViewModel: MaintenanceViewModel
    @Inject lateinit var statusViewModel: StatusViewModel
    @Inject lateinit var treatmentViewModel: TreatmentViewModel
    @Inject lateinit var automationViewModel: AutomationViewModel
    @Inject lateinit var graphViewModel: GraphViewModel
    @Inject lateinit var treatmentsViewModel: TreatmentsViewModel
    @Inject lateinit var tempTargetManagementViewModel: TempTargetManagementViewModel
    @Inject lateinit var quickWizardManagementViewModel: QuickWizardManagementViewModel
    @Inject lateinit var statsViewModel: StatsViewModel
    @Inject lateinit var profileHelperViewModel: ProfileHelperViewModel
    @Inject lateinit var profileEditorViewModel: ProfileEditorViewModel
    @Inject lateinit var profileManagementViewModel: ProfileManagementViewModel
    @Inject lateinit var runningModeManagementViewModel: RunningModeManagementViewModel
    @Inject lateinit var careDialogViewModel: CareDialogViewModel
    @Inject lateinit var fillDialogViewModel: FillDialogViewModel
    @Inject lateinit var carbsDialogViewModel: CarbsDialogViewModel
    @Inject lateinit var insulinDialogViewModel: InsulinDialogViewModel
    @Inject lateinit var treatmentDialogViewModel: TreatmentDialogViewModel
    @Inject lateinit var wizardDialogViewModel: WizardDialogViewModel
    @Inject lateinit var importViewModel: ImportViewModel
    @Inject lateinit var searchViewModel: SearchViewModel
    @Inject lateinit var permissionsViewModel: PermissionsViewModel
    @Inject lateinit var configurationViewModel: ConfigurationViewModel

    private val _autoShowNotifications = mutableStateOf(false)
    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onPermissionResultDenied = { denied ->
            permissionsViewModel.onPermissionsDenied(
                deniedPermissions = denied,
                canShowRationale = { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
            )
        }

        setupEventListeners()
        setupWakeLock()

        setContent {
            MainContent()
        }
    }

    @Composable
    private fun MainContent() {
        val navController = rememberNavController()

        CompositionLocalProvider(
            LocalPreferences provides preferences,
            LocalRxBus provides rxBus,
            LocalDateUtil provides dateUtil,
            LocalConfig provides config,
            LocalProfileUtil provides profileUtil,
            LocalCheckPassword provides cryptoUtil::checkPassword,
            LocalHashPassword provides cryptoUtil::hashPassword,
            LocalVisibilityContext provides visibilityContext
        ) {
            AapsTheme {
                // Protection dialog host - handles all protection requests
                ProtectionHost(
                    protectionCheck = protectionCheck,
                    preferences = preferences,
                    checkPassword = cryptoUtil::checkPassword,
                    showBiometric = { activity, titleRes, onGranted, onCancelled, onDenied ->
                        BiometricCheck.biometricPrompt(activity, titleRes, onGranted, onCancelled, onDenied, passwordCheck)
                    }
                )

                // Permissions bottom sheet
                val permState by permissionsViewModel.uiState.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    permissionsViewModel.sideEffect.collect { effect ->
                        when (effect) {
                            is PermissionsSideEffect.RequestPermissions      ->
                                requestMultiplePermissions?.launch(effect.permissions.toTypedArray())

                            is PermissionsSideEffect.LaunchSpecialPermission ->
                                when {
                                    effect.group.permissions.contains(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) ->
                                        try {
                                            callForBatteryOptimization?.launch(null)
                                        } catch (_: ActivityNotFoundException) {
                                            snackbarHostState.showSnackbar(getString(app.aaps.plugins.configuration.R.string.alert_dialog_permission_battery_optimization_failed))
                                        } catch (_: IllegalStateException) {
                                            snackbarHostState.showSnackbar(getString(app.aaps.plugins.configuration.R.string.error_asking_for_permissions))
                                        }

                                    effect.group.permissions.contains(PluginStore.PERMISSION_SELECT_DIRECTORY)                  ->
                                        try {
                                            accessTree?.launch(null)
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(getString(app.aaps.ui.R.string.permission_directory_picker_error))
                                        }

                                    effect.group.permissions.contains(DexcomPlugin.PERMISSION)                                  ->
                                        startActivity(Intent(this@ComposeMainActivity, RequestDexcomPermissionActivity::class.java))

                                    effect.group.permissions.contains(Manifest.permission.POST_NOTIFICATIONS)                   ->
                                        startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                            }
                                        )

                                    effect.group.permissions.contains(Manifest.permission.SCHEDULE_EXACT_ALARM)                 ->
                                        startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                data = "package:$packageName".toUri()
                                            }
                                        )
                                }

                            is PermissionsSideEffect.ShowError               ->
                                snackbarHostState.showSnackbar(effect.message)

                            is PermissionsSideEffect.PermanentlyDenied       -> {
                                val result = snackbarHostState.showSnackbar(
                                    message = getString(app.aaps.ui.R.string.permission_denied_go_to_settings),
                                    actionLabel = getString(app.aaps.ui.R.string.permission_open_settings),
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = "package:$packageName".toUri()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (permState.showSheet) {
                    PermissionsSheet(
                        items = permState.items,
                        snackbarHostState = snackbarHostState,
                        onRequestPermission = { permissionsViewModel.requestPermission(it) },
                        onDismiss = { permissionsViewModel.dismissSheet() }
                    )
                }

                val state by mainViewModel.uiState.collectAsStateWithLifecycle()

                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Main.route
                ) {
                    composable(AppRoute.Main.route) {
                        val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
                        val calcProgress by mainViewModel.calcProgressFlow.collectAsStateWithLifecycle()
                        val notifications by notificationManager.notifications.collectAsStateWithLifecycle()

                        // Pump setup button in bottom bar
                        val pumpPlugin = activePlugin.activePumpInternal as PluginBase
                        val showPumpSetup = !activePlugin.activePump.isInitialized() && pumpPlugin.hasComposeContent()
                        val pumpSetupIcon = if (showPumpSetup) pumpPlugin.pluginDescription.icon ?: Pump else null
                        val pumpSetupLabel = if (showPumpSetup) stringResource(pumpPlugin.pluginDescription.pluginName) else null

                        val onProfileManagement: () -> Unit = {
                            protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                if (result == ProtectionResult.GRANTED) {
                                    navController.navigate(AppRoute.Profile.route)
                                }
                            }
                        }
                        val onTempTarget: () -> Unit = {
                            protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                if (result == ProtectionResult.GRANTED) {
                                    navController.navigate(AppRoute.TempTargetManagement.route)
                                }
                            }
                        }

                        val manageSheetState = ManageSheetHost(
                            manageViewModel = manageViewModel,
                            isSimpleMode = state.isSimpleMode,
                            onProfileManagementClick = onProfileManagement,
                            onTempTargetClick = onTempTarget,
                            onTempBasalClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        uiInteraction.runTempBasalDialog(supportFragmentManager)
                                    }
                                }
                            },
                            onExtendedBolusClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        uiInteraction.showOkCancelDialog(
                                            context = this@ComposeMainActivity,
                                            title = app.aaps.core.ui.R.string.extended_bolus,
                                            message = app.aaps.plugins.main.R.string.ebstopsloop,
                                            ok = { uiInteraction.runExtendedBolusDialog(supportFragmentManager) }
                                        )
                                    }
                                }
                            },
                            onBgCheckClick = {
                                navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.BGCHECK.ordinal))
                            },
                            onNoteClick = {
                                navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.NOTE.ordinal))
                            },
                            onExerciseClick = {
                                navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.EXERCISE.ordinal))
                            },
                            onQuestionClick = {
                                navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.QUESTION.ordinal))
                            },
                            onAnnouncementClick = {
                                navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.ANNOUNCEMENT.ordinal))
                            },
                            onSiteRotationClick = {
                                uiInteraction.runSiteRotationDialog(supportFragmentManager)
                            },
                            onQuickWizardClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.QuickWizardManagement.route)
                                    }
                                }
                            },
                            onActionsError = { comment, title ->
                                uiInteraction.runAlarm(comment, title, app.aaps.core.ui.R.raw.boluserror)
                            },
                        )

                        MainScreen(
                            uiState = state,
                            versionName = mainViewModel.versionName,
                            appIcon = mainViewModel.appIcon,
                            aboutDialogData = if (state.showAboutDialog) {
                                mainViewModel.buildAboutDialogData(getString(R.string.app_name))
                            } else null,
                            manageSheetState = manageSheetState,
                            manageViewModel = manageViewModel,
                            maintenanceViewModel = maintenanceViewModel,
                            statusViewModel = statusViewModel,
                            treatmentViewModel = treatmentViewModel,
                            automationViewModel = automationViewModel,
                            // Search
                            searchUiState = searchState,
                            onSearchQueryChange = { searchViewModel.onQueryChanged(it) },
                            onSearchClear = { searchViewModel.clearQuery() },
                            onSearchActiveChange = { active ->
                                if (active) searchViewModel.onSearchModeActivated()
                                else searchViewModel.onSearchModeDeactivated()
                            },
                            onSearchResultClick = { entry ->
                                handleSearchResultClick(entry, navController)
                            },
                            onMenuClick = { mainViewModel.openDrawer() },
                            onProfileManagementClick = onProfileManagement,
                            onPreferencesClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.Preferences.route)
                                    }
                                }
                            },
                            onMenuItemClick = { menuItem ->
                                handleMenuItemClick(menuItem, navController)
                            },
                            onDrawerClosed = { mainViewModel.closeDrawer() },
                            onSwitchToClassicUi = { switchToClassicUi() },
                            onAboutDialogDismiss = { mainViewModel.setShowAboutDialog(false) },
                            onMaintenanceSheetDismiss = { mainViewModel.setShowMaintenanceSheet(false) },
                            onDirectoryClick = {
                                try {
                                    accessTree?.launch(null)
                                } catch (_: Exception) {
                                    maintenanceViewModel.emitError("Unable to launch activity. This is an Android issue")
                                }
                            },
                            onLaunchBrowser = { url ->
                                try {
                                    val customTabsIntent = CustomTabsIntent.Builder()
                                        .setShowTitle(true)
                                        .build()
                                    customTabsIntent.launchUrl(this@ComposeMainActivity, url.toUri())
                                } catch (_: Exception) {
                                    maintenanceViewModel.emitError("Unable to open browser")
                                }
                            },
                            onBringToForeground = {
                                val intent = Intent(this@ComposeMainActivity, ComposeMainActivity::class.java)
                                    .addFlags(
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                            or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            or Intent.FLAG_ACTIVITY_NO_ANIMATION
                                    )
                                startActivity(intent)
                            },
                            onImportSettingsNavigate = { source ->
                                navController.navigate(AppRoute.ImportSettings.createRoute(source.name))
                            },
                            onRecreateActivity = { recreate() },
                            // Overview status callbacks
                            onSensorInsertClick = {
                                navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.SENSOR_INSERT.ordinal))
                            },
                            onFillClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.FillDialog.createRoute(FillPreselect.SITE_CHANGE.ordinal))
                                    }
                                }
                            },
                            onInsulinChangeClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.FillDialog.createRoute(FillPreselect.CARTRIDGE_CHANGE.ordinal))
                                    }
                                }
                            },
                            onBatteryChangeClick = {
                                navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.BATTERY_CHANGE.ordinal))
                            },
                            // Actions callbacks
                            onRunningModeClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.RunningMode.route)
                                    }
                                }
                            },
                            onTempTargetClick = onTempTarget,
                            onCarbsClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.CarbsDialog.route)
                                    }
                                }
                            },
                            onInsulinClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.InsulinDialog.route)
                                    }
                                }
                            },
                            onTreatmentClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.TreatmentDialog.route)
                                    }
                                }
                            },
                            onQuickWizardClick = { guid ->
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        mainViewModel.executeQuickWizard(this@ComposeMainActivity, guid)
                                    }
                                }
                            },
                            onCalculatorClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.WizardDialog.createRoute())
                                    }
                                }
                            },
                            onCgmClick = {
                                if (xDripSource.isEnabled()) openCgmApp("com.eveningoutpost.dexdrip")
                                else if (dexcomBoyda.isEnabled()) dexcomBoyda.dexcomPackages().forEach { openCgmApp(it) }
                            },
                            onCalibrationClick = if (xDripSource.isEnabled()) {
                                { uiInteraction.runCalibrationDialog(supportFragmentManager) }
                            } else null,
                            // Notifications
                            notifications = notifications,
                            onDismissNotification = { notification ->
                                notificationManager.dismiss(notification.id)
                            },
                            onNotificationActionClick = { notification ->
                                handleNotificationAction(notification.id, navController)
                            },
                            autoShowNotificationSheet = _autoShowNotifications.value,
                            onAutoShowConsumed = { _autoShowNotifications.value = false },
                            showPumpSetup = showPumpSetup,
                            pumpSetupIcon = pumpSetupIcon,
                            pumpSetupLabel = pumpSetupLabel,
                            onPumpSetupClick = {
                                navController.navigate(AppRoute.PumpSetup.route)
                            },
                            permissionsMissing = permState.hasAnyMissing,
                            onPermissionsClick = {
                                permissionsViewModel.showSheet()
                            },
                            calcProgress = calcProgress,
                            graphViewModel = graphViewModel,
                            statusLightsDef = builtInSearchables.statusLights,
                            treatmentButtonsDef = builtInSearchables.treatmentButtons
                        )
                    }

                    composable(AppRoute.Profile.route) {
                        ProfileManagementScreen(
                            viewModel = profileManagementViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onEditProfile = { index ->
                                profileEditorViewModel.selectProfile(index)
                                navController.navigate(AppRoute.ProfileEditor.createRoute(index))
                            },
                            onActivateProfile = { index ->
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.ProfileActivation.createRoute(index))
                                    }
                                }
                            }
                        )
                    }

                    composable(AppRoute.TempTargetManagement.route) {
                        TempTargetManagementScreen(
                            viewModel = tempTargetManagementViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.QuickWizardManagement.route) {
                        QuickWizardManagementScreen(
                            viewModel = quickWizardManagementViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onExecuteClick = { guid ->
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        mainViewModel.executeQuickWizard(this@ComposeMainActivity, guid)
                                    }
                                }
                            }
                        )
                    }

                    composable(AppRoute.RunningMode.route) {
                        RunningModeScreen(
                            viewModel = runningModeManagementViewModel,
                            showOkCancel = true,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = AppRoute.CareDialog.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("eventTypeOrdinal") {
                                type = androidx.navigation.NavType.IntType
                            }
                        )
                    ) { backStackEntry ->
                        val ordinal = backStackEntry.arguments?.getInt("eventTypeOrdinal") ?: 0
                        val eventType = UiInteraction.EventType.entries[ordinal]
                        val vm: CareDialogViewModel = viewModel(
                            factory = daggerViewModel { careDialogViewModel }
                        )
                        CareDialogScreen(
                            viewModel = vm,
                            eventType = eventType,
                            onNavigateBack = { navController.popBackStack() },
                            onShowSiteRotationDialog = {
                                uiInteraction.runSiteRotationDialog(supportFragmentManager)
                            }
                        )
                    }

                    composable(
                        route = AppRoute.FillDialog.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("preselect") {
                                type = androidx.navigation.NavType.IntType
                            }
                        )
                    ) { backStackEntry ->
                        val preselectOrdinal = backStackEntry.arguments?.getInt("preselect") ?: 0
                        val preselect = FillPreselect.entries[preselectOrdinal]
                        val vm: FillDialogViewModel = viewModel(
                            factory = daggerViewModel { fillDialogViewModel }
                        )
                        FillDialogScreen(
                            viewModel = vm,
                            preselect = preselect,
                            fillButtonsDef = builtInSearchables.fillButtons,
                            onNavigateBack = { navController.popBackStack() },
                            onShowSiteRotationDialog = {
                                uiInteraction.runSiteRotationDialog(supportFragmentManager)
                            },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(route = AppRoute.CarbsDialog.route) {
                        val vm: CarbsDialogViewModel = viewModel(
                            factory = daggerViewModel { carbsDialogViewModel }
                        )
                        CarbsDialogScreen(
                            viewModel = vm,
                            carbsButtonsDef = builtInSearchables.carbsButtons,
                            bgInfoState = graphViewModel.bgInfoState,
                            iobUiState = graphViewModel.iobUiState,
                            cobUiState = graphViewModel.cobUiState,
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(route = AppRoute.InsulinDialog.route) {
                        val vm: InsulinDialogViewModel = viewModel(
                            factory = daggerViewModel { insulinDialogViewModel }
                        )
                        InsulinDialogScreen(
                            viewModel = vm,
                            insulinButtonsDef = builtInSearchables.insulinButtons,
                            bgInfoState = graphViewModel.bgInfoState,
                            iobUiState = graphViewModel.iobUiState,
                            cobUiState = graphViewModel.cobUiState,
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(route = AppRoute.TreatmentDialog.route) {
                        val vm: TreatmentDialogViewModel = viewModel(
                            factory = daggerViewModel { treatmentDialogViewModel }
                        )
                        TreatmentDialogScreen(
                            viewModel = vm,
                            bgInfoState = graphViewModel.bgInfoState,
                            iobUiState = graphViewModel.iobUiState,
                            cobUiState = graphViewModel.cobUiState,
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(
                        route = AppRoute.WizardDialog.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("carbs") {
                                type = androidx.navigation.NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            androidx.navigation.navArgument("notes") {
                                type = androidx.navigation.NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val carbs = backStackEntry.arguments?.getString("carbs")?.toIntOrNull()
                        val notes = backStackEntry.arguments?.getString("notes")
                        val vm: WizardDialogViewModel = viewModel(
                            factory = daggerViewModel { wizardDialogViewModel }
                        )
                        WizardDialogScreen(
                            viewModel = vm,
                            wizardSettingsDef = builtInSearchables.wizardSettings,
                            initialCarbs = carbs,
                            initialNotes = notes,
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(
                        route = AppRoute.ImportSettings.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("source") {
                                type = androidx.navigation.NavType.StringType
                            }
                        )
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
                            onClose = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = AppRoute.ProfileActivation.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("profileIndex") {
                                type = androidx.navigation.NavType.IntType
                            }
                        )
                    ) { backStackEntry ->
                        val profileIndex = backStackEntry.arguments?.getInt("profileIndex") ?: 0
                        val profileName = profileManagementViewModel.uiState.value.profileNames.getOrNull(profileIndex) ?: ""
                        val reuseValues = profileManagementViewModel.getReuseValues()

                        ProfileActivationScreen(
                            profileName = profileName,
                            currentPercentage = reuseValues?.first ?: 100,
                            currentTimeshiftHours = reuseValues?.second ?: 0,
                            hasReuseValues = reuseValues != null,
                            showNotesField = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                            initialTimestamp = profileManagementViewModel.dateUtil.nowWithoutMilliseconds(),
                            rh = rh,
                            onNavigateBack = { navController.popBackStack() },
                            onActivate = { duration, percentage, timeshift, withTT, notes, timestamp, timeChanged ->
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
                        )
                    }

                    composable(
                        route = AppRoute.ProfileEditor.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("profileIndex") {
                                type = androidx.navigation.NavType.IntType
                            }
                        )
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
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.Treatments.route) {
                        TreatmentsScreen(
                            viewModel = treatmentsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.Stats.route) {
                        StatsScreen(
                            viewModel = statsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.ProfileHelper.route) {
                        ProfileHelperScreen(
                            viewModel = profileHelperViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.Preferences.route) {
                        AllPreferencesScreen(
                            activePlugin = activePlugin,
                            rh = rh,
                            builtInSearchables = builtInSearchables,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.PumpSetup.route) {
                        val pumpPlugin = activePlugin.activePumpInternal as PluginBase
                        val composeContent = pumpPlugin.getComposeContent()
                        if (composeContent is ComposablePluginContent) {
                            val navigateBack: @Composable () -> Unit = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                                    )
                                }
                            }
                            val settingsAction: @Composable RowScope.() -> Unit = {
                                IconButton(onClick = {
                                    protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                        if (result == ProtectionResult.GRANTED) {
                                            navController.navigate(
                                                AppRoute.PluginPreferences.createRoute(pumpPlugin.javaClass.simpleName)
                                            )
                                        }
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
                                        title = pumpPlugin.name,
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
                                        onNavigateBack = { navController.popBackStack() },
                                        onSettings = {
                                            protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                                if (result == ProtectionResult.GRANTED) {
                                                    navController.navigate(
                                                        AppRoute.PluginPreferences.createRoute(pumpPlugin.javaClass.simpleName)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    composable(AppRoute.Configuration.route) {
                        val configState by configurationViewModel.uiState.collectAsStateWithLifecycle()
                        app.aaps.ui.compose.configuration.ConfigurationScreen(
                            categories = configState.pluginCategories,
                            isSimpleMode = configState.isSimpleMode,
                            pluginStateVersion = configState.pluginStateVersion,
                            onNavigateBack = { navController.popBackStack() },
                            onPluginClick = { plugin -> handlePluginClick(plugin) },
                            onPluginEnableToggle = { plugin, type, enabled ->
                                configurationViewModel.togglePluginEnabled(plugin, type, enabled)
                                permissionsViewModel.refresh(this@ComposeMainActivity)
                            },
                            onPluginPreferencesClick = { plugin ->
                                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.PluginPreferences.createRoute(plugin.javaClass.simpleName))
                                    }
                                }
                            }
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
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(AppRoute.PreferenceScreen.route) { backStackEntry ->
                        val screenKey = backStackEntry.arguments?.getString("screenKey")
                        val highlightKey = backStackEntry.arguments?.getString("highlightKey")
                        val screenDef = screenKey?.let { key ->
                            findScreenDef(key)
                        }
                        if (screenDef != null) {
                            PreferenceScreenView(
                                screenDef = screenDef,
                                highlightKey = highlightKey,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun findScreenDef(key: String): PreferenceSubScreenDef? {
        // Check built-in screens from BuiltInSearchables
        builtInSearchables.getSearchableItems().forEach { item ->
            if (item is SearchableItem.Category && item.screenDef.key == key) {
                return item.screenDef
            }
        }
        // Check plugin screens
        for (plugin in activePlugin.getPluginsList()) {
            val content = plugin.getPreferenceScreenContent()
            if (content is PreferenceSubScreenDef) {
                if (content.key == key) return content
                // Check nested screens
                val nested = findNestedScreen(content, key)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun findNestedScreen(
        screen: PreferenceSubScreenDef,
        key: String
    ): PreferenceSubScreenDef? {
        for (item in screen.items) {
            if (item is PreferenceSubScreenDef) {
                if (item.key == key) return item
                val nested = findNestedScreen(item, key)
                if (nested != null) return nested
            }
        }
        return null
    }

    override fun onResume() {
        super.onResume()
        // Profile and TempTarget state are now updated reactively via OverviewDataCache flows
        manageViewModel.refreshState()
        permissionsViewModel.refresh(this)
        // Auto-show notification sheet if urgent notifications exist
        if (notificationManager.notifications.value.any { it.level == NotificationLevel.URGENT }) {
            _autoShowNotifications.value = true
        }
    }

    override fun updateButtons() {
        // Called by activity result callbacks (battery optimization, runtime permissions)
        permissionsViewModel.refresh(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun setupEventListeners() {
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event ->
                           // Handle screen wake lock
                           if (event.isChanged(BooleanKey.OverviewKeepScreenOn.key)) {
                               setupWakeLock()
                           }
                           // Language change requires full restart to reload resources
                           if (event.isChanged(StringKey.GeneralLanguage.key)) {
                               finish()
                           }
                       }, fabricPrivacy::logException)
    }

    private fun setupWakeLock() {
        val keepScreenOn = preferences.get(BooleanKey.OverviewKeepScreenOn)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun switchToClassicUi() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun handleNotificationAction(notificationId: NotificationId, navController: NavController) {
        when (notificationId) {
            NotificationId.IDENTIFICATION_NOT_SET  ->
                navController.navigate(AppRoute.PreferenceScreen.createRoute("data_choice_setting", StringKey.MaintenanceIdentification.key))

            NotificationId.MASTER_PASSWORD_NOT_SET ->
                navController.navigate(AppRoute.PreferenceScreen.createRoute("protection", StringKey.ProtectionMasterPassword.key))

            NotificationId.AAPS_DIR_NOT_SELECTED   ->
                try {
                    accessTree?.launch(null)
                } catch (_: Exception) {
                }

            else                                   -> Unit
        }
    }

    private fun handleMenuItemClick(menuItem: MainMenuItem, navController: NavController) {
        when (menuItem) {
            is MainMenuItem.Preferences,
            is MainMenuItem.PluginPreferences -> {
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        navController.navigate(AppRoute.Preferences.route)
                    }
                }
            }

            is MainMenuItem.Treatments        -> navController.navigate(AppRoute.Treatments.route)

            is MainMenuItem.HistoryBrowser    -> {
                startActivity(Intent(this, HistoryBrowseActivity::class.java).setAction("app.aaps.ComposeMainActivity"))
            }

            is MainMenuItem.SetupWizard       -> {
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        startActivity(Intent(this, SetupWizardActivity::class.java).setAction("app.aaps.ComposeMainActivity"))
                    }
                }
            }

            is MainMenuItem.Stats             -> navController.navigate(AppRoute.Stats.route)
            is MainMenuItem.ProfileHelper     -> navController.navigate(AppRoute.ProfileHelper.route)

            is MainMenuItem.Maintenance       -> {
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        mainViewModel.setShowMaintenanceSheet(true)
                    }
                }
            }

            is MainMenuItem.Configuration     -> {
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        navController.navigate(AppRoute.Configuration.route)
                    }
                }
            }

            is MainMenuItem.About             -> mainViewModel.setShowAboutDialog(true)

            is MainMenuItem.Exit              -> {
                finish()
                configBuilder.exitApp("Menu", Sources.Aaps, false)
            }
        }
    }

    private fun openCgmApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: throw ActivityNotFoundException()
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            aapsLogger.debug("Error opening CGM app: $packageName")
        }
    }

    private fun handleSearchResultClick(entry: SearchIndexEntry, navController: NavController) {
        // Keep search active so user can return to results with back button

        when (val item = entry.item) {
            is SearchableItem.Category   -> {
                // Navigate to the specific preference screen
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        navController.navigate(AppRoute.PreferenceScreen.createRoute(item.screenDef.key))
                    }
                }
            }

            is SearchableItem.Preference -> {
                // Navigate to parent screen with preference highlighted
                val screenKey = item.parentScreenKey
                if (screenKey != null) {
                    protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                        if (result == ProtectionResult.GRANTED) {
                            navController.navigate(AppRoute.PreferenceScreen.createRoute(screenKey, item.preferenceKey.key))
                        }
                    }
                } else {
                    // Fallback to all preferences if no parent screen
                    protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                        if (result == ProtectionResult.GRANTED) {
                            navController.navigate(AppRoute.Preferences.route)
                        }
                    }
                }
            }

            is SearchableItem.Dialog     -> {
                // Handle dialog navigation based on dialog key
                when (item.dialogKey) {
                    // Drawer menu screens
                    "treatments"              -> navController.navigate(AppRoute.Treatments.route)
                    "stats",
                    "stats_cycle_pattern"     -> navController.navigate(AppRoute.Stats.route)

                    "profile_helper"          -> navController.navigate(AppRoute.ProfileHelper.route)

                    "history_browser"         -> {
                        startActivity(Intent(this@ComposeMainActivity, uiInteraction.historyBrowseActivity))
                    }

                    "setup_wizard"            -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                startActivity(Intent(this@ComposeMainActivity, SetupWizardActivity::class.java))
                            }
                        }
                    }

                    "about"                   -> mainViewModel.setShowAboutDialog(true)

                    // Action screens
                    "running_mode"            -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.RunningMode.route)
                            }
                        }
                    }

                    "temp_target_management"  -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.TempTargetManagement.route)
                            }
                        }
                    }

                    "quick_wizard_management" -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.QuickWizardManagement.route)
                            }
                        }
                    }

                    // Dialogs
                    "carbs_dialog"            -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.CarbsDialog.route)
                            }
                        }
                    }

                    "insulin_dialog"          -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.InsulinDialog.route)
                            }
                        }
                    }

                    "treatment_dialog"        -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.TreatmentDialog.route)
                            }
                        }
                    }

                    "fill_dialog"             -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.FillDialog.createRoute(0))
                            }
                        }
                    }

                    "wizard_dialog"           -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                navController.navigate(AppRoute.WizardDialog.createRoute())
                            }
                        }
                    }

                    // CareDialog events
                    "care_bgcheck"            -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.BGCHECK.ordinal))
                    "care_sensor_insert"      -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.SENSOR_INSERT.ordinal))
                    "care_battery_change"     -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.BATTERY_CHANGE.ordinal))
                    "care_note"               -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.NOTE.ordinal))
                    "care_exercise"           -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.EXERCISE.ordinal))
                    "care_question"           -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.QUESTION.ordinal))
                    "care_announcement"       -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.ANNOUNCEMENT.ordinal))
                }
            }

            is SearchableItem.Plugin     -> {
                // Handle plugin click - same as drawer plugin click
                handlePluginClick(item.pluginRef)
            }

            is SearchableItem.Wiki       -> {
                // Open wiki page in default browser
                val intent = Intent(Intent.ACTION_VIEW, item.url.toUri())
                startActivity(intent)
            }
        }
    }

    private fun handlePluginClick(plugin: PluginBase) {
        if (!plugin.hasFragment() && !plugin.hasComposeContent()) {
            return
        }
        lifecycleScope.launch {
            val pluginIndex = activePlugin.getPluginsList().indexOf(plugin)
            startActivity(
                Intent(this@ComposeMainActivity, SingleFragmentActivity::class.java)
                    .setAction(this@ComposeMainActivity::class.simpleName)
                    .putExtra("plugin", pluginIndex)
            )
        }
    }

}

/**
 * Creates a [ViewModelProvider.Factory] that returns a Dagger-provided ViewModel instance.
 * Used with [viewModel] composable to scope Dagger-injected ViewModels to NavBackStackEntry,
 * so they survive configuration changes but are recreated on navigation.
 */
@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> daggerViewModel(provider: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V = provider() as V
    }
