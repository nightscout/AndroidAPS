package app.aaps.di.metro

import com.google.common.truth.Truth.assertThat
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.medtrum.MedtrumPlugin
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
    fun `every converted pump driver is in the pump bucket, under its own key`() {
        val drivers = testRoot().contributedPumpDriverPlugins

        assertThat(drivers[1040]).isInstanceOf(DanaRSPlugin::class.java)
        assertThat(drivers[1060]).isInstanceOf(ComboV2Plugin::class.java)
        assertThat(drivers[1120]).isInstanceOf(MedtrumPlugin::class.java)
        assertThat(drivers[1130]).isInstanceOf(EquilPumpPlugin::class.java)
    }

    @Test
    fun `no pump driver leaks into the every-build bucket`() {
        // Where a wrong qualifier would put them. `:app` merges the every-build bucket unconditionally,
        // so a driver landing there would appear in a follower that has no pump at all - and nothing
        // would report it.
        val everyBuild = testRoot().contributedPlugins.keys

        assertThat(everyBuild).containsNoneOf(1040, 1060, 1120, 1130)
    }
}
