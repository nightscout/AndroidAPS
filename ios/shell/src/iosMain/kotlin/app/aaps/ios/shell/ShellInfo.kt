package app.aaps.ios.shell

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.ios.shell.di.IosProbeGraph
import app.aaps.ios.shell.di.ProbeLogger
import app.aaps.ios.shell.prefs.IosSp
import dev.zacsweers.metro.createGraphFactory
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * A small entry point the iOS side can call to prove the framework loaded and runs.
 *
 * The link itself is what this module is for, so this stays deliberately small. It gives an Xcode
 * project something to call, so that "the framework builds", "an app can reach it" and "Kotlin code
 * actually runs" stay three separate questions with three separate answers.
 */
object ShellInfo {

    /** Name of the framework, so a caller can print something it did not hard code itself. */
    const val NAME: String = "AapsShared"

    /** How many migrated modules this framework links. Kept in step with `ios/shell/build.gradle.kts`. */
    const val LINKED_MODULES: Int = 14

    /**
     * The current local time, formatted by Kotlin.
     *
     * Swift could read a clock by itself, so the value is not the point. Running this proves the
     * Kotlin runtime started and that a multiplatform dependency, here kotlinx-datetime, works
     * inside a real app rather than only linking.
     */
    fun localTime(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

    /**
     * Builds a Metro graph and uses what comes out of it.
     *
     * This is the check that linking cannot make. A graph is generated code, and generated code
     * that links can still fail the first time it is asked for an object, so the only way to know
     * Metro works on Kotlin/Native is to make it hand something over and then use it.
     *
     * Four things are checked, in order of how much they would cost to get wrong:
     *
     * 1. the graph can be created at all,
     * 2. it constructs real AAPS plugins, injecting the leaves this module supplies,
     * 3. `@SingleIn(AppScope::class)` holds, so one scoped plugin is one object,
     * 4. a second graph does not share that object with the first.
     *
     * Point 4 is not pedantry. On the earlier Koin branch a module written as a top-level `val`
     * silently shared singletons between graphs, which is exactly the sort of thing that links,
     * runs, and is still wrong.
     *
     * @return one line per check, ready to print. Any failure is returned rather than thrown, so a
     *   caller sees which step broke instead of only a crash.
     */
    fun checkDi(): String = try {
        val graph = createGraphFactory<IosProbeGraph.Factory>().create()
        val other = createGraphFactory<IosProbeGraph.Factory>().create()

        val built = listOf(
            graph.noSmoothing, graph.avgSmoothing, graph.exponentialSmoothing, graph.noCalibration
        )
        // Real work, not just construction: a plugin that cannot run would still pass a null check.
        val sample = mutableListOf(
            InMemoryGlucoseValue(timestamp = 1_000L, value = 100.0),
            InMemoryGlucoseValue(timestamp = 2_000L, value = 105.0)
        )
        val smoothed = graph.avgSmoothing.smooth(sample)

        listOf(
            "graph built: yes",
            "plugins injected: ${built.size}",
            "names: " + built.joinToString { it.name },
            "singleton holds: " + (graph.noSmoothing === graph.noSmoothing),
            "graphs isolated: " + (graph.noSmoothing !== other.noSmoothing),
            "plugin ran: ${smoothed.size} values",
            "logger calls: ${ProbeLogger.calls}"
        ).joinToString("\n")
    } catch (e: Throwable) {
        "DI FAILED: ${e::class.simpleName}: ${e.message}"
    }

    /**
     * Exercises the NSUserDefaults backed store.
     *
     * The reason this is a runtime check and not a unit test yet is the failure it is looking for.
     * `NSUserDefaults` answers a missing key with a zero value rather than saying it is missing, so
     * a store written the obvious way returns `false` and `0.0` instead of the caller's defaults.
     * That version compiles, links, runs, and quietly replaces every AAPS default with zero. So the
     * first case below is the important one.
     *
     * @return one line per check, or the failure.
     */
    fun checkPrefs(): String = try {
        val sp = IosSp()
        val key = "aaps.ios.probe"
        sp.remove(key)

        // The trap: absent key must give the caller's default, not NSUserDefaults' zero.
        val absentBool = sp.getBoolean(key, defaultValue = true)
        val absentDouble = sp.getDouble(key, defaultValue = 5.5)
        val absentInt = sp.getInt(key, defaultValue = 42)

        sp.putDouble(key, 7.25)
        val roundTrip = sp.getDouble(key, defaultValue = 0.0)
        val present = sp.contains(key)

        sp.putInt("$key.count", 1)
        sp.incInt("$key.count")
        val counted = sp.getInt("$key.count", defaultValue = 0)

        sp.edit { putString("$key.text", "ok") }
        val edited = sp.getString("$key.text", defaultValue = "")

        sp.remove(key); sp.remove("$key.count"); sp.remove("$key.text")
        val cleaned = !sp.contains(key)

        listOf(
            "defaults kept: " + (absentBool && absentDouble == 5.5 && absentInt == 42),
            "round trip: " + (roundTrip == 7.25 && present),
            "increment: " + (counted == 2),
            "edit block: " + (edited == "ok"),
            "remove: $cleaned"
        ).joinToString("\n")
    } catch (e: Throwable) {
        "PREFS FAILED: ${e::class.simpleName}: ${e.message}"
    }
}
