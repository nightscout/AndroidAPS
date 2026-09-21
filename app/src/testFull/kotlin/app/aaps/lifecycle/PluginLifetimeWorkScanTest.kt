package app.aaps.lifecycle

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.di.metro.AppRootGraph
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Work a plugin schedules that can outlive its own stop.
 *
 * Stopping a plugin runs `onStop`, and nothing else. Anything the plugin handed to the application
 * scope, to a `Handler`, or to a raw `Thread` keeps running unless `onStop` cancels it by hand. That
 * matters for the settings import, which stops the plugins, writes the store and starts them again: a
 * job launched before the stop lands in the middle of that window and can queue a pump command against
 * a driver that is being torn down.
 *
 * The scan reads the plugin's whole source file, not only `onStart`. The work that survives is usually
 * launched from an ordinary method - `LoopPlugin.invoke` ends its SMB branch with
 * `appScope.launch { delay(1000); invoke(...) }`, which is exactly the shape this looks for and is
 * nowhere near `onStart`.
 *
 * ## It is a worklist, not a gate
 *
 * Every hit must be named in [reviewedSafe] or [survivesStop], with the reason in plain English.
 * [reviewedSafe] means `onStop` really does undo it. [survivesStop] means it does not, and that entry
 * is a thing to fix - the map is the todo list for the rest of this work, and it should only ever
 * shrink. Adding scheduled work to a plugin fails this test until someone writes down which it is.
 *
 * ## What it cannot see
 *
 * Source text only. It cannot see work scheduled by a helper class the plugin calls, and it cannot see
 * `WorkManager` or `AlarmManager`, which outlive the process entirely and so are a different problem.
 * `pluginScope` is deliberately not a pattern: it is the answer, not the offence.
 */
class PluginLifetimeWorkScanTest {

    /**
     * `onStop` undoes these. Key is `SimpleClassName#member`, value is why it is safe.
     * Each one was read before it was written down, not assumed from the pattern.
     */
    private val reviewedSafe: Map<String, String> = mapOf(
        "GlunovoPlugin#contentUri" to "the refreshLoop it posts is removed in onStop (removeCallbacksAndMessages, looper quit, handler nulled)",
        "GlunovoPlugin#onStart" to "same handler, torn down in onStop",
        "IntelligoPlugin#contentUri" to "the refreshLoop it posts is removed in onStop",
        "IntelligoPlugin#onStart" to "same handler, torn down in onStop",
        "RandomBgPlugin#interval" to "the refreshLoop it posts is removed in onStop",
        "RandomBgPlugin#onStart" to "same handler, torn down in onStop",
        "SignatureVerifierPlugin#onStart" to "onStop removes the callbacks, quits the looper and nulls the handler",
        "OmnipodDashPumpPlugin#scope" to "onStop cancels the scope and tears the handler down",
        "OmnipodDashPumpPlugin#onStart" to "onStop cancels the scope and tears the handler down",
        "LoopPlugin#scheduleBuildAndStoreDeviceStatus" to "the job is held in deviceStatusJob and onStop cancels it",
        "XdripPlugin#onStart" to "onStop removes the callbacks first, then quits the looper",
        "OmnipodErosPumpPlugin#loopHandler" to "onStop removes the callbacks; the looper stays alive on purpose, loopHandler is created once",
        "OmnipodErosPumpPlugin#pumpDescription" to "the statusChecker chain it re-posts is removed in onStop",
        "OmnipodErosPumpPlugin#onStart" to "the statusChecker it posts is removed in onStop",
        "LoopPlugin#onStart" to "the two appScope collectors are kept in `collectors` and cancelled in onStop",
    )

    /**
     * Work that OUTLIVES `onStop` - whether or not that is a problem on its own. The reason says which.
     * This is the worklist for the `onStop` parity step of `_docs/PREFERENCE_MIGRATIONS_PLAN.md`, and it
     * should only ever get shorter. Nothing may be added here without a decision recorded next to it.
     */
    private val survivesStop: Map<String, String> = mapOf(
        "LoopPlugin#invoke" to
            "appScope.launch { delay(1000); invoke(...) } reschedules the loop a second later, so it lands inside or just " +
                "after a restart window and can queue pump commands against a driver being torn down. The worst one here.",
        "VirtualPumpPlugin#deliverTreatment" to
            "one-shot appScope.launch that persists a bolus; it reschedules nothing, but it can still write during a restart window",
        "InsightPlugin#bolusProgressData" to
            "hands the application scope to a collaborator, so whatever that launches is not tied to this plugin's life",
        "SmsCommunicatorPlugin#processTARGET" to
            "hands the application scope to a collaborator, same as Insight",
    )

    private data class Hit(val key: String, val matched: String, val where: String)

    /**
     * Work handed to something that outlives the plugin.
     *
     * `CoroutineScope(` is deliberately NOT here. Creating a scope in `onStart` and cancelling it in
     * `onStop` is the correct idiom and 25 plugins use it, so flagging it would bury the real hits in
     * noise. The risk with a scope is failing to cancel it, which is a different question and wants a
     * different check. `pluginScope` is not here either: it is the answer, not the offence.
     */
    private val patterns = listOf(
        Regex("""\bappScope\b"""),
        Regex("""\bGlobalScope\b"""),
        Regex("""\bpostDelayed\b"""),
        Regex("""\b(?:HandlerThread|Handler|Thread)\s*\(""")
    )

    /** Its own declaration is not a use. */
    private val declaration = Regex("""\b(?:appScope|pluginScope)\s*:\s*CoroutineScope""")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").exists() && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "Could not find the repository root above ${System.getProperty("user.dir")}" }
    }

    /** Production Kotlin sources, keyed by `<package path>/<SimpleName>.kt`, so a class maps to its file. */
    private fun sourceIndex(root: File): Map<String, File> {
        val skip = listOf("/build/", "/src/test", "/src/androidTest", "/src/androidHostTest", "/src/commonTest", "/src/iosTest")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to it.invariantSeparatorsPath }
            .filterNot { (_, path) -> skip.any { path.contains(it) } }
            // Both roots: several older modules keep Kotlin under src/main/java (omnipod eros, for one).
            .mapNotNull { (file, path) ->
                val root = listOf("/kotlin/", "/java/").firstOrNull { path.contains(it) } ?: return@mapNotNull null
                path.substring(path.lastIndexOf(root) + root.length) to file
            }
            .toMap()
    }

    /** Comments are not code: a pattern named in a comment is not scheduled work. */
    private fun stripComments(text: String): String {
        val out = StringBuilder(text.length)
        var inBlock = false
        for (line in text.lineSequence()) {
            var i = 0
            val sb = StringBuilder()
            while (i < line.length) {
                if (inBlock) {
                    val end = line.indexOf("*/", i)
                    if (end < 0) { i = line.length } else { inBlock = false; i = end + 2 }
                } else {
                    val block = line.indexOf("/*", i)
                    val lineComment = line.indexOf("//", i)
                    when {
                        lineComment >= 0 && (block < 0 || lineComment < block) -> { sb.append(line, i, lineComment); i = line.length }
                        block >= 0                                             -> { sb.append(line, i, block); inBlock = true; i = block + 2 }
                        else                                                   -> { sb.append(line, i, line.length); i = line.length }
                    }
                }
            }
            out.append(sb).append('\n')
        }
        return out.toString()
    }

    /**
     * A member of the class, not a local. Anchored to at most four spaces of indentation, which is this
     * codebase's class-member level - a `val` inside a function is indented further and would otherwise
     * steal the attribution from the function it sits in.
     */
    private val memberStart = Regex("""^ {0,4}(?:(?:private|internal|public|protected|open|override|suspend|inline)\s+)*(?:fun|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")

    private fun scan(simpleName: String, file: File): List<Hit> {
        val hits = mutableListOf<Hit>()
        var member = "<class body>"
        stripComments(file.readText()).lineSequence().forEachIndexed { index, line ->
            memberStart.find(line)?.let { member = it.groupValues[1] }
            if (declaration.containsMatchIn(line)) return@forEachIndexed
            patterns.firstNotNullOfOrNull { it.find(line) }?.let { match ->
                hits += Hit("$simpleName#$member", match.value.trim(), "${file.name}:${index + 1}")
            }
        }
        return hits
    }

    private fun allHits(): List<Hit> {
        val root = repoRoot()
        val sources = sourceIndex(root)
        val plugins = aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { PluginBase::class.java.isAssignableFrom(it) && it != PluginBase::class.java }
        check(plugins.isNotEmpty()) { "Found no PluginBase subclasses on the classpath - the scan broke" }

        val missing = mutableListOf<String>()
        val hits = plugins.flatMap { cls ->
            val simpleName = cls.name.substringAfterLast('.').substringAfterLast('$')
            val path = cls.name.substringBeforeLast('.').replace('.', '/') + "/$simpleName.kt"
            val file = sources[path]
            // A nested or generated class has no file of its own; a top-level one that is missing means
            // the index is wrong, and silently skipping it would make a clean sheet meaningless.
            if (file == null) {
                if (!cls.name.contains('$')) missing += cls.name
                emptyList()
            } else scan(simpleName, file)
        }
        assertThat(missing).isEmpty()
        return hits
    }

    @Test
    fun `every plugin that schedules work outside its own scope is accounted for`() {
        val hits = allHits()
        check(hits.isNotEmpty()) { "The scan found nothing at all, which cannot be right - it is broken" }

        val unaccounted = hits.filterNot { it.key in reviewedSafe || it.key in survivesStop }
            .map { "${it.key} -> ${it.matched} (${it.where})" }
            .distinct()

        assertThat(unaccounted).isEmpty()
    }

    /** A waiver that matches nothing is stale: a rename or a fix should shorten the list, not rot it. */
    @Test
    fun `no waiver is stale`() {
        val keys = allHits().map { it.key }.toSet()
        val stale = (reviewedSafe.keys + survivesStop.keys).filterNot { it in keys }

        assertThat(stale).isEmpty()
    }
}
