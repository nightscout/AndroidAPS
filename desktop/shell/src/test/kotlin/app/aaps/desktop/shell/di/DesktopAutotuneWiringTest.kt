package app.aaps.desktop.shell.di

import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.shared.clientbindings.ClientGraphBindings
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * That the desktop gets the real Autotune.
 *
 * It used to get `DesktopAutotune`, which refused every run. Now the plugin registers itself from
 * common code, and the only desktop part is where its files go. A shell that still bound a placeholder
 * would fail to compile on the duplicate binding, but one that lost the plugin from the map would not,
 * so the map is what is checked.
 *
 * The graph is the real one. The build points `user.home` at a folder under `build`, so nothing it
 * opens is the developer's own.
 */
class DesktopAutotuneWiringTest {

    private val graph = createGraphFactory<DesktopAppGraph.Factory>().create(CoreObjectsGraph, ClientGraphBindings)

    @Test
    fun `the plugin list holds the real autotune`() {
        assertIs<AutotunePlugin>(graph.contributedPlugins[240])
    }
}
