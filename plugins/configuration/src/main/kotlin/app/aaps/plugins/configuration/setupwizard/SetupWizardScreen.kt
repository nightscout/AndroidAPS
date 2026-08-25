package app.aaps.plugins.configuration.setupwizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.core.ui.compose.pump.StepProgressIndicator
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.plugins.configuration.R
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SetupWizardScreen(
    swDefinition: SWDefinition,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onImportSettings: () -> Unit,
    onPluginPreferences: (pluginId: String) -> Unit,
    onPluginOpen: (pluginId: String) -> Unit,
    onSetMasterPassword: () -> Unit,
    onManageInsulin: () -> Unit,
    onManageProfile: () -> Unit,
    onProfileSwitch: () -> Unit,
    onOpenAuthorizedClients: () -> Unit,
    onPairWithMaster: () -> Unit,
    onOpenNsReceiveSettings: () -> Unit,
    onRunObjectives: () -> Unit,
    onRequestDirectoryAccess: () -> Unit,
    onRequestPermission: (app.aaps.core.interfaces.plugin.PermissionGroup) -> Unit,
    permissionItems: () -> List<Pair<app.aaps.core.interfaces.plugin.PermissionGroup, Boolean>>,
    isDirectoryAccessGranted: () -> Boolean,
    rxBus: RxBus,
    startPage: Int = 0
) {
    DisposableEffect(Unit) {
        swDefinition.onImportSettings = onImportSettings
        swDefinition.onPluginPreferences = onPluginPreferences
        swDefinition.onPluginOpen = onPluginOpen
        swDefinition.onSetMasterPassword = onSetMasterPassword
        swDefinition.onManageInsulin = onManageInsulin
        swDefinition.onManageProfile = onManageProfile
        swDefinition.onProfileSwitch = onProfileSwitch
        swDefinition.onOpenAuthorizedClients = onOpenAuthorizedClients
        swDefinition.onPairWithMaster = onPairWithMaster
        swDefinition.onOpenNsReceiveSettings = onOpenNsReceiveSettings
        swDefinition.onRunObjectives = onRunObjectives
        swDefinition.onRequestDirectoryAccess = onRequestDirectoryAccess
        swDefinition.onRequestPermission = onRequestPermission
        swDefinition.permissionItems = permissionItems
        swDefinition.isDirectoryAccessGranted = isDirectoryAccessGranted
        onDispose {
            swDefinition.onImportSettings = null
            swDefinition.onPluginPreferences = null
            swDefinition.onPluginOpen = null
            swDefinition.onSetMasterPassword = null
            swDefinition.onManageInsulin = null
            swDefinition.onManageProfile = null
            swDefinition.onProfileSwitch = null
            swDefinition.onOpenAuthorizedClients = null
            swDefinition.onPairWithMaster = null
            swDefinition.onOpenNsReceiveSettings = null
            swDefinition.onRunObjectives = null
            swDefinition.onRequestDirectoryAccess = null
            swDefinition.onRequestPermission = null
            swDefinition.permissionItems = null
            swDefinition.isDirectoryAccessGranted = null
        }
    }
    val screens = remember(swDefinition) { swDefinition.getScreens() }
    var currentPage by rememberSaveable { mutableIntStateOf(startPage) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Trigger recomposition on RxBus events
    var updateTick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val disposable = CompositeDisposable()
        disposable += rxBus.toObservable(EventSWUpdate::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { updateTick++ }
        disposable += rxBus.toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { updateTick++ }
        disposable += rxBus.toObservable(EventSWRLStatus::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { updateTick++ }
        disposable += rxBus.toObservable(EventSWSyncStatus::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { updateTick++ }
        onDispose { disposable.clear() }
    }

    // Read updateTick to subscribe to recomposition
    @Suppress("UNUSED_EXPRESSION") updateTick

    // Calculate visible screens off the main thread. Some screen visibility predicates do blocking
    // I/O (e.g. reading/parsing exported preference files, runBlocking profile lookups) which would
    // otherwise stall composition and trigger an ANR. Until the first async pass completes, all
    // screens are treated as visible; the previous result is retained across recomputations.
    val visibleScreens by produceState(initialValue = screens, screens, updateTick) {
        value = withContext(Dispatchers.IO) {
            screens.filter { it.visibility == null || it.visibility?.invoke() == true }
        }
    }
    val visibleIndex = remember(currentPage, visibleScreens) {
        val currentScreen = screens.getOrNull(currentPage)
        visibleScreens.indexOf(currentScreen).coerceAtLeast(0)
    }

    // Scan from the current page using the precomputed visible set (instead of re-invoking
    // visibility() and its I/O). Preserves the original forward/backward semantics: skip hidden
    // screens, and stay on the current page when there is no visible screen in that direction.
    fun nextPage(): Int {
        var page = currentPage + 1
        while (page < screens.size) {
            if (screens[page] in visibleScreens) return page
            page++
        }
        return currentPage
    }

    fun previousPage(): Int {
        var page = currentPage - 1
        while (page >= 0) {
            if (screens[page] in visibleScreens) return page
            page--
        }
        return currentPage
    }

    val currentScreen = screens.getOrNull(currentPage)
    val isFirstPage = currentPage == 0
    val isLastPage = currentPage == nextPage()
    val canProceed = currentScreen?.let { screen ->
        screen.validator == null || screen.validator?.invoke() == true || screen.skippable
    } ?: false

    // Back handler
    BackHandler {
        if (isFirstPage) {
            showExitDialog = true
        } else {
            currentPage = previousPage()
        }
    }

    if (showExitDialog) {
        OkCancelDialog(
            title = stringResource(app.aaps.core.ui.R.string.confirmation),
            message = stringResource(R.string.exitwizard),
            onConfirm = {
                showExitDialog = false
                onBack()
            },
            onDismiss = { showExitDialog = false }
        )
    }

    ProvidePreferenceTheme {
        Scaffold(
            topBar = {
                AapsTopAppBar(
                    title = { Text(currentScreen?.getHeaderCompose() ?: stringResource(app.aaps.core.ui.R.string.nav_setupwizard)) },
                    navigationIcon = {
                        IconButton(onClick = { showExitDialog = true }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(app.aaps.core.ui.R.string.close))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                StepProgressIndicator(
                    totalSteps = visibleScreens.size,
                    currentStep = visibleIndex
                )

                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 4 })
                            .togetherWith(fadeOut() + slideOutHorizontally { -it / 4 })
                    },
                    label = "setupWizardStepTransition"
                ) { page ->
                    val screen = screens.getOrNull(page)
                    WizardStepLayout(
                        primaryButton = when {
                            isLastPage && canProceed -> WizardButton(
                                text = stringResource(R.string.setupwizard_finish),
                                onClick = onFinish
                            )

                            canProceed               -> WizardButton(
                                text = stringResource(R.string.next_button),
                                onClick = { currentPage = nextPage() }
                            )

                            else                     -> null
                        },
                        secondaryButton = if (!isFirstPage) WizardButton(
                            text = stringResource(R.string.previous_button),
                            onClick = { currentPage = previousPage() }
                        ) else null
                    ) {
                        screen?.let {
                            it.Compose()
                        }
                    }
                }
            }
        } // Scaffold
    } // ProvidePreferenceTheme
}
