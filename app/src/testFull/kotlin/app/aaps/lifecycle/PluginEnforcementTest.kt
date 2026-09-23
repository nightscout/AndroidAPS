package app.aaps.lifecycle

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.di.metro.AppRootGraph
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the enforcement declarations that ship.
 *
 * An enforcement overrides what the user stored, so a wrong one is not cosmetic: it either locks a plugin
 * off that someone needs, or locks one on that they deliberately switched off. `PluginDescriptionTest`
 * covers the builder in isolation; this covers what the plugins actually declare.
 *
 * ## Why a contradiction has to fail the build
 *
 * `PluginBase.enforcedState` resolves a disagreement by letting Disabled win. Failing closed is the right
 * default, but it is also silent: a plugin declaring both would just be mysteriously off, and the person
 * who found out would be a user, not this file.
 *
 * Conditions cannot be evaluated here - a plugin cannot be constructed in a unit test - so the check is
 * textual, the same technique and the same limits as [PluginLifetimeWorkScanTest]. It flags a file that
 * declares both directions by hand and asks for a justification in [reviewedSafe]. `enforceEnabledOnlyWhen`
 * is the sanctioned way to say "both directions": its conditions are `c` and `!c`, so they can never both
 * hold, and it reads as one decision rather than two that happen to sit together.
 */
class PluginEnforcementTest {

    /**
     * Files that declare both directions by hand, with the reason it is safe. Empty is the goal: prefer
     * `enforceEnabledOnlyWhen`, which cannot contradict itself.
     */
    private val reviewedSafe: Map<String, String> = emptyMap()

    private val enabledPattern = Regex("""\.enforce\(\s*EnforcedState\.Enabled""")
    private val disabledPattern = Regex("""\.enforce\(\s*EnforcedState\.Disabled""")

    @Test
    fun `no plugin declares both directions by hand`() {
        val files = pluginSources()
        check(files.isNotEmpty()) { "Found no plugin sources - the scan broke" }

        val bothDirections = files.filter { (_, file) ->
            val text = file.readText()
            enabledPattern.containsMatchIn(text) && disabledPattern.containsMatchIn(text)
        }.keys

        assertThat(bothDirections.filterNot { it in reviewedSafe }).isEmpty()
    }

    /** A waiver that matches nothing is stale. */
    @Test
    fun `every waiver still matches a file`() {
        val files = pluginSources()
        assertThat(reviewedSafe.keys.filterNot { it in files.keys }).isEmpty()
    }

    /**
     * The scan can see enforcements at all, so a refactor that renames `enforce` cannot make this file pass
     * by finding nothing. Deliberately loose: that some exist, not how many.
     */
    @Test
    fun `the scan can see enforcement declarations`() {
        val declaring = pluginSources().filter { (_, file) ->
            val text = file.readText()
            enabledPattern.containsMatchIn(text) || disabledPattern.containsMatchIn(text) ||
                text.contains(".enforceEnabledOnlyWhen")
        }
        assertThat(declaring).isNotEmpty()
    }

    /** Plugin class simple name -> its production source file. */
    private fun pluginSources(): Map<String, File> {
        val sources = sourceIndex(repoRoot())
        return aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { PluginBase::class.java.isAssignableFrom(it) && it != PluginBase::class.java }
            .filterNot { it.name.contains('$') }
            .mapNotNull { cls ->
                val simpleName = cls.name.substringAfterLast('.')
                val path = cls.name.substringBeforeLast('.').replace('.', '/') + "/$simpleName.kt"
                sources[path]?.let { simpleName to it }
            }
            .toMap()
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").exists() && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "Could not find the repository root above ${System.getProperty("user.dir")}" }
    }

    /** Production Kotlin sources, keyed by `<package path>/<SimpleName>.kt`. */
    private fun sourceIndex(root: File): Map<String, File> {
        val skip = listOf("/build/", "/src/test", "/src/androidTest", "/src/androidHostTest", "/src/commonTest", "/src/iosTest")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to it.invariantSeparatorsPath }
            .filterNot { (_, path) -> skip.any { path.contains(it) } }
            .mapNotNull { (file, path) ->
                val sourceRoot = listOf("/kotlin/", "/java/").firstOrNull { path.contains(it) } ?: return@mapNotNull null
                path.substring(path.lastIndexOf(sourceRoot) + sourceRoot.length) to file
            }
            .toMap()
    }
}
