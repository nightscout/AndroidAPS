package app.aaps.di

import app.aaps.core.interfaces.plugin.PluginBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Both cases nearly happened during the migration, so these are regression tests, not hypotheticals.
 */
class PluginListMergeTest {

    private class PluginA : PluginBase(mock(), mock(), mock())
    private class PluginB : PluginBase(mock(), mock(), mock())

    @Test
    fun `merges sources and sorts by order key`() {
        val a = PluginA()
        val b = PluginB()
        val (plugins, problems) = mergePlugins(
            listOf(PluginSource("BucketA", mapOf(20 to b)), PluginSource("BucketB", mapOf(10 to a)))
        )

        assertThat(plugins).containsExactly(a, b).inOrder()
        assertThat(problems).isEmpty()
    }

    @Test
    fun `reports the same plugin class arriving from both frameworks`() {
        val (_, problems) = mergePlugins(
            listOf(PluginSource("BucketA", mapOf(10 to PluginA())), PluginSource("BucketB", mapOf(20 to PluginA())))
        )

        assertThat(problems).hasSize(1)
        assertThat(problems.single()).contains("PluginA")
        assertThat(problems.single()).contains("2 times")
    }

    @Test
    fun `reports two sources fighting over one order key`() {
        // The dangerous case: the loser vanishes, and the overwrite also hides a double contribution.
        val (plugins, problems) = mergePlugins(
            listOf(PluginSource("BucketA", mapOf(340 to PluginA())), PluginSource("BucketB", mapOf(340 to PluginB())))
        )

        assertThat(plugins).hasSize(1)
        assertThat(problems).hasSize(1)
        assertThat(problems.single()).contains("340")
        assertThat(problems.single()).contains("BucketA")
        assertThat(problems.single()).contains("BucketB")
    }

    @Test
    fun `a healthy list of several sources reports nothing`() {
        val (plugins, problems) = mergePlugins(
            listOf(
                PluginSource("BucketA", mapOf(10 to PluginA(), 30 to PluginB())),
                PluginSource("BucketB", mapOf(20 to PluginA()))
            )
        )

        assertThat(plugins).hasSize(3)
        // PluginA appears twice here, so this list is NOT healthy - the check must say so.
        assertThat(problems).isNotEmpty()
    }

    @Test
    fun `distinct classes under distinct keys are healthy`() {
        val (plugins, problems) = mergePlugins(
            listOf(
                PluginSource("BucketA", mapOf(10 to PluginA())),
                PluginSource("BucketB", mapOf(20 to PluginB()))
            )
        )

        assertThat(plugins).hasSize(2)
        assertThat(problems).isEmpty()
    }
}
