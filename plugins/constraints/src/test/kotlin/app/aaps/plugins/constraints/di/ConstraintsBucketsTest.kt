package app.aaps.plugins.constraints.di

import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
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

    /** A mocked leaf. Five of the plugins are really constructed now, so these must be usable. */
    private inline fun <reified T : Any> leaf(): DeferredRef<T> = DeferredRef { mock<T>() }

    private fun graph(): ConstraintsMetroGraph =
        createGraphFactory<ConstraintsMetroGraph.Factory>().create(
            DeferredRef { mock<SignatureVerifierPlugin>() },
            leaf(), leaf(), leaf(), leaf(), leaf(), leaf(), leaf(), leaf(),
            leaf(), leaf(), leaf(), leaf(), leaf(), leaf(), leaf(), leaf(),
            leaf(), leaf(), leaf(), leaf(), leaf(), leaf()
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
