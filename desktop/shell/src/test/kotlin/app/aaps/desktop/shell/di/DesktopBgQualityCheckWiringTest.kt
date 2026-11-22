package app.aaps.desktop.shell.di

import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * That the desktop judges glucose quality with the real plugin, as an Android follower does.
 *
 * It used to get `DesktopBgQualityCheck`, which always answered UNKNOWN. The plugin now registers itself from shared
 * code, as a plugin in the list and as `BgQualityCheck`, and both have to be the same object: the one in the list is
 * the one that is started and sees the data.
 *
 * The graph is the real one. The build points `user.home` at a folder under `build`, so nothing it opens is the
 * developer's own.
 */
class DesktopBgQualityCheckWiringTest {

    private val graph = createGraph<DesktopAppGraph>()

    @Test
    fun `the BgQualityCheck binding is the plugin in the list`() {
        val plugin = graph.contributedPlugins[860]
        assertIs<BgQualityCheckPlugin>(plugin)
        assertSame<Any>(plugin, graph.bgQualityCheck)
    }
}
