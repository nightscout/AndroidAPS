package app.aaps.di.metro

import com.google.common.truth.Truth.assertThat
import info.nightscout.pump.combov2.ComboV2Plugin
import org.junit.jupiter.api.Test

/**
 * Pump drivers reach the `@PumpDriver` bucket, and only that bucket.
 *
 * The bucket is new: before this there was no Metro `@PumpDriver` map at all, so a pump plugin
 * contributed under that qualifier went nowhere - `AppModule` never read it, and the plugin simply did
 * not appear in the list. That is the same silent failure the virtual pump hit with `@AllConfigs`, and
 * it cannot be seen from the annotation on the plugin.
 *
 * `:app` merges this bucket only when `config.PUMPDRIVERS`, so a driver landing in the unqualified map
 * instead would show up in a follower build that has no pump at all.
 */
class PumpDriverBucketTest {

    @Test
    fun `combov2 is registered as a pump driver`() {
        val root = testRoot()

        assertThat(root.contributedPumpDriverPlugins.keys).contains(1060)
        assertThat(root.contributedPumpDriverPlugins[1060]).isInstanceOf(ComboV2Plugin::class.java)
    }

    @Test
    fun `a pump driver is not in the every-build bucket`() {
        val root = testRoot()

        assertThat(root.contributedPlugins.keys).doesNotContain(1060)
    }
}
