package app.aaps.desktop.shell.di

import app.aaps.database.AppRepository
import app.aaps.database.di.JvmAppDatabaseBuilder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The real app's object graph on desktop - the counterpart of `AppRootGraph` in `:app` and
 * `IosAppGraph` in `:ios:shell`.
 *
 * It opens the database and nothing else yet. The accessors that [app.aaps.appshell.AapsAppRoot]
 * needs are listed below rather than declared, because Metro validates a graph as a whole: adding
 * them before their bindings exist stops the module compiling instead of leaving something that can
 * be grown one binding at a time.
 *
 * ## What the app root asks for, and what desktop still owes it
 *
 * [DesktopPlatformBindings] now answers the platform half, which took the list from 32 bindings to
 * **26**. Declaring the twelve accessors `AapsAppRoot` takes still fails on those, in three groups:
 *
 * **Implementable on desktop, and more easily than on Apple** - the JVM has the libraries:
 * `PasswordHasher`, `PasswordCheck`, `SecureEncrypt` (javax.crypto), `ExportPasswordDataStore`,
 * `ReceiverStatusStore`, `NotificationManager` (a `SystemNotificationPlatform` over `SystemTray`),
 * `ImportExportPrefs`, `CommandExecutionPlatform`.
 *
 * **Absent by nature, so they want the honest "not on this platform" answer** the way the Apple side
 * gives it: `PairedBtDevices`, `LastKnownLocation`, `LocationPermissions`,
 * `LocationServiceController`, `SmsCommunicator`, `FabricPrivacy`, `ReminderScheduler`,
 * `SceneExpiryScheduler`, `UiInteraction`, `NsConnection`, `NsLoadExecutor`.
 *
 * **Loop-side objects a follower does not run**, which the iOS shell also had to satisfy just to
 * build the graph: `Autotune`, `BolusWizard`, `BolusProgressData`, `IobCobCalculator`, `QuickWizard`,
 * `RunningModeGuard`, `LoopNotifier`. Worth questioning rather than implementing - a follower reaches
 * these only through plugin registration, never through anything the UI touches. Same note in
 * `_docs/ios_blockers.md`.
 *
 * The pattern to follow is `app.aaps.ios.shell.missing`: nothing returns a plausible value, and
 * nothing is silent.
 */
@DependencyGraph(AppScope::class)
interface DesktopAppGraph {

    /** The app's own database, in the AAPS folder under the user's home directory. */
    @Provides
    @SingleIn(AppScope::class)
    fun repository(): AppRepository = JvmAppDatabaseBuilder().provideAppRepository("aaps-desktop.db")
}
