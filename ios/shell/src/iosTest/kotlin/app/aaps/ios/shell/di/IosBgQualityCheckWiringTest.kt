package app.aaps.ios.shell.di

import app.aaps.ios.shell.prefs.TestStorage
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.sync.nsclientV3.ws.NsSocket
import app.aaps.plugins.sync.nsclientV3.ws.NsSocketFactory
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * That iOS judges glucose quality with the real plugin, as an Android follower does.
 *
 * It used to get `IosBgQualityCheck`, which always answered UNKNOWN. The plugin now registers itself from shared
 * code, so a full iOS app gets it too: on doubled readings the loop falls back to LGS.
 */
class IosBgQualityCheckWiringTest {

    /** Nothing here connects to anything, so a factory that makes no sockets is the honest stand-in. */
    private object NoSockets : NsSocketFactory {

        override fun create(url: String): NsSocket? = null
    }

    init {
        TestStorage.install()
    }

    private val graph = createGraphFactory<IosAppGraph.Factory>().create(NoSockets, NoCrashReporter)

    @Test
    fun `the BgQualityCheck binding is the plugin in the list`() {
        val plugin = graph.contributedPlugins[860]
        assertIs<BgQualityCheckPlugin>(plugin)
        assertSame<Any>(plugin, graph.bgQualityCheck)
    }
}
