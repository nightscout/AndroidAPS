package app.aaps.di.metro

import app.aaps.plugins.source.di.SourceMetroGraph
import app.aaps.plugins.source.AidexPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import app.aaps.plugins.source.instara.InstaraPlugin
import app.aaps.plugins.source.instara.InstaraStaleCheckWorker
import app.aaps.plugins.source.notificationreader.NotificationCollectorService
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every blood-glucose source worker must be registered, under its own class.
 *
 * These workers cannot be exercised on a test phone unless that phone actually runs the matching CGM
 * app, so this is where the wiring gets checked. A worker missing from the map is not a build error:
 * `MetroWorkerFactory` would simply hand it back to Hilt, and once Hilt is gone it would fail at the
 * moment a CGM delivers a reading - the worst possible time for a glucose source to stop working.
 *
 * Nothing is dereferenced here on purpose. Reading the map only builds the assisted factories, not the
 * workers, so no leaf has to be stubbed at all - see [testRoot].
 *
 * The graph is opened from the root rather than built directly, because it is a `@GraphExtension` and
 * that is the only way to reach one.
 */
class SourceGraphTest {

    private fun graph(): SourceMetroGraph = testRoot().sourceGraph

    @Test
    fun `all twelve source workers are registered`() {
        assertThat(graph().workerCreators.keys).containsExactly(
            AidexPlugin.AidexWorker::class,
            DexcomPlugin.DexcomWorker::class,
            GlimpPlugin.GlimpWorker::class,
            InstaraPlugin.InstaraWorker::class,
            InstaraStaleCheckWorker::class,
            MM640gPlugin.MM640gWorker::class,
            PatchedSiAppPlugin.PatchedSiAppWorker::class,
            PatchedSinoAppPlugin.PatchedSinoAppWorker::class,
            PoctechPlugin.PoctechWorker::class,
            SyaiPlugin.SyaiWorker::class,
            TomatoPlugin.TomatoWorker::class,
            XdripSourcePlugin.XdripSourceWorker::class
        )
    }

    @Test
    fun `the notification reader service is the only member injection here`() {
        assertThat(graph().memberInjectors.keys).containsExactly(NotificationCollectorService::class)
    }
}
