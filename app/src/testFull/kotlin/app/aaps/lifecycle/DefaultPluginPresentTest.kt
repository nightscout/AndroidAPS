package app.aaps.lifecycle

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.di.metro.AppRootGraph
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every category `PluginStore.verifySelectionInCategories` falls back on has a default plugin.
 *
 * That method elects one plugin per category, and when nothing is enabled it reaches for the category's
 * default:
 *
 *     activePumpStore = getTheOneEnabledInArray(...) as Pump?
 *     if (activePumpStore == null) { activePumpStore = getDefaultPlugin(PluginType.PUMP) as Pump ... }
 *
 * and `getDefaultPlugin` does not return null - it throws `IllegalStateException("Default plugin not
 * found")`. So a category with no default is not a degraded app, it is a **failed start**: the throw
 * comes out of `ConfigBuilderImpl.initialize()`. During a settings import it surfaces instead as
 * `ImportStep.ApplyFailed`, with the settings already written to disk.
 *
 * Nothing else checks this. A default is declared by one plugin, in one module, and the categories are
 * spread over six of them - so a module dropped from a flavour, or a `setDefault()` removed during a
 * refactor, takes the guarantee with it silently. This is the check.
 *
 * **BGSOURCE is the one to watch.** Its default is split across two plugins and two mechanisms:
 * `NSClientSourcePlugin` declares `.setDefault(config.AAPSCLIENT)` in its description, and
 * `DexcomPlugin` does `init { if (!config.AAPSCLIENT) pluginDescription.setDefault() }`. Exactly one of
 * them is the default in any given flavour, and neither reads as "the BGSOURCE default" at a glance.
 *
 * Scanned from source rather than from instances on purpose: building the real graph here would need
 * Android, and the regression this guards against is a declaration disappearing, which source sees
 * exactly. The limitation is the other side of that - it cannot evaluate `config.AAPSCLIENT`, so it
 * checks that a category HAS a declared default, not which flavour gets which one.
 */
class DefaultPluginPresentTest {

    /**
     * The categories `verifySelectionInCategories` defaults, in its own order.
     *
     * Not every [PluginType]: it defaults these six and leaves the rest alone, so requiring a default
     * for GENERAL or CONSTRAINTS would fail on a rule that does not exist.
     */
    private val defaultedCategories = listOf(
        PluginType.APS,
        PluginType.SENSITIVITY,
        PluginType.SMOOTHING,
        PluginType.CALIBRATION,
        PluginType.BGSOURCE,
        PluginType.PUMP
    )

    /** `.setDefault()`, `.setDefault(true)`, `.setDefault(config.AAPSCLIENT)`, `pluginDescription.setDefault()`. */
    private val declaresDefault = Regex("""\bsetDefault\s*\(""")

    /** `.mainType(PluginType.PUMP)` - the category the plugin belongs to. */
    private val mainType = Regex("""\bmainType\s*\(\s*PluginType\.(\w+)\s*\)""")

    @Test
    fun `every category the election falls back on has at least one default plugin`() {
        val byCategory = defaultsByCategory()

        val without = defaultedCategories.filter { byCategory[it].isNullOrEmpty() }

        assertThat(without).isEmpty()
    }

    /**
     * The same facts, written out, so a failure above says which plugin was supposed to carry it.
     *
     * Deliberately not an exact-match assertion on plugin names: a new default is a legitimate change,
     * and a test that has to be edited for every legitimate change stops being read.
     */
    @Test
    fun `the declared defaults are the expected ones`() {
        val byCategory = defaultsByCategory()

        assertThat(byCategory[PluginType.APS]).contains("OpenAPSSMBPlugin")
        assertThat(byCategory[PluginType.SENSITIVITY]).contains("SensitivityOref1Plugin")
        assertThat(byCategory[PluginType.SMOOTHING]).contains("NoSmoothingPlugin")
        assertThat(byCategory[PluginType.CALIBRATION]).contains("NoCalibrationPlugin")
        assertThat(byCategory[PluginType.PUMP]).contains("VirtualPumpPlugin")
        // Both halves of the flavour split - losing either one breaks a flavour, not the build.
        assertThat(byCategory[PluginType.BGSOURCE]).containsAtLeast("NSClientSourcePlugin", "DexcomPlugin")
    }

    /** Plugin simple names that declare a default, keyed by the category they declare a main type for. */
    private fun defaultsByCategory(): Map<PluginType, List<String>> {
        val sources = sourceIndex(repoRoot())
        val plugins = aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { PluginBase::class.java.isAssignableFrom(it) && it != PluginBase::class.java }
        check(plugins.isNotEmpty()) { "Found no PluginBase subclasses on the classpath - the scan broke" }

        val found = mutableMapOf<PluginType, MutableList<String>>()
        for (cls in plugins) {
            val simpleName = cls.name.substringAfterLast('.').substringAfterLast('$')
            val path = cls.name.substringBeforeLast('.').replace('.', '/') + "/$simpleName.kt"
            val file = sources[path] ?: continue
            val text = file.readText()
            if (!declaresDefault.containsMatchIn(text)) continue
            val type = mainType.find(text)?.groupValues?.get(1) ?: continue
            val category = PluginType.entries.firstOrNull { it.name == type } ?: continue
            found.getOrPut(category) { mutableListOf() }.add(simpleName)
        }
        return found
    }

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
            .mapNotNull { (file, path) ->
                val sourceRoot = listOf("/kotlin/", "/java/").firstOrNull { path.contains(it) } ?: return@mapNotNull null
                path.substring(path.lastIndexOf(sourceRoot) + sourceRoot.length) to file
            }
            .toMap()
    }
}
