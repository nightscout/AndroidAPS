package app.aaps.plugins.constraints.di

import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import app.aaps.plugins.constraints.dstHelper.DstHelperPlugin
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.constraints.storage.StorageConstraintPlugin
import app.aaps.plugins.constraints.versionChecker.VersionCheckerPlugin
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.createGraphFactory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Each plugin must land in exactly one build bucket, and in the right one.
 *
 * This is not a formality. Metro ignores javax qualifiers unless Dagger interop is switched on for the
 * module, and every binding here has the same type - so with interop off they would all collapse into
 * one map and the build would still pass. The result would be Objectives and the signature verifier
 * appearing in follower builds, and no error anywhere.
 *
 * A wrong bucket is silent at runtime too, because a plugin list is just a list.
 */
class ConstraintsBucketsTest {

    private fun <T : Any> unused(): DeferredRef<T> = DeferredRef { error("must not be resolved") }

    private fun graph(): ConstraintsMetroGraph =
        createGraphFactory<ConstraintsMetroGraph.Factory>().create(
            DeferredRef { mock<SafetyPlugin>() },
            DeferredRef { mock<VersionCheckerPlugin>() },
            DeferredRef { mock<StorageConstraintPlugin>() },
            DeferredRef { mock<SignatureVerifierPlugin>() },
            DeferredRef { mock<ObjectivesPlugin>() },
            DeferredRef { mock<DstHelperPlugin>() },
            DeferredRef { mock<BgQualityCheckPlugin>() },
            // The view model's dependencies. Reading bucket keys never builds a view model, so these
            // are handles that would throw if anything tried.
            unused(), unused(), unused(), unused(), unused(), unused(), unused(), unused()
        )

    @Test
    fun `every build gets safety, DST helper and BG quality check`() {
        assertThat(graph().allConfigsPlugins.keys).containsExactly(800, 850, 860)
    }

    @Test
    fun `only a looping build gets storage, signature verifier and objectives`() {
        assertThat(graph().apsPlugins.keys).containsExactly(820, 830, 840)
    }

    @Test
    fun `only a non-follower build gets the version checker`() {
        assertThat(graph().notNsClientPlugins.keys).containsExactly(810)
    }

    @Test
    fun `the buckets do not overlap and cover all seven plugins`() {
        val g = graph()
        val all = g.allConfigsPlugins.keys + g.apsPlugins.keys + g.notNsClientPlugins.keys
        assertThat(all).hasSize(7)
        // Summing the sizes and comparing to the union size is what catches a plugin bound twice.
        val total = g.allConfigsPlugins.size + g.apsPlugins.size + g.notNsClientPlugins.size
        assertThat(total).isEqualTo(all.size)
    }
}
