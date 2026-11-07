package app.aaps.pump.dana.di

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the shared Dana module, taken from the app's own graph.
 *
 * ## Why a test reaches through the graph at all
 *
 * Instrumented tests used to build their own copies of these and were silently getting a second
 * [DanaPump] - a screen watching an object the pump never writes to. That cost CI shards A and C
 * ("Pump never reported initialized", "Command queue never went idle") while a shard driving the same
 * transport with no UI stayed green. Reading them from the graph makes a second instance impossible.
 *
 * ## Why it is declared here and not in `:app`
 *
 * It used to be one `PumpAccessors` interface in `app/src/withPumps`, naming Dana and Equil types.
 * `src/withPumps` is part of the `full` and `pumpcontrol` MAIN source sets, so that made those pump
 * modules a compile-time dependency of the app itself: removing `:pump:equil` from `settings.gradle`
 * failed `:app:compileFullDebugKotlin`, and no APK was built at all.
 *
 * Declared per module and contributed, the accessors are simply absent when the module is, and nothing
 * in `:app` names a pump.
 *
 * ## The cost, which is not free
 *
 * A contributed interface reaches the *generated* graph, so a test resolves it with a cast rather than
 * at compile time - see `MetroGraphs.rootGraph`. Worse, its accessors become **roots**: every
 * `@DependencyGraph(AppScope::class)` on the same classpath must then satisfy them. That broke
 * `MetroScopingTest`, which declared its own `AppScope` graph and suddenly had to provide
 * `@ApplicationScope CoroutineScope`. That test now uses a scope of its own, which it should have done
 * anyway. Anything else declaring `AppScope` next to the pump modules will hit the same wall.
 */
@ContributesTo(AppScope::class)
interface DanaAccessors {

    val danaPump: DanaPump
    val danaHistoryRecordDao: DanaHistoryRecordDao
}
