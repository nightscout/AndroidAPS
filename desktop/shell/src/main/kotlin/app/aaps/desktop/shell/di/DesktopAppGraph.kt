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
 * Declaring the twelve accessors `AapsAppRoot` takes pulls in **32 bindings** with no desktop
 * answer. They fall into three groups, and only the first is really work:
 *
 * **Platform pieces desktop has to supply itself** - the equivalent of `IosPlatformBindings`:
 * `AAPSLogger`, `TextResolver`, `Config`, a `KeyValueStore` for preferences, and a JVM actual for
 * `DateFormatPlatform`. Everything they feed - `RxBusImpl`, `DateUtilImpl`, `LImpl`,
 * `CommonNotificationManager` - is already shared code that compiles for desktop.
 *
 * **Bindings that exist only in androidMain** and need a desktop implementation or an honest stub:
 * `PasswordCheck`, `PasswordHasher`, `SecureEncrypt`, `ExportPasswordDataStore`,
 * `ReceiverStatusStore`, `NotificationManager`, `FabricPrivacy`, `NsConnection`, `NsLoadExecutor`.
 *
 * **Loop-side objects a follower does not run**, which the iOS shell also had to satisfy just to
 * build the graph: `APSResult`, `WizardBolusExecutor`, `BolusProgressData`, `CommandQueue`,
 * `PumpSync`, `ProfileFunction`, `SceneAutomationApi`, `ActiveSceneSync`, `L`. Worth questioning
 * rather than implementing - see the same note in `_docs/ios_blockers.md`.
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
