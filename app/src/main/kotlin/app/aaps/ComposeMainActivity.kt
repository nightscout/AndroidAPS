package app.aaps

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.aaps.appshell.AapsAppRoot
import app.aaps.appshell.navigation.AppRoute
import app.aaps.appshell.navigation.ElementNavigator
import app.aaps.appshell.navigation.appNavGraph
import app.aaps.appshell.navigation.handleNotificationAction
import app.aaps.appshell.navigation.handleQuickLaunchAction
import app.aaps.appshell.navigation.handleSearchResultClick
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.ui.compose.MetroViewModelFactoryOwner
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.pump.PumpActivityDialog
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.utils.isRunningRealPumpTest
import app.aaps.implementation.plugin.PluginPermissionsImpl
import app.aaps.implementation.protection.BiometricCheck
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.configuration.setupwizard.SWDefinition
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.activities.RequestDexcomPermissionActivity
import app.aaps.ui.compose.configuration.ConfigurationViewModel
import app.aaps.ui.compose.insulinManagement.InsulinManagementViewModel
import app.aaps.ui.compose.loopSheet.LoopActionViewModel
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.main.OverviewScreen
import app.aaps.ui.compose.maintenance.ImportViewModel
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.permissionsSheet.PermissionsSheet
import app.aaps.ui.compose.permissionsSheet.PermissionsSideEffect
import app.aaps.ui.compose.permissionsSheet.PermissionsViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileEditorViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileHelperViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickWizard.viewmodels.QuickWizardManagementViewModel
import app.aaps.ui.compose.runningMode.RunningModeManagementViewModel
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationManagementViewModel
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.BuiltInSearchables
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import app.aaps.core.ui.R as CoreUiR

class ComposeMainActivity : MetroAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var passwordCheck: PasswordCheck
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var pluginPermissions: PluginPermissions
    @Inject lateinit var nsClient: NsClient
    @Inject lateinit var clientControlActionDispatcher: ClientControlActionDispatcher
    @Inject lateinit var automationRuntime: AutomationRuntime
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var swDefinition: SWDefinition
    @Inject lateinit var config: Config
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var visibilityContext: VisibilityContext
    @Inject lateinit var dexcomBoyda: DexcomBoyda
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var prefFileList: FileListProvider
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var builtInSearchables: BuiltInSearchables
    @Inject lateinit var bolusProgressData: BolusProgressData
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var bgQualityCheck: BgQualityCheck
    @Inject lateinit var objectives: Objectives
    @Inject lateinit var graphViewModelFactory: GraphViewModel.Factory
    @Inject lateinit var chipsViewModelFactory: ChipsViewModel.Factory
    @Inject lateinit var overviewDataCache: OverviewDataCache

    private var accessTree: ActivityResultLauncher<Uri?>? = null
    private var requestMultiplePermissions: ActivityResultLauncher<Array<String>>? = null
    private var onPermissionResultDenied: ((List<String>) -> Unit)? = null

    /**
     * The factory `by viewModels()` uses.
     */
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (applicationContext as MetroViewModelFactoryOwner).metroViewModelFactory

    // View models, built by Metro - each carries @ContributesIntoMap and @ViewModelKey.
    private val mainViewModel: MainViewModel by viewModels()
    private val manageViewModel: ManageViewModel by viewModels()
    private val maintenanceViewModel: MaintenanceViewModel by viewModels()
    private val statusViewModel: StatusViewModel by viewModels()
    private val treatmentViewModel: TreatmentViewModel by viewModels()
    private val scenesViewModel: ScenesViewModel by viewModels()
    private val loopActionViewModel: LoopActionViewModel by viewModels()
    private val graphViewModel: GraphViewModel by viewModels {
        viewModelFactory { initializer { graphViewModelFactory.create(overviewDataCache, fullWindow = false) } }
    }
    private val chipsViewModel: ChipsViewModel by viewModels {
        viewModelFactory { initializer { chipsViewModelFactory.create(overviewDataCache) } }
    }
    private val treatmentsViewModel: TreatmentsViewModel by viewModels()
    private val insulinManagementViewModel: InsulinManagementViewModel by viewModels()
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
    private val siteRotationManagementViewModel: SiteRotationManagementViewModel by viewModels()

    private val pumpCommunicationStatus by lazy {
        PumpCommunicationStatus(rxBus, commandQueue, rh, lifecycleScope)
    }
    private var navController: NavHostController? = null
    private val _autoShowNotifications = mutableStateOf(false)
    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bar icon color is kept in sync with the AAPS-effective theme reactively
        // from inside AapsTheme via a SideEffect on WindowInsetsControllerCompat,
        // so a plain enableEdgeToEdge() here is enough.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Activity result launchers (from base class)
        accessTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                val lastPathSegment = uri.lastPathSegment ?: ""
                val pathAfterColon = if (lastPathSegment.contains(":")) lastPathSegment.substringAfterLast(":") else lastPathSegment
                val directoryName = pathAfterColon.substringAfterLast("/", pathAfterColon)
                val managedSubdirectories = listOf("preferences", "extra", "exports", "temp")
                if (managedSubdirectories.any { it.equals(directoryName, ignoreCase = true) }) {
                    rxBus.send(
                        EventShowDialog.Error(
                            title = rh.gs(app.aaps.plugins.configuration.R.string.warning_wrong_directory_selected),
                            message = rh.gs(app.aaps.plugins.configuration.R.string.warning_wrong_directory_message, directoryName)
                        )
                    )
                    return@registerForActivityResult
                }
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                preferences.put(StringKey.AapsDirectoryUri, uri.toString())
            }
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
        // LocalActivity, not a cast of LocalContext: that context is usually a ContextWrapper, where
        // `as? FragmentActivity` is null - and then the biometric prompt cannot be shown at all.
        // Read here so the prompt lambdas below can capture it; the shared host no longer takes one.
        val activity = LocalActivity.current as? FragmentActivity
        AapsAppRoot(
            config = config,
            preferences = preferences,
            dateUtil = dateUtil,
            decimalFormatter = decimalFormatter,
            profileUtil = profileUtil,
            passwordHasher = cryptoUtil,
            passwordCheck = passwordCheck,
            protectionCheck = protectionCheck,
            // The biometric prompt is the only platform-specific part of protection. The host used
            // to be handed the activity; it captures it here instead, which is what let the host
            // itself move to commonMain.
            showBiometric = { title, onGranted, onCancelled, onDenied ->
                if (activity != null) BiometricCheck.biometricPrompt(activity, title, rxBus, onGranted, onCancelled, onDenied, passwordCheck)
                else onCancelled()
            },
            showBiometricSimple = { title, onSuccess, onFallback, onCancel ->
                if (activity != null) BiometricCheck.biometricPromptSimple(activity, title, rxBus, onSuccess, onFallback, onCancel)
                else onFallback()
            },
            exportPasswordDataStore = exportPasswordDataStore,
            visibilityContext = visibilityContext,
            nsClient = nsClient,
            rxBus = rxBus,
            clientControlActionDispatcher = clientControlActionDispatcher,
            // The two per-build bitmaps the shared root cannot paint itself.
            appIcon = { modifier -> Image(painterResource(iconsProvider.getIcon()), null, modifier) },
            splashLogo = { modifier -> Image(painterResource(CoreUiR.drawable.splash_logo), null, modifier) },
            // The Activity keeps a reference so an incoming intent can route without the composition.
            onNavControllerReady = { navController = it },
            onClose = { finish() },
            content = { navController -> AppContent(navController) }
        )
    }

    @SuppressLint("BatteryLife")
    @Composable
    private fun AppContent(navController: NavHostController) {
        // Trigger initial refresh when app content first appears (after init completes)
        LaunchedEffect(Unit) { refreshOnResume() }

        // Track last navigated route as a Crashlytics custom key for crash reports
        DisposableEffect(navController) {
            val listener = NavController.OnDestinationChangedListener { _, dest, _ ->
                FirebaseCrashlytics.getInstance().setCustomKey("last_route", dest.route ?: "unknown")
            }
            navController.addOnDestinationChangedListener(listener)
            onDispose { navController.removeOnDestinationChangedListener(listener) }
        }

        // Auto-launch setup wizard on first run
        LaunchedEffect(Unit) {
            if (!preferences.get(BooleanNonKey.GeneralSetupWizardProcessed) && !isRunningRealPumpTest()) {
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        navController.navigate(AppRoute.SetupWizard.route)
                    }
                }
            }
        }

        // Permissions bottom sheet
        val permState by permissionsViewModel.uiState.collectAsStateWithLifecycle()

        // Sheet-scoped snackbar state (separate from the root host so messages
        // triggered from the permissions flow render inside the modal sheet,
        // and never duplicate on the activity's root host).
        val permissionsSnackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            permissionsViewModel.sideEffect.collect { effect ->
                when (effect) {
                    is PermissionsSideEffect.RequestPermissions ->
                        requestMultiplePermissions?.launch(effect.permissions.toTypedArray())

                    is PermissionsSideEffect.LaunchSpecialPermission ->
                        when {
                            effect.group.permissions.contains(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) ->
                                try {
                                    startActivity(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = "package:$packageName".toUri()
                                        }
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    permissionsSnackbarHostState.showSnackbar(getString(app.aaps.plugins.configuration.R.string.alert_dialog_permission_battery_optimization_failed))
                                }

                            effect.group.permissions.contains(PluginPermissionsImpl.PERMISSION_SELECT_DIRECTORY)        ->
                                try {
                                    accessTree?.launch(null)
                                } catch (_: Exception) {
                                    permissionsSnackbarHostState.showSnackbar(getString(app.aaps.ui.R.string.permission_directory_picker_error))
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

                            effect.group.permissions.contains(PluginPermissionsImpl.PERMISSION_NOTIFICATION_LISTENER)   ->
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }

                    is PermissionsSideEffect.ShowError ->
                        permissionsSnackbarHostState.showSnackbar(effect.message)

                    is PermissionsSideEffect.PermanentlyDenied -> {
                        val result = permissionsSnackbarHostState.showSnackbar(
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
                snackbarHostState = permissionsSnackbarHostState,
                onRequestPermission = { permissionsViewModel.requestPermission(it) },
                onDismiss = { permissionsViewModel.dismissSheet() }
            )
        }

        val state by mainViewModel.uiState.collectAsStateWithLifecycle()
        val bolusState by bolusProgressData.state.collectAsStateWithLifecycle()
        val pumpStatusBanner by pumpCommunicationStatus.statusBannerFlow.collectAsStateWithLifecycle()
        val pumpQueueStatus by pumpCommunicationStatus.queueStatusFlow.collectAsStateWithLifecycle()

        NavHost(
            navController = navController,
            startDestination = AppRoute.Main.route
        ) {
            appNavGraph(
                navController = navController,
                insulinManagementViewModel = insulinManagementViewModel,
                profileManagementViewModel = profileManagementViewModel,
                profileEditorViewModel = profileEditorViewModel,
                profileHelperViewModel = profileHelperViewModel,
                tempTargetManagementViewModel = tempTargetManagementViewModel,
                quickWizardManagementViewModel = quickWizardManagementViewModel,
                runningModeManagementViewModel = runningModeManagementViewModel,
                importViewModel = importViewModel,
                configurationViewModel = configurationViewModel,
                treatmentsViewModel = treatmentsViewModel,
                statsViewModel = statsViewModel,
                siteRotationManagementViewModel = siteRotationManagementViewModel,
                graphViewModel = graphViewModel,
                chipsViewModel = chipsViewModel,
                swDefinition = swDefinition,
                rxBus = rxBus,
                activePlugin = activePlugin,
                pluginPermissions = pluginPermissions,
                automationRuntime = automationRuntime,
                preferences = preferences,
                rh = rh,
                builtInSearchables = builtInSearchables,
                configBuilder = configBuilder,
                prefFileList = prefFileList,
                persistenceLayer = persistenceLayer,
                visibilityContext = visibilityContext,
                onNavigationRequest = { request, nc -> handleNavigationRequest(request, nc) },
                onShowDeliveryError = { comment, title ->
                    uiInteraction.runAlarm(comment, rh.gs(title), AlarmSound.BOLUS_ERROR)
                },
                withProtection = { protection, action -> navigator(navController).guarded(protection, action) },
                requestEditModeAuthorization = { onGranted ->
                    protectionCheck.requestAuthorization(ProtectionCheck.Protection.PREFERENCES) { result ->
                        if (result.grantedLevel != null) onGranted()
                    }
                },
                onRefreshPermissions = { permissionsViewModel.refresh() },
                onExecuteQuickWizard = { guid -> mainViewModel.executeQuickWizard(guid) },
                onRequestDirectoryAccess = {
                    try {
                        accessTree?.launch(null)
                    } catch (_: Exception) {
                    }
                },
                onRequestPermission = { group -> permissionsViewModel.requestPermission(group) },
                findScreenDef = { key -> findScreenDef(key) },
                overview = {
                    OverviewScreen(
                        mainViewModel = mainViewModel,
                        manageViewModel = manageViewModel,
                        maintenanceViewModel = maintenanceViewModel,
                        statusViewModel = statusViewModel,
                        treatmentViewModel = treatmentViewModel,
                        scenesViewModel = scenesViewModel,
                        loopActionViewModel = loopActionViewModel,
                        searchViewModel = searchViewModel,
                        permissionsViewModel = permissionsViewModel,
                        graphViewModel = graphViewModel,
                        chipsViewModel = chipsViewModel,
                        activePlugin = activePlugin,
                        config = config,
                        objectives = objectives,
                        bgQualityCheck = bgQualityCheck,
                        notificationManager = notificationManager,
                        uiInteraction = uiInteraction,
                        builtInSearchables = builtInSearchables,
                        bolusProgressData = bolusProgressData,
                        clientControlActionDispatcher = clientControlActionDispatcher,
                        commandQueue = commandQueue,
                        pumpCommunicationStatus = pumpCommunicationStatus,
                        appName = getString(R.string.app_name),
                        authorizationFailedMessage = getString(R.string.authorizationfailed),
                        onNavigate = { request -> handleNavigationRequest(request, navController) },
                        onSearchResultClick = { entry -> handleSearchResultClick(entry, navController) },
                        onNotificationActionClick = { notification -> handleNotificationAction(notification.id, navController) },
                        onQuickLaunchActionClick = { action -> handleQuickLaunchAction(action, navController) },
                        onImportSettingsNavigate = { source -> navController.navigate(AppRoute.ImportSettings.createRoute(source.name)) },
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
                        onRecreateActivity = { recreate() },
                        onAuthorizationFailed = { finish() },
                        autoShowNotificationSheet = _autoShowNotifications.value,
                        onAutoShowConsumed = { _autoShowNotifications.value = false }
                    )
                },
            )
        }

        // Modal bolus progress overlay — shown above everything for standard bolus
        bolusState?.let { state ->
            if (!state.isSMB) {
                val pumpStatus = pumpStatusBanner?.text ?: ""
                val queueStatus = pumpQueueStatus
                PumpActivityDialog(
                    bolusState = state,
                    pumpStatus = pumpStatus,
                    queueStatus = queueStatus,
                    isModal = true,
                    onStop = {
                        if (config.AAPSCLIENT) {
                            clientControlActionDispatcher.stopBolus()
                            bolusProgressData.stopPressed()
                        } else {
                            commandQueue.cancelAllBoluses(null)
                        }
                    },
                    // Only reachable via the stalled-state Dismiss button (client/follower): hides the
                    // local mirror dialog. Delivery belongs to the master — this does not touch the pump.
                    onDismiss = { bolusProgressData.clear() }
                )
            }
        }
    }

    private val pluginScreenDefsCache: List<PreferenceSubScreenDef> by lazy {
        activePlugin.getPluginsList().mapNotNull { it.getPreferenceScreenContent() as? PreferenceSubScreenDef }
    }

    private fun findScreenDef(key: String): PreferenceSubScreenDef? {
        // Check built-in screens from BuiltInSearchables (including nested subscreens)
        builtInSearchables.getSearchableItems().forEach { item ->
            if (item is SearchableItem.Category) {
                if (item.screenDef.key == key) return item.screenDef
                val nested = findNestedScreen(item.screenDef, key)
                if (nested != null) return nested
            }
        }
        // Check plugin screens (including nested subscreens) — cached to avoid walking all plugins on every lookup
        for (content in pluginScreenDefsCache) {
            if (content.key == key) return content
            val nested = findNestedScreen(content, key)
            if (nested != null) return nested
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

    private fun refreshOnResume() {
        manageViewModel.refreshState()
        permissionsViewModel.refresh()
        if (notificationManager.notifications.value.any { it.level.priority <= NotificationLevel.IMPORTANT.priority }) {
            _autoShowNotifications.value = true
        }
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

    override fun onResume() {
        super.onResume()
        if (!config.appInitialized) return
        refreshOnResume()
    }

    private fun updateButtons() {
        permissionsViewModel.refresh()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onDestroy() {
        disposable.clear()
        accessTree = null
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
            preferences.observe(StringKey.GeneralLanguage).drop(1).collect { recreate() }
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

    /**
     * The shared navigator, built with the four actions only Android can perform.
     *
     * The mapping from an element, a search result, a notification or a quick launch tile to a
     * route lives in `:appshell` so every platform behaves the same; what differs is opening a CGM
     * app, opening a browser, launching the directory picker and finishing the activity.
     */
    private fun navigator(navController: NavController) = ElementNavigator(
        navController = navController,
        mainViewModel = mainViewModel,
        activePlugin = activePlugin,
        protectionCheck = protectionCheck,
        configBuilder = configBuilder,
        dexcomBoyda = dexcomBoyda,
        onOpenCgmApp = { packageName -> openCgmApp(packageName) },
        onExit = { finish() },
        onRequestDirectoryAccess = {
            try {
                accessTree?.launch(null)
            } catch (_: Exception) {
            }
        },
        onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    )

    private fun handleNavigationRequest(request: NavigationRequest, navController: NavController) {
        navigator(navController).handleNavigationRequest(request)
    }

    private fun handleQuickLaunchAction(action: QuickLaunchAction, navController: NavController) {
        navigator(navController).handleQuickLaunchAction(action)
    }

    private fun handleNotificationAction(notificationId: NotificationId, navController: NavController) {
        navigator(navController).handleNotificationAction(notificationId)
    }

    private fun handleSearchResultClick(entry: SearchIndexEntry, navController: NavController) {
        navigator(navController).handleSearchResultClick(entry)
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
     * Navigate to [elementType] using hierarchical authorization.
     * For management screens, the granted level determines the screen mode
     * (PLAY for BOLUS, EDIT for PREFERENCES or higher).
     */

    /**
     * Execute [action] after verifying protection level.
     * Protection level is defined once in [ElementType] — no manual lookup needed at call sites.
     */

    /**
     * Navigate to an [ElementType] destination. Protection is handled by the caller.
     * No `else` — compiler catches missing enum values.
     */

}

