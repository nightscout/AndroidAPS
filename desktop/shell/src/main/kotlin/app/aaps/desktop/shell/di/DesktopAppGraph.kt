package app.aaps.desktop.shell.di

import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.database.AppRepository
import app.aaps.database.di.JvmAppDatabaseBuilder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The real app's object graph on desktop - the counterpart of `AppRootGraph` in `:app` and
 * `IosAppGraph` in `:ios:shell`.
 *
 * The accessors that [app.aaps.appshell.AapsAppRoot] needs are listed below rather than declared,
 * because Metro validates a graph as a whole: adding them before their bindings exist stops the
 * module compiling instead of leaving something that can be grown one binding at a time. The list is
 * kept by declaring them, reading what Metro reports and taking them out again.
 *
 * ## Where this stands
 *
 * The shell now depends on the same 26 modules `:ios:shell` does, so every plugin that registers
 * itself with `@ContributesBinding` is already in the graph. What is left is what no shared module
 * can supply. Measured at **15**, down from 32:
 *
 * **Absent by nature on a desktop, so they want the honest "not on this platform" answer:**
 * `PairedBtDevices`, `LastKnownLocation`, `LocationPermissions`, `LocationServiceController`,
 * `SmsCommunicator`, `FabricPrivacy`, `ReminderScheduler`, `SceneExpiryScheduler`, `UiInteraction`,
 * `WidgetUpdater`.
 *
 * **Implementable, and the next work:** `ImportExportPrefs` (file dialogs), and `NsConnection` and
 * `NsLoadExecutor`, which are HTTP and a socket - a JVM has both, so a follower can genuinely sync.
 *
 * **Needs a port rather than an implementation:** `Autotune`, whose `AutotunePlugin` is arithmetic
 * over treatment history sitting in androidMain, and `LoopNotifier`, an interface whose only
 * implementation is Android notifications with actions.
 *
 * The pattern to follow for the first group is `app.aaps.ios.shell.missing`: nothing returns a
 * plausible value, and nothing is silent.
 */
@DependencyGraph(AppScope::class)
interface DesktopAppGraph {

    /** The app's own database, in the AAPS folder under the user's home directory. */
    @Provides
    @SingleIn(AppScope::class)
    fun repository(): AppRepository = JvmAppDatabaseBuilder().provideAppRepository("aaps-desktop.db")

    /**
     * [CoreObjectsGraph] is a plain binding container rather than a contributed one, so every graph
     * has to include it by name. It holds the QuickWizard / QuickWizardEntry / BolusWizard cycle,
     * broken with deferred providers - the same shape `AppRootGraph` and `IosAppGraph` use.
     */
    @DependencyGraph.Factory
    fun interface Factory {

        fun create(@Includes coreObjects: CoreObjectsGraph): DesktopAppGraph
    }
}
