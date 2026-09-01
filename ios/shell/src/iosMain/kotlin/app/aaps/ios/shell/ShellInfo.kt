package app.aaps.ios.shell

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.ios.shell.di.IosProbeGraph
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.keys.BooleanKey
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import app.aaps.implementation.logging.AAPSLoggerIos
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ios.shell.prefs.IosSp
import dev.zacsweers.metro.createGraphFactory
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import app.aaps.core.objects.di.CoreObjectsGraph

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
    const val LINKED_MODULES: Int = 26

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
        val graph = createGraphFactory<IosProbeGraph.Factory>().create(CoreObjectsGraph)
        val other = createGraphFactory<IosProbeGraph.Factory>().create(CoreObjectsGraph)

        val built = listOf(
            graph.noSmoothing, graph.avgSmoothing, graph.exponentialSmoothing, graph.noCalibration
        )
        // The five that needed the whole chain below Preferences. Built here rather than only named,
        // because the point of this probe is that linking and running are different questions.
        val unblocked = listOf(
            graph.sensitivityOref1, graph.sensitivityAAPS, graph.sensitivityWeightedAverage,
            graph.unscentedKalmanFilter, graph.linearCalibration
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
            "unblocked built: ${unblocked.size}",
            "unblocked: " + unblocked.joinToString { it.name },
            "preferences: simpleMode=" + graph.preferences.get(BooleanKey.GeneralSimpleMode),
            "notifications: " + graph.notificationManager.notifications.value.size + " live"
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

    /**
     * Calls into every `:core` module and reports what each one did.
     *
     * See [CoreProbe] for why `expect`/`actual` code is what this goes after.
     */
    fun checkCore(): String = CoreProbe.run().joinToString("\n")

    /**
     * Opens the AAPS database on iOS and writes a reading through it.
     *
     * See [DatabaseProbe] for why this writes rather than inspects.
     */
    fun checkDatabase(): String = DatabaseProbe.run()

    /**
     * Writes one line through the real logger and looks for the file afterwards.
     *
     * `NSLog` reaching the console proves nothing on its own - the file is the half that has to
     * survive the app closing, and it is the half that can silently fail on a sandboxed path.
     */
    fun checkLogging(): String = try {
        val logger = AAPSLoggerIos(fileName = "aaps-probe.log")
        logger.debug(LTag.CORE, "probe line")
        val url = logger.logFileUrl()
        val path = url?.path
        val exists = path?.let { NSFileManager.defaultManager.fileExistsAtPath(it) } == true
        listOf(
            "log file: " + (path?.substringAfterLast('/') ?: "no path"),
            "written: $exists"
        ).joinToString("\n")
    } catch (e: Throwable) {
        "LOGGING FAILED: ${e::class.simpleName}: ${e.message}"
    }

    /**
     * Looks for the alarm audio, and starts one briefly.
     *
     * Two things here can fail without any compiler noticing. The four MP3s are copied into the
     * bundle by a script in the Xcode project, so a rename on the Android side leaves them missing,
     * and `AVAudioPlayer` refuses to start for reasons - a bad file, a session it cannot activate -
     * that only show at run time. Both would end as an alarm that stays silent, which is the worst
     * way for this to break.
     */
    fun checkAlarm(): String = try {
        val found = listOf("alarm", "urgentalarm", "error", "boluserror")
            .count { NSBundle.mainBundle.URLForResource(it, withExtension = "mp3") != null }
        val graph = createGraphFactory<IosProbeGraph.Factory>().create(CoreObjectsGraph)
        val player = graph.alarmSoundPlayer
        // Started and stopped straight away: this is a check that it runs, not a demonstration.
        player.play(AlarmSound.ERROR, AlarmSoundPlayer.OWNER_INTERNAL)
        player.stop(AlarmSoundPlayer.OWNER_INTERNAL)
        // Not "the alarm works": play() is asynchronous, so this only says the call was accepted.
        // Whether AVAudioPlayer actually started is in the log, and on the simulator it does not.
        listOf(
            "alarm sounds bundled: $found of 4",
            "play requested: yes (see the log for whether audio started)"
        ).joinToString("\n")
    } catch (e: Throwable) {
        "ALARM FAILED: ${e::class.simpleName}: ${e.message}"
    }
}
