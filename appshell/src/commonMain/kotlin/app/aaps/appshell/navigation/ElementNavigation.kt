package app.aaps.appshell.navigation

import androidx.navigation.NavController
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.ui.compose.careDialog.CareportalEventType
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.fillDialog.FillPreselect

/**
 * Where a tap in the drawer, the toolbar or a search result actually goes.
 *
 * This was inline in `ComposeMainActivity` and nothing in it was Android: it is a mapping from
 * [ElementType] to a route, plus the protection check that has to pass first. Leaving it there meant
 * every other platform had a drawer whose items did nothing - which is exactly what desktop showed
 * the moment the overview was hosted there.
 *
 * Two things genuinely are platform specific and are passed in: launching another app for a CGM, and
 * closing the app. Everything else is shared.
 *
 * @param onOpenCgmApp opens the CGM app by package name. Android only; elsewhere there is no app to
 *   open, and the caller should say so rather than doing nothing quietly.
 * @param onExit closes the window or activity. Called **before** `configBuilder.exitApp`, which is
 *   what the phone did: the UI goes first, then the app records the exit and stops the process.
 */
@Suppress("LongParameterList")
fun handleNavigationRequest(
    request: NavigationRequest,
    navController: NavController,
    mainViewModel: MainViewModel,
    activePlugin: ActivePlugin,
    protectionCheck: ProtectionCheck,
    configBuilder: ConfigBuilder,
    dexcomBoyda: DexcomBoyda,
    onOpenCgmApp: (packageName: String) -> Unit,
    onExit: () -> Unit
) {
    when (request) {
        is NavigationRequest.Element           -> navigateProtected(
            elementType = request.type,
            navController = navController,
            mainViewModel = mainViewModel,
            activePlugin = activePlugin,
            protectionCheck = protectionCheck,
            configBuilder = configBuilder,
            dexcomBoyda = dexcomBoyda,
            onOpenCgmApp = onOpenCgmApp,
            onExit = onExit
        )

        is NavigationRequest.QuickWizard       ->
            withProtection(ElementType.QUICK_WIZARD.protection, protectionCheck) {
                mainViewModel.executeQuickWizard(request.guid)
            }

        is NavigationRequest.Plugin            -> {
            val plugin = activePlugin.getPluginsList()
                .find { it::class.simpleName == request.className } ?: return
            openPlugin(plugin, navController, activePlugin)
        }

        is NavigationRequest.PluginPreferences ->
            withProtection(ElementType.SETTINGS.protection, protectionCheck) {
                navController.navigate(AppRoute.PluginPreferences.createRoute(request.pluginKey))
            }
    }
}

/**
 * Asks for authorization when the element needs it, then navigates.
 *
 * The granted level decides the mode: anything at or above "preferences" opens a management screen
 * for editing, anything less opens it read only. That is why the level is carried through rather
 * than just a yes or no.
 */
@Suppress("LongParameterList")
private fun navigateProtected(
    elementType: ElementType,
    navController: NavController,
    mainViewModel: MainViewModel,
    activePlugin: ActivePlugin,
    protectionCheck: ProtectionCheck,
    configBuilder: ConfigBuilder,
    dexcomBoyda: DexcomBoyda,
    onOpenCgmApp: (packageName: String) -> Unit,
    onExit: () -> Unit
) {
    fun go(mode: ScreenMode) = navigateToElement(
        elementType, navController, mainViewModel, activePlugin, configBuilder, dexcomBoyda, onOpenCgmApp, onExit, mode
    )

    val minLevel = elementType.protection
    if (minLevel == ProtectionCheck.Protection.NONE) {
        go(ScreenMode.EDIT)
        return
    }
    protectionCheck.requestAuthorization(minLevel) { result ->
        result.grantedLevel?.let { granted ->
            val mode = if (granted.level >= ProtectionCheck.Protection.PREFERENCES.level) ScreenMode.EDIT else ScreenMode.PLAY
            go(mode)
        }
    }
}

/** Runs [action] once the protection level is granted, or straight away when none is needed. */
private fun withProtection(
    protection: ProtectionCheck.Protection,
    protectionCheck: ProtectionCheck,
    action: () -> Unit
) {
    when (protection) {
        ProtectionCheck.Protection.NONE        -> action()
        ProtectionCheck.Protection.BOLUS,
        ProtectionCheck.Protection.APPLICATION,
        ProtectionCheck.Protection.MASTER,
        ProtectionCheck.Protection.PREFERENCES -> protectionCheck.requestProtection(protection) { result ->
            if (result == ProtectionResult.GRANTED) action()
        }
    }
}

/** Opens a plugin's own screen, when it has one. Public because search results reach a plugin directly. */
fun openPlugin(plugin: PluginBase, navController: NavController, activePlugin: ActivePlugin) {
    val pluginIndex = activePlugin.getPluginsList().indexOf(plugin)
    if (plugin.hasComposeContent()) navController.navigate(AppRoute.PluginContent.createRoute(pluginIndex))
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
private fun navigateToElement(
    elementType: ElementType,
    navController: NavController,
    mainViewModel: MainViewModel,
    activePlugin: ActivePlugin,
    configBuilder: ConfigBuilder,
    dexcomBoyda: DexcomBoyda,
    onOpenCgmApp: (packageName: String) -> Unit,
    onExit: () -> Unit,
    mode: ScreenMode
) {
    when (elementType) {
        // Navigation screens (drawer)
        ElementType.TREATMENTS              -> navController.navigate(AppRoute.Treatments.route)
        ElementType.STATISTICS,
        ElementType.TDD_CYCLE_PATTERN       -> navController.navigate(AppRoute.Stats.route)

        ElementType.PROFILE_HELPER          -> navController.navigate(AppRoute.ProfileHelper.route)
        ElementType.HISTORY_BROWSER         -> navController.navigate(AppRoute.HistoryBrowser.route)
        ElementType.SETUP_WIZARD            -> navController.navigate(AppRoute.SetupWizard.route)
        ElementType.MAINTENANCE             -> mainViewModel.setShowMaintenanceSheet(true)
        ElementType.CONFIGURATION           -> navController.navigate(AppRoute.Configuration.route)
        ElementType.ABOUT                   -> mainViewModel.setShowAboutDialog(true)

        // Management screens - mode determined by granted auth level
        ElementType.INSULIN_MANAGEMENT      -> navController.navigate(AppRoute.InsulinManagement.createRoute(mode))
        ElementType.PROFILE_MANAGEMENT      -> navController.navigate(AppRoute.Profile.createRoute(mode))
        ElementType.TEMP_TARGET_MANAGEMENT  -> navController.navigate(AppRoute.TempTargetManagement.createRoute(mode))
        ElementType.QUICK_WIZARD_MANAGEMENT -> navController.navigate(AppRoute.QuickWizardManagement.createRoute(mode))
        ElementType.FOOD_MANAGEMENT         -> navController.navigate(AppRoute.FoodManagement.route)
        ElementType.RUNNING_MODE            -> navController.navigate(AppRoute.RunningMode.route)
        ElementType.SCENE_MANAGEMENT        -> navController.navigate(AppRoute.SceneList.route)
        ElementType.AUTOMATION_MANAGEMENT   -> navController.navigate(AppRoute.AutomationList.route)
        ElementType.AUTHORIZED_CLIENTS      -> navController.navigate(AppRoute.AuthorizedClients.route)
        ElementType.PAIR_WITH_MASTER        -> navController.navigate(AppRoute.PairWithMaster.route)
        ElementType.QUICK_LAUNCH_CONFIG     -> navController.navigate(AppRoute.QuickLaunchConfig.route)

        // Treatment dialogs
        ElementType.CARBS                   -> navController.navigate(AppRoute.CarbsDialog.route)
        ElementType.INSULIN                 -> navController.navigate(AppRoute.InsulinDialog.route)
        ElementType.TREATMENT               -> navController.navigate(AppRoute.TreatmentDialog.route)
        ElementType.FILL                    -> navController.navigate(AppRoute.FillDialog.createRoute(FillPreselect.CARTRIDGE_CHANGE.ordinal))
        ElementType.CANNULA_CHANGE          -> navController.navigate(AppRoute.FillDialog.createRoute(FillPreselect.SITE_CHANGE.ordinal))
        ElementType.BOLUS_WIZARD            -> navController.navigate(AppRoute.WizardDialog.createRoute())
        ElementType.TEMP_BASAL              -> navController.navigate(AppRoute.TempBasalDialog.route)
        ElementType.EXTENDED_BOLUS          -> navController.navigate(AppRoute.ExtendedBolusDialog.route)

        // CGM
        ElementType.CGM_XDRIP               -> onOpenCgmApp("com.eveningoutpost.dexdrip")
        ElementType.CGM_DEX                 -> dexcomBoyda.dexcomPackages().forEach { onOpenCgmApp(it) }

        ElementType.CALIBRATION             -> navController.navigate(AppRoute.CalibrationDialog.route)

        // Careportal
        ElementType.BG_CHECK                -> navController.navigate(AppRoute.CareDialog.createRoute(CareportalEventType.BGCHECK.ordinal))
        ElementType.SENSOR_INSERT           -> navController.navigate(AppRoute.CareDialog.createRoute(CareportalEventType.SENSOR_INSERT.ordinal))
        ElementType.BATTERY_CHANGE          -> navController.navigate(AppRoute.CareDialog.createRoute(CareportalEventType.BATTERY_CHANGE.ordinal))
        ElementType.NOTE                    -> navController.navigate(AppRoute.CareDialog.createRoute(CareportalEventType.NOTE.ordinal))
        ElementType.EXERCISE                -> navController.navigate(AppRoute.CareDialog.createRoute(CareportalEventType.EXERCISE.ordinal))
        ElementType.QUESTION                -> navController.navigate(AppRoute.CareDialog.createRoute(CareportalEventType.QUESTION.ordinal))
        ElementType.ANNOUNCEMENT            -> navController.navigate(AppRoute.CareDialog.createRoute(CareportalEventType.ANNOUNCEMENT.ordinal))
        ElementType.SITE_ROTATION           -> navController.navigate(AppRoute.SiteRotationManagement.route)

        // Settings
        ElementType.SETTINGS                -> navController.navigate(AppRoute.Preferences.route)

        // App lifecycle
        ElementType.EXIT                    -> {
            onExit()
            configBuilder.exitApp("Menu", Sources.Aaps, false)
        }

        ElementType.PUMP                    -> openPlugin(activePlugin.activePumpInternal as PluginBase, navController, activePlugin)

        // Non-searchable types - listed explicitly so the compiler catches new enum values
        ElementType.QUICK_WIZARD,
        ElementType.SCENE,
        ElementType.AUTOMATION,
        ElementType.COB,
        ElementType.SENSITIVITY,
        ElementType.USER_ENTRY,
        ElementType.LOOP,
        ElementType.AAPS                    -> {
        }
    }
}
