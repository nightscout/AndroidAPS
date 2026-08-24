package app.aaps.plugins.source.di

import app.aaps.core.interfaces.di.DeferredRef
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
import dev.zacsweers.metro.createGraphFactory
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
 * workers, so the dependencies can be handles that would throw. That keeps the test about the wiring
 * and means it does not need a mock for every plugin.
 */
class SourceMetroGraphTest {

    private fun <T : Any> unused(): DeferredRef<T> = DeferredRef { error("must not be resolved") }

    private fun graph(): SourceMetroGraph =
        createGraphFactory<SourceMetroGraph.Factory>().create(
            unused(), unused(), unused(), unused(), unused(), unused(), unused(), unused(),
            unused(), unused(), unused(), unused(), unused(), unused(), unused(), unused(),
            unused(), unused(), unused(), unused(), unused(), unused(), unused(), unused()
        )

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
