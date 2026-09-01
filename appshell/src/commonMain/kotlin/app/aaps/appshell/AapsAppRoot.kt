package app.aaps.appshell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.PasswordHasher
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAppIcon
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalDecimalFormatter
import app.aaps.core.ui.compose.LocalMasterControlAllowed
import app.aaps.core.ui.compose.LocalMasterReachable
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.GlobalDialogHost
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import app.aaps.core.ui.compose.dialogs.PasswordCheckHost
import app.aaps.core.ui.compose.preference.LocalCheckPassword
import app.aaps.core.ui.compose.preference.LocalClearExportPasswordStore
import app.aaps.core.ui.compose.preference.LocalHashPassword
import app.aaps.core.ui.compose.preference.LocalVisibilityContext
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.clientcontrol.ClientControlPendingDialog

/**
 * The app's Compose root: everything that is on screen before any particular screen is.
 *
 * It owns the composition locals, the theme, the splash-to-app switch, and the four things hosted
 * once for the whole app - the snackbar, the dialog host, the password prompt and the client-control
 * pending modal. A platform's entry point calls this and passes [content], which is where that
 * platform wires its own navigation callbacks.
 *
 * Everything it needs is an interface, so this is shared code. The two things that cannot be are
 * passed as composable lambdas: [appIcon] and [splashLogo] are per-build bitmaps, and only the
 * platform knows how to paint them.
 *
 * @param content the app itself, shown once [Config.initProgressFlow] reports done. It receives the
 *   [NavHostController] this root created, because the splash gate and the offline self-heal below
 *   both need to watch the same one.
 * @param onNavControllerReady handed the controller as soon as it exists, for a platform entry point
 *   that has to route from outside the composition - an Android intent, for example.
 * @param onClose leaves the app when initialization failed and there is nothing to show.
 */
@Composable
fun AapsAppRoot(
    config: Config,
    preferences: Preferences,
    dateUtil: DateUtil,
    decimalFormatter: DecimalFormatter,
    profileUtil: ProfileUtil,
    passwordHasher: PasswordHasher,
    passwordCheck: PasswordCheck,
    exportPasswordDataStore: ExportPasswordDataStore,
    visibilityContext: VisibilityContext,
    nsClient: NsClient,
    rxBus: RxBus,
    clientControlActionDispatcher: ClientControlActionDispatcher,
    appIcon: @Composable (Modifier) -> Unit,
    splashLogo: @Composable (Modifier) -> Unit,
    onNavControllerReady: (NavHostController) -> Unit,
    onClose: () -> Unit,
    content: @Composable (NavHostController) -> Unit
) {
    val navController = rememberNavController().also(onNavControllerReady)
    val masterReachable by nsClient.masterReachable.collectAsStateWithLifecycle()
    val masterControlAllowed by nsClient.masterControlAllowed.collectAsStateWithLifecycle()

    // Global self-heal — event-driven, NOT a poll (a timer would keep the CPU awake). Probe once when
    // we go offline, and again on each navigation while offline, so any screen/dialog the user opens
    // re-checks. (The WS reconnect and a failed action also probe; all internally rate-limited.)
    // `masterReachable` is lifecycle-collected and navigation only happens in the foreground, so this
    // never runs in the background.
    LaunchedEffect(masterReachable) {
        if (!masterReachable) nsClient.requestMasterProbe()
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect {
            if (!nsClient.masterReachable.value) nsClient.requestMasterProbe()
        }
    }

    CompositionLocalProvider(
        LocalPreferences provides preferences,
        LocalDateUtil provides dateUtil,
        LocalDecimalFormatter provides decimalFormatter,
        // This build's launcher icon: a flavour specific bitmap, so the shell paints it and shared
        // screens only say where it goes.
        LocalAppIcon provides { modifier -> appIcon(modifier) },
        LocalConfig provides config,
        LocalMasterReachable provides masterReachable,
        LocalMasterControlAllowed provides masterControlAllowed,
        LocalProfileUtil provides profileUtil,
        LocalCheckPassword provides passwordHasher::checkPassword,
        LocalHashPassword provides passwordHasher::hashPassword,
        LocalClearExportPasswordStore provides { exportPasswordDataStore.clearPasswordDataStore() },
        LocalVisibilityContext provides visibilityContext
    ) {
        AapsTheme {
            val rootSnackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(LocalSnackbarHostState provides rootSnackbarHostState) {
                val initProgress by config.initProgressFlow.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = !initProgress.done,
                        exit = fadeOut()
                    ) {
                        LaunchedEffect(Unit) {
                            config.initSnackbarFlow.collect { message ->
                                rootSnackbarHostState.showSnackbar(message)
                            }
                        }
                        SplashScreen(initProgress, splashLogo, onClose)
                    }

                    AnimatedVisibility(
                        visible = initProgress.done,
                        enter = fadeIn()
                    ) {
                        content(navController)
                    }

                    // Root-level snackbar host — subscribes to EventShowSnackbar
                    // and is the single visible SnackbarHost across every screen.
                    GlobalSnackbarHost(
                        rxBus = rxBus,
                        hostState = rootSnackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    // Root-level dialog host — subscribes to EventShowDialog and
                    // renders one modal dialog at a time.
                    GlobalDialogHost(rxBus = rxBus)

                    // Root-level password prompt. Any caller can ask for a password from plain
                    // Kotlin; the dialog appears here, so PasswordCheck needs no Context.
                    PasswordCheckHost(passwordCheck = passwordCheck)

                    // The single app-level pending modal for ANY client-control round-trip
                    // (insulin / scenes / synced-preference edits). Hosted once here, feature-
                    // independent; round-trips are single-in-flight so at most one shows. Applied is
                    // cleared by the dispatcher (silent); Rejected/Unconfirmed stay until dismissed.
                    val pendingAction by clientControlActionDispatcher.pendingAction.collectAsStateWithLifecycle()
                    pendingAction?.let { pending ->
                        if (pending.progress !is ActionProgress.Applied)
                            ClientControlPendingDialog(
                                pending = pending,
                                onDismiss = { clientControlActionDispatcher.dismissActionProgress() }
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(
    progress: InitProgress,
    splashLogo: @Composable (Modifier) -> Unit,
    onClose: () -> Unit
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            splashLogo(Modifier.size(200.dp))
            Spacer(Modifier.height(32.dp))
            val error = progress.error
            if (error != null) {
                Text(
                    text = stringResource(CoreUiStrings.initialization_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClose) {
                    Text(stringResource(CoreUiStrings.close))
                }
            } else {
                Text(
                    text = progress.step.ifEmpty { stringResource(CoreUiStrings.loading) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(16.dp))
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.current.toFloat() / progress.total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .height(4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${progress.current} / ${progress.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .height(4.dp)
                    )
                }
            }
        }
    }
}
