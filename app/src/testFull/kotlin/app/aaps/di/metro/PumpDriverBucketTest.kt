package app.aaps.di.metro

import com.google.common.truth.Truth.assertThat
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.diaconn.DiaconnG8Plugin
import app.aaps.pump.eopatch.EopatchPumpPlugin
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.pump.combov2.ComboV2Plugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails

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

    /**
     * Moved here from `ContributedPluginsTest`, which lives in `src/test` and so is compiled for every
     * flavour - including the followers, which have no pump module on the classpath and an empty bucket
     * by design. It could only ever fail there, and did: `:app:testAapsclientDebugUnitTest` was red on
     * its own, unnoticed because CI runs the `full` variant.
     *
     * `containsExactly`, not a per-key check: it is the only assertion that catches a driver quietly
     * *disappearing* from the bucket.
     */
    @Test
    fun `the pump bucket holds exactly the known drivers`() {
        assertThat(testRoot().contributedPumpDriverPlugins.keys)
            .containsExactly(1010, 1020, 1030, 1040, 1050, 1060, 1070, 1080, 1090, 1100, 1110, 1120, 1130)
    }

    @Test
    fun `every converted pump driver is in the pump bucket, under its own key`() {
        val drivers = testRoot().contributedPumpDriverPlugins

        assertThat(drivers[1010]).isInstanceOf(DanaRPlugin::class.java)
        assertThat(drivers[1020]).isInstanceOf(DanaRKoreanPlugin::class.java)
        assertThat(drivers[1030]).isInstanceOf(DanaRv2Plugin::class.java)
        assertThat(drivers[1040]).isInstanceOf(DanaRSPlugin::class.java)
        assertThat(drivers[1050]).isInstanceOf(InsightPlugin::class.java)
        assertThat(drivers[1060]).isInstanceOf(ComboV2Plugin::class.java)
        assertThat(drivers[1070]).isInstanceOf(OmnipodErosPumpPlugin::class.java)
        assertThat(drivers[1080]).isInstanceOf(OmnipodDashPumpPlugin::class.java)
        assertThat(drivers[1090]).isInstanceOf(MedtronicPumpPlugin::class.java)
        assertThat(drivers[1100]).isInstanceOf(DiaconnG8Plugin::class.java)
        assertThat(drivers[1110]).isInstanceOf(EopatchPumpPlugin::class.java)
        assertThat(drivers[1120]).isInstanceOf(MedtrumPlugin::class.java)
        assertThat(drivers[1130]).isInstanceOf(EquilPumpPlugin::class.java)
    }

    @Test
    fun `no pump driver in the bucket is built by Metro itself`() {
        // The rule PumpLeaves exists to enforce, applied to the plugins rather than to what a view model
        // injects: Dagger owns every pump driver, because the pump services are still DaggerServices and
        // write to Dagger's copy. If Metro builds its own for this map, the list holds an object the pump
        // never touches - "DanaR pump never reported initialized", which is exactly what this cost on CI.
        //
        // `testRoot()` mocks PumpLeaves with RETURNS_MOCKS, so anything handed over comes back a mock and
        // anything Metro constructed comes back real. That difference is the whole check.
        //
        // Getting there needs BOTH halves and neither fails to compile on its own: keep javax @Singleton
        // on the plugin (never @SingleIn - that tells Metro to own it) AND list it in PumpLeaves.
        val builtByMetro = testRoot().contributedPumpDriverPlugins
            .filterValues { !mockingDetails(it).isMock }
            .map { (key, plugin) -> "$key -> ${plugin.javaClass.simpleName}" }

        assertThat(builtByMetro).isEmpty()
    }

    @Test
    fun `no pump driver leaks into the every-build bucket`() {
        // Where a wrong qualifier would put them. `:app` merges the every-build bucket unconditionally,
        // so a driver landing there would appear in a follower that has no pump at all - and nothing
        // would report it.
        val everyBuild = testRoot().contributedPlugins.keys

        assertThat(everyBuild).containsNoneOf(1010, 1020, 1030, 1040, 1050, 1060, 1070, 1080, 1090, 1100, 1110, 1120, 1130)
    }
}
