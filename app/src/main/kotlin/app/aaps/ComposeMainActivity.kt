package app.aaps

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.aaps.compose.navigation.AppRoute
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventThemeSwitch
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.ProtectionHost
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.LocalCheckPassword
import app.aaps.core.ui.compose.preference.LocalHashPassword
import app.aaps.core.ui.compose.preference.LocalVisibilityContext
import app.aaps.core.ui.compose.preference.PluginPreferencesScreen
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.search.SearchableItem
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.protection.BiometricCheck
import app.aaps.plugins.configuration.activities.OptimizationPermissionContract
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.maintenance.PrefsFileContract
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.activities.RequestDexcomPermissionActivity
import app.aaps.ui.compose.automationSheet.AutomationViewModel
import app.aaps.ui.compose.carbsDialog.CarbsDialogScreen
import app.aaps.ui.compose.careDialog.CareDialogScreen
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.extendedBolusDialog.ExtendedBolusDialogScreen
import app.aaps.ui.compose.fillDialog.FillDialogScreen
import app.aaps.ui.compose.fillDialog.FillPreselect
import app.aaps.ui.compose.insulinDialog.InsulinDialogScreen
import app.aaps.ui.compose.main.MainScreen
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.maintenance.ImportSettingsScreen
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.ImportViewModel
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetHost
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.permissionsSheet.PermissionsSheet
import app.aaps.ui.compose.permissionsSheet.PermissionsSideEffect
import app.aaps.ui.compose.permissionsSheet.PermissionsViewModel
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
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchConfigViewModel
import app.aaps.ui.compose.quickWizard.QuickWizardManagementScreen
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeScreen
import app.aaps.ui.compose.stats.StatsScreen
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempBasalDialog.TempBasalDialogScreen
import app.aaps.ui.compose.tempTarget.TempTargetManagementScreen
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import app.aaps.ui.compose.treatmentDialog.TreatmentDialogScreen
import app.aaps.ui.compose.treatments.TreatmentsScreen
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.compose.wizardDialog.WizardDialogScreen
import app.aaps.ui.search.BuiltInSearchables
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ComposeMainActivity : AppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var uiInteraction: UiInteraction
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
    @Inject lateinit var localProfileManager: LocalProfileManager

    private var accessTree: ActivityResultLauncher<Uri?>? = null
    private var callForPrefFile: ActivityResultLauncher<Void?>? = null
    private var callForBatteryOptimization: ActivityResultLauncher<Void?>? = null
    private var requestMultiplePermissions: ActivityResultLauncher<Array<String>>? = null
    private var onPermissionResultDenied: ((List<String>) -> Unit)? = null

    // ViewModels (Hilt-provided via @HiltViewModel)
    private val mainViewModel: MainViewModel by viewModels()
    private val manageViewModel: ManageViewModel by viewModels()
    private val maintenanceViewModel: MaintenanceViewModel by viewModels()
    private val statusViewModel: StatusViewModel by viewModels()
    private val treatmentViewModel: TreatmentViewModel by viewModels()
    private val automationViewModel: AutomationViewModel by viewModels()
    private val graphViewModel: GraphViewModel by viewModels()
    private val treatmentsViewModel: TreatmentsViewModel by viewModels()
    private val tempTargetManagementViewModel: TempTargetManagementViewModel by viewModels()
    private val quickWizardManagementViewModel: QuickWizardManagementViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val profileHelperViewModel: ProfileHelperViewModel by viewModels()
    private val profileEditorViewModel: ProfileEditorViewModel by viewModels()
    private val profileManagementViewModel: ProfileManagementViewModel by viewModels()
    private val runningModeManagementViewModel: RunningModeManagementViewModel by viewModels()
    private val importViewModel: ImportViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val permissionsViewModel: PermissionsViewModel by viewModels()
    private val configurationViewModel: ConfigurationViewModel by viewModels()

    private var navController: NavHostController? = null
    private val _autoShowNotifications = mutableStateOf(false)
    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Theme switch listener (from base class)
        disposable += rxBus.toObservable(EventThemeSwitch::class.java).subscribe { recreate() }

        // Activity result launchers (from base class)
        accessTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                val lastPathSegment = uri.lastPathSegment ?: ""
                val pathAfterColon = if (lastPathSegment.contains(":")) lastPathSegment.substringAfterLast(":") else lastPathSegment
                val directoryName = pathAfterColon.substringAfterLast("/", pathAfterColon)
                val managedSubdirectories = listOf("preferences", "extra", "exports", "temp")
                if (managedSubdirectories.any { it.equals(directoryName, ignoreCase = true) }) {
                    uiInteraction.showError(
                        this,
                        rh.gs(app.aaps.plugins.configuration.R.string.warning_wrong_directory_selected),
                        rh.gs(app.aaps.plugins.configuration.R.string.warning_wrong_directory_message, directoryName)
                    )
                    return@registerForActivityResult
                }
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                preferences.put(StringKey.AapsDirectoryUri, uri.toString())
            }
        }
        callForPrefFile = registerForActivityResult(PrefsFileContract()) {
            importExportPrefs.doImportSharedPreferences(this)
        }
        callForBatteryOptimization = registerForActivityResult(OptimizationPermissionContract()) {
            updateButtons()
        }
        requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val denied = mutableListOf<String>()
            permissions.entries.forEach {
                aapsLogger.info(LTag.CORE, "Permission ${it.key} ${it.value}")
                if (!it.value) denied.add(it.key)
            }
            if (denied.isNotEmpty()) onPermissionResultDenied?.invoke(denied)
            updateButtons()
        }

        onPermissionResultDenied = { denied ->
            permissionsViewModel.onPermissionsDenied(
                deniedPermissions = denied,
                canShowRationale = { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
            )
        }

        observePreferences()

        setContent {
            MainContent()
        }
    }

    @Composable
    private fun MainContent() {
        val navController = rememberNavController().also { this.navController = it }

        CompositionLocalProvider(
            LocalPreferences provides preferences,
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
                        val quickLaunchItems by mainViewModel.quickLaunchItems.collectAsStateWithLifecycle()

                        // Pump setup button in bottom bar
                        val pumpPlugin = activePlugin.activePump as PluginBase
                        val showPumpSetup = !activePlugin.activePump.isInitialized() && pumpPlugin.hasComposeContent()
                        val pumpSetupClassName = if (showPumpSetup) pumpPlugin.javaClass.simpleName else null
                        val pumpSetupIcon = if (showPumpSetup) pumpPlugin.pluginDescription.icon ?: Pump else null
                        val pumpSetupLabel = if (showPumpSetup) stringResource(pumpPlugin.pluginDescription.pluginName) else null

                        val manageSheetState = ManageSheetHost(
                            manageViewModel = manageViewModel,
                            isSimpleMode = state.isSimpleMode,
                            onNavigate = { request -> handleNavigationRequest(request, navController) },
                            onActionsError = { comment, title ->
                                uiInteraction.runAlarm(comment, title, app.aaps.core.ui.R.raw.boluserror)
                            },
                        )

                        // Authorization failed dialog
                        if (state.showAuthFailedDialog) {
                            OkDialog(
                                title = "",
                                message = stringResource(R.string.authorizationfailed),
                                onDismiss = {
                                    mainViewModel.setShowAuthFailedDialog(false)
                                    finish()
                                }
                            )
                        }


                        MainScreen(
                            mainViewModel = mainViewModel,
                            uiState = state,
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
                            onNavigate = { request -> handleNavigationRequest(request, navController) },
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
                            pumpSetupClassName = pumpSetupClassName,
                            pumpSetupIcon = pumpSetupIcon,
                            pumpSetupLabel = pumpSetupLabel,
                            permissionsMissing = permState.hasAnyMissing,
                            onPermissionsClick = {
                                permissionsViewModel.showSheet()
                            },
                            // Toolbar
                            quickLaunchItems = quickLaunchItems,
                            onQuickLaunchActionClick = { action -> handleQuickLaunchAction(action, navController) },
                            calcProgress = calcProgress,
                            graphViewModel = graphViewModel,
                            statusLightsDef = builtInSearchables.statusLights,
                            treatmentButtonsDef = builtInSearchables.treatmentButtons
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
                            onNavigateBack = { navController.popBackStack() },
                            onRequestEditMode = {
                                withProtection(ProtectionCheck.Protection.PREFERENCES) {
                                    profileManagementViewModel.setScreenMode(ScreenMode.EDIT)
                                }
                            },
                            onEditProfile = { index ->
                                profileEditorViewModel.selectProfile(index)
                                navController.navigate(AppRoute.ProfileEditor.createRoute(index))
                            },
                            onActivateProfile = { index ->
                                withProtection(ProtectionCheck.Protection.BOLUS) {
                                    navController.navigate(AppRoute.ProfileActivation.createRoute(index))
                                }
                            }
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
                            onNavigateBack = { navController.popBackStack() },
                            onRequestEditMode = {
                                withProtection(ProtectionCheck.Protection.PREFERENCES) {
                                    tempTargetManagementViewModel.setScreenMode(ScreenMode.EDIT)
                                }
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
                            onNavigateBack = { navController.popBackStack() },
                            onRequestEditMode = {
                                withProtection(ProtectionCheck.Protection.PREFERENCES) {
                                    quickWizardManagementViewModel.setScreenMode(ScreenMode.EDIT)
                                }
                            },
                            onExecuteClick = { guid ->
                                withProtection(ElementType.QUICK_WIZARD.protection) {
                                    mainViewModel.executeQuickWizard(this@ComposeMainActivity, guid)
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
                            navArgument("eventTypeOrdinal") {
                                type = NavType.IntType
                            }
                        )
                    ) {
                        CareDialogScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onShowSiteRotationDialog = {
                                uiInteraction.runSiteRotationDialog(supportFragmentManager)
                            }
                        )
                    }

                    composable(
                        route = AppRoute.FillDialog.route,
                        arguments = listOf(
                            navArgument("preselect") {
                                type = NavType.IntType
                            }
                        )
                    ) {
                        FillDialogScreen(
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
                        CarbsDialogScreen(
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
                        InsulinDialogScreen(
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
                        TreatmentDialogScreen(
                            bgInfoState = graphViewModel.bgInfoState,
                            iobUiState = graphViewModel.iobUiState,
                            cobUiState = graphViewModel.cobUiState,
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(route = AppRoute.TempBasalDialog.route) {
                        TempBasalDialogScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(route = AppRoute.ExtendedBolusDialog.route) {
                        ExtendedBolusDialogScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
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
                            onNavigateBack = { navController.popBackStack() },
                            onShowDeliveryError = { comment ->
                                uiInteraction.runAlarm(comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                            }
                        )
                    }

                    composable(
                        route = AppRoute.ImportSettings.route,
                        arguments = listOf(
                            navArgument("source") {
                                type = NavType.StringType
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
                            navArgument("profileIndex") {
                                type = NavType.IntType
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
                            navArgument("profileIndex") {
                                type = NavType.IntType
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

                    composable(
                        route = AppRoute.PluginContent.route,
                        arguments = listOf(
                            navArgument("pluginIndex") {
                                type = NavType.IntType
                            }
                        )
                    ) { backStackEntry ->
                        val pluginIndex = backStackEntry.arguments?.getInt("pluginIndex") ?: return@composable
                        val plugin = activePlugin.getPluginsList().getOrNull(pluginIndex) ?: return@composable
                        val composeContent = plugin.getComposeContent()
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
                                    composeContent.Render(
                                        setToolbarConfig = { config -> toolbarConfig = config },
                                        onNavigateBack = { navController.popBackStack() },
                                        onSettings = {
                                            handleNavigationRequest(
                                                NavigationRequest.PluginPreferences(plugin.javaClass.simpleName),
                                                navController
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    composable(AppRoute.QuickLaunchConfig.route) {
                        val quickLaunchConfigViewModel: QuickLaunchConfigViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                        QuickLauchConfigScreen(
                            viewModel = quickLaunchConfigViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.Configuration.route) {
                        val configState by configurationViewModel.uiState.collectAsStateWithLifecycle()
                        app.aaps.ui.compose.configuration.ConfigurationScreen(
                            categories = configState.pluginCategories,
                            isSimpleMode = configState.isSimpleMode,
                            pluginStateVersion = configState.pluginStateVersion,
                            hardwarePumpConfirmation = configState.hardwarePumpConfirmation,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigate = { request -> handleNavigationRequest(request, navController) },
                            onPluginEnableToggle = { plugin, type, enabled ->
                                configurationViewModel.togglePluginEnabled(plugin, type, enabled)
                                permissionsViewModel.refresh(this@ComposeMainActivity)
                            },
                            onConfirmHardwarePump = {
                                configurationViewModel.confirmHardwarePumpSwitch()
                                permissionsViewModel.refresh(this@ComposeMainActivity)
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

    private var isProtectionCheckActive = false

    override fun onResume() {
        super.onResume()
        // Profile and TempTarget state are now updated reactively via OverviewDataCache flows
        manageViewModel.refreshState()
        permissionsViewModel.refresh(this)
        // Auto-show notification sheet if urgent notifications exist
        if (notificationManager.notifications.value.any { it.level == NotificationLevel.URGENT }) {
            _autoShowNotifications.value = true
        }
        // APPLICATION protection — lock out unauthorized users
        if (!isProtectionCheckActive) {
            isProtectionCheckActive = true
            protectionCheck.requestProtection(ProtectionCheck.Protection.APPLICATION) { result ->
                isProtectionCheckActive = false
                if (result != ProtectionResult.GRANTED) {
                    mainViewModel.setShowAuthFailedDialog(true)
                }
            }
        }
    }

    private fun updateButtons() {
        // Called by activity result callbacks (battery optimization, runtime permissions)
        permissionsViewModel.refresh(this)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CloudConstants.CLOUD_IMPORT_REQUEST_CODE && resultCode == RESULT_OK) {
            importExportPrefs.doImportSharedPreferences(this)
        }
    }

    override fun onDestroy() {
        disposable.clear()
        accessTree = null
        callForPrefFile = null
        callForBatteryOptimization = null
        requestMultiplePermissions = null
        onPermissionResultDenied = null
        super.onDestroy()
    }

    private fun observePreferences() {
        // Wake lock: initial value applies on startup, subsequent changes update the flag
        lifecycleScope.launch {
            preferences.observe(BooleanKey.OverviewKeepScreenOn).collect { setupWakeLock() }
        }
        // Language change requires full restart to reload resources
        lifecycleScope.launch {
            preferences.observe(StringKey.GeneralLanguage).drop(1).collect { finish() }
        }
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

    private fun handleQuickLaunchAction(action: QuickLaunchAction, navController: NavController) {
        when (action) {
            is QuickLaunchAction.StaticAction      -> navigateProtected(action.elementType, navController)

            // Dynamic actions — execution-based, not navigation
            is QuickLaunchAction.QuickWizardAction -> withProtection(ElementType.QUICK_WIZARD.protection) {
                mainViewModel.executeQuickWizard(this, action.guid)
            }

            is QuickLaunchAction.AutomationAction  -> mainViewModel.requestAutomationConfirmation(action.automationId)

            is QuickLaunchAction.TempTargetPreset  -> withProtection(ElementType.TEMP_TARGET_MANAGEMENT_PLAY.protection) {
                mainViewModel.requestTempTargetPresetConfirmation(action.presetId)
            }

            is QuickLaunchAction.ProfileAction     -> withProtection(ProtectionCheck.Protection.BOLUS) {
                mainViewModel.requestProfileConfirmation(action.profileName, action.percentage, action.durationMinutes)
            }

            is QuickLaunchAction.PluginAction      -> {
                val pluginIndex = activePlugin.getPluginsList().indexOfFirst { it.javaClass.simpleName == action.className }
                if (pluginIndex >= 0) navController.navigate(AppRoute.PluginContent.createRoute(pluginIndex))
            }
        }
    }

    private fun handleNavigationRequest(request: NavigationRequest, navController: NavController) {
        when (request) {
            is NavigationRequest.Element           -> navigateProtected(request.type, navController)
            is NavigationRequest.QuickWizard       -> withProtection(ElementType.QUICK_WIZARD.protection) {
                mainViewModel.executeQuickWizard(this@ComposeMainActivity, request.guid)
            }

            is NavigationRequest.Plugin            -> {
                val plugin = activePlugin.getPluginsList()
                    .find { it.javaClass.simpleName == request.className } ?: return
                handlePluginClick(plugin)
            }

            is NavigationRequest.PluginPreferences -> withProtection(ElementType.SETTINGS.protection) {
                navController.navigate(AppRoute.PluginPreferences.createRoute(request.pluginKey))
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

    /**
     * Navigate to [elementType] after verifying its protection level.
     * Combines protection check + navigation in one call — the standard entry point for all
     * ElementType-based navigation. Special cases (confirm dialogs, parameterized routes) still
     * use [withProtection] + manual navigation.
     */
    private fun navigateProtected(elementType: ElementType, navController: NavController) {
        withProtection(elementType.protection) {
            navigateToElement(elementType, navController)
        }
    }

    /**
     * Execute [action] after verifying protection level.
     * Protection level is defined once in [ElementType] — no manual lookup needed at call sites.
     */
    private fun withProtection(protection: ProtectionCheck.Protection, action: () -> Unit) {
        when (protection) {
            ProtectionCheck.Protection.NONE        -> action()
            ProtectionCheck.Protection.BOLUS,
            ProtectionCheck.Protection.APPLICATION,
            ProtectionCheck.Protection.PREFERENCES -> protectionCheck.requestProtection(protection) { result ->
                if (result == ProtectionResult.GRANTED) action()
            }
        }
    }

    private fun handleSearchResultClick(entry: SearchIndexEntry, navController: NavController) {
        // Keep search active so user can return to results with back button

        when (val item = entry.item) {
            is SearchableItem.Category   -> withProtection(ProtectionCheck.Protection.PREFERENCES) {
                navController.navigate(AppRoute.PreferenceScreen.createRoute(item.screenDef.key))
            }

            is SearchableItem.Preference -> withProtection(ProtectionCheck.Protection.PREFERENCES) {
                val screenKey = item.parentScreenKey
                if (screenKey != null) {
                    navController.navigate(AppRoute.PreferenceScreen.createRoute(screenKey, item.preferenceKey.key))
                } else {
                    navController.navigate(AppRoute.Preferences.route)
                }
            }

            is SearchableItem.Dialog     -> navigateProtected(item.elementType, navController)

            is SearchableItem.Plugin     -> {
                handlePluginClick(item.pluginRef)
            }

            is SearchableItem.Wiki       -> {
                val intent = Intent(Intent.ACTION_VIEW, item.url.toUri())
                startActivity(intent)
            }
        }
    }

    /**
     * Navigate to an [ElementType] destination. Protection is handled by the caller.
     * No `else` — compiler catches missing enum values.
     */
    private fun navigateToElement(elementType: ElementType, navController: NavController) {
        when (elementType) {
            // Navigation screens (drawer)
            ElementType.TREATMENTS                   -> navController.navigate(AppRoute.Treatments.route)
            ElementType.STATISTICS,
            ElementType.TDD_CYCLE_PATTERN            -> navController.navigate(AppRoute.Stats.route)

            ElementType.PROFILE_HELPER               -> navController.navigate(AppRoute.ProfileHelper.route)
            ElementType.HISTORY_BROWSER              -> startActivity(Intent(this@ComposeMainActivity, uiInteraction.historyBrowseActivity))
            ElementType.SETUP_WIZARD                 -> startActivity(Intent(this@ComposeMainActivity, SetupWizardActivity::class.java))
            ElementType.MAINTENANCE                  -> mainViewModel.setShowMaintenanceSheet(true)
            ElementType.CONFIGURATION                -> navController.navigate(AppRoute.Configuration.route)
            ElementType.ABOUT                        -> mainViewModel.setShowAboutDialog(true)

            // Management screens
            ElementType.PROFILE_MANAGEMENT_PLAY      -> navController.navigate(AppRoute.Profile.createRoute(ScreenMode.PLAY))
            ElementType.PROFILE_MANAGEMENT_EDIT      -> navController.navigate(AppRoute.Profile.createRoute(ScreenMode.EDIT))
            ElementType.TEMP_TARGET_MANAGEMENT_PLAY  -> navController.navigate(AppRoute.TempTargetManagement.createRoute(ScreenMode.PLAY))
            ElementType.TEMP_TARGET_MANAGEMENT_EDIT  -> navController.navigate(AppRoute.TempTargetManagement.createRoute(ScreenMode.EDIT))
            ElementType.QUICK_WIZARD_MANAGEMENT_PLAY -> navController.navigate(AppRoute.QuickWizardManagement.createRoute(ScreenMode.PLAY))
            ElementType.QUICK_WIZARD_MANAGEMENT_EDIT -> navController.navigate(AppRoute.QuickWizardManagement.createRoute(ScreenMode.EDIT))
            ElementType.RUNNING_MODE                 -> navController.navigate(AppRoute.RunningMode.route)
            ElementType.QUICK_LAUNCH_CONFIG          -> navController.navigate(AppRoute.QuickLaunchConfig.route)

            // Treatment dialogs
            ElementType.CARBS                        -> navController.navigate(AppRoute.CarbsDialog.route)
            ElementType.INSULIN                      -> navController.navigate(AppRoute.InsulinDialog.route)
            ElementType.TREATMENT                    -> navController.navigate(AppRoute.TreatmentDialog.route)
            ElementType.FILL                         -> navController.navigate(AppRoute.FillDialog.createRoute(FillPreselect.CARTRIDGE_CHANGE.ordinal))
            ElementType.CANNULA_CHANGE               -> navController.navigate(AppRoute.FillDialog.createRoute(FillPreselect.SITE_CHANGE.ordinal))
            ElementType.BOLUS_WIZARD                 -> navController.navigate(AppRoute.WizardDialog.createRoute())
            ElementType.TEMP_BASAL                   -> navController.navigate(AppRoute.TempBasalDialog.route)
            ElementType.EXTENDED_BOLUS               -> navController.navigate(AppRoute.ExtendedBolusDialog.route)

            // CGM
            ElementType.CGM_XDRIP                    -> openCgmApp("com.eveningoutpost.dexdrip")
            ElementType.CGM_DEX                      -> dexcomBoyda.dexcomPackages().forEach { openCgmApp(it) }

            ElementType.CALIBRATION                  -> uiInteraction.runCalibrationDialog(supportFragmentManager)

            // Careportal
            ElementType.BG_CHECK                     -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.BGCHECK.ordinal))
            ElementType.SENSOR_INSERT                -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.SENSOR_INSERT.ordinal))
            ElementType.BATTERY_CHANGE               -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.BATTERY_CHANGE.ordinal))
            ElementType.NOTE                         -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.NOTE.ordinal))
            ElementType.EXERCISE                     -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.EXERCISE.ordinal))
            ElementType.QUESTION                     -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.QUESTION.ordinal))
            ElementType.ANNOUNCEMENT                 -> navController.navigate(AppRoute.CareDialog.createRoute(UiInteraction.EventType.ANNOUNCEMENT.ordinal))
            ElementType.SITE_ROTATION                -> uiInteraction.runSiteRotationDialog(supportFragmentManager)

            // Settings
            ElementType.SETTINGS                     -> navController.navigate(AppRoute.Preferences.route)

            // App lifecycle
            ElementType.EXIT                         -> {
                finish()
                configBuilder.exitApp("Menu", Sources.Aaps, false)
            }

            // Non-searchable types — listed explicitly so the compiler catches new enum values
            ElementType.QUICK_WIZARD,
            ElementType.AUTOMATION,
            ElementType.PUMP,
            ElementType.COB,
            ElementType.SENSITIVITY,
            ElementType.USER_ENTRY,
            ElementType.LOOP,
            ElementType.AAPS                         -> {
            }
        }
    }

    private fun handlePluginClick(plugin: PluginBase) {
        val pluginIndex = activePlugin.getPluginsList().indexOf(plugin)
        if (plugin.hasComposeContent()) {
            navController?.navigate(AppRoute.PluginContent.createRoute(pluginIndex))
        } else if (plugin.hasFragment()) {
            startActivity(
                Intent(this, SingleFragmentActivity::class.java)
                    .setAction(this::class.simpleName)
                    .putExtra("plugin", pluginIndex)
            )
        }
    }
}

