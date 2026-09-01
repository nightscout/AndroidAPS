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
 * The shell depends on the same 26 modules `:ios:shell` does, so every plugin that registers itself
 * with `@ContributesBinding` is already in the graph, and the classes in `app.aaps.desktop.shell.platform`
 * answer the platform half. Measured at **3**, down from 32:
 *
 * **Implementable, and the next work:** `ImportExportPrefs`, which is file dialogs.
 *
 * Nightscout sync is done and needs no websocket: `DesktopNsConnection` reports that this platform
 * has none, and the plugin's refresh tick then polls the same REST round a phone runs, through
 * `DesktopNsLoadExecutor`. The socket would only lower latency, and it is not worth what it costs -
 * see `DesktopNsConnection` for the JSON implementation it would drag in.
 *
 * **Needs a port rather than an implementation:** `Autotune`, whose `AutotunePlugin` is arithmetic
 * over treatment history sitting in androidMain, and `LoopNotifier`, an interface whose only
 * implementation is Android notifications with actions.
 *
 * Everything that was "absent by nature" is now answered rather than missing - see
 * `DesktopAutomationInputs` and `DesktopAbsentIntegrations`. Two of that group turned out to be real
 * work rather than refusals: `ReminderScheduler` and `SceneExpiryScheduler` both run on a coroutine
 * timer, and the second one has to, because scene expiry reverts an SMB toggle and a profile switch.
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
