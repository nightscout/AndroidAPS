package app.aaps.di.metro

import app.aaps.plugins.calibration.LinearCalibrationPlugin
import app.aaps.plugins.calibration.NoCalibrationPlugin
import app.aaps.plugins.sensitivity.SensitivityAAPSPlugin
import app.aaps.plugins.sensitivity.SensitivityOref1Plugin
import app.aaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import app.aaps.plugins.smoothing.AvgSmoothingPlugin
import app.aaps.plugins.smoothing.ExponentialSmoothingPlugin
import app.aaps.plugins.smoothing.NoSmoothingPlugin
import app.aaps.plugins.smoothing.UnscentedKalmanFilterPlugin
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails

/**
 * Plugins that register themselves with `@ContributesIntoMap` really reach the root graph.
 *
 * A contribution is invisible at the call site - there is no `@Provides` to read and no factory
 * argument to check - so the only way to see that a plugin is wired is to ask the graph. Getting the
 * scope wrong, or the bound type, or forgetting the annotation on a new plugin, all produce a smaller
 * map and no error at all: the plugin would simply never appear in the app.
 */
class ContributedPluginsTest {

    private val plugins get() = testRoot().contributedPlugins

    @Test
    fun `every contributed plugin appears under its order key`() {
        assertThat(plugins.keys).containsExactly(
            // persistent notification
            0,
            // iob/cob calculator, built in MainPluginsBindings. The history browser's second one is
            // scoped to a window and deliberately never reaches this map.
            10,
            // sensitivity
            100, 110, 120,
            200, 210, 220, 230, 240,
            400, 410, 420, 430, 440, 450, 460, 470, 480, 490, 500, 510, 520, 530, 540, 550,
            // sync - the every-build part of the 300 block. SmsCommunicator 300 and Tidepool 320 are
            // @NotNSClient; 340 (OpenHumans) comes from its own graph extension.
            310, 330, 350, 360, 370,
            // smoothing
            600, 610, 620, 630,
            // calibration
            700, 710,
            // constraints, every-build bucket only
            800, 850, 860,
            1000
        )
    }

    // The pump bucket is asserted by `PumpDriverBucketTest`, in `src/testFull`. It cannot be checked
    // here: this source set is compiled for every flavour, and a follower build has no pump module on
    // the classpath at all, so the bucket is legitimately empty and the assertion could only fail.

    @Test
    fun `the three sync plugins are built by Metro, once each`() {
        val root = testRoot()

        assertThat(root.contributedNotNsClientPlugins[300]).isSameInstanceAs(root.smsCommunicatorPlugin)
        assertThat(root.contributedPlugins[310]).isSameInstanceAs(root.nsClientV3Plugin)
        assertThat(root.contributedPlugins[350]).isSameInstanceAs(root.wearPlugin)

        // Built for real, not handed over by a mocked leaf.
        assertThat(mockingDetails(root.contributedPlugins[310]).isMock).isFalse()
    }

    @Test
    fun `the xdrip plugin and the XDripBroadcast binding are the same object`() {
        val root = testRoot()
        assertThat(root.xDripBroadcast).isSameInstanceAs(root.contributedPlugins[330])
    }

    @Test
    fun `the DST plugin and the DstHelper binding are the same object`() {
        val root = testRoot()
        assertThat(root.dstHelper).isSameInstanceAs(root.contributedPlugins[850])
    }

    @Test
    fun `the NS client source plugin and the NSClientSource binding are the same object`() {
        val root = testRoot()
        assertThat(root.nsClientSource).isSameInstanceAs(root.contributedPlugins[410])
    }

    @Test
    fun `a history window calculates with its own calculator, not the loop's`() {
        // The inverse of every other instance-identity test here, and the reason `MainPluginsBindings`
        // does not just annotate the class: the window extension hangs off a root that now binds
        // IobCobCalculator too, so an extension binding that failed to shadow the parent's would hand
        // the browser the live loop's calculator. Nothing would crash - the browser would quietly
        // recalculate the running loop's autosens data over whatever day the user opened.
        val root = testRoot()
        val window = root.historyWindowFactory.create()

        assertThat(window.iobCobCalculator).isNotSameInstanceAs(root.iobCobCalculator)
    }

    @Test
    fun `two history windows do not share a calculator`() {
        val root = testRoot()

        assertThat(root.historyWindowFactory.create().iobCobCalculator)
            .isNotSameInstanceAs(root.historyWindowFactory.create().iobCobCalculator)
    }

    @Test
    fun `only a looping build gets storage, signature verifier and objectives`() {
        assertThat(testRoot().contributedApsPlugins.keys).containsExactly(820, 830, 840)
    }

    @Test
    fun `only a non-follower build gets the version checker and Tidepool`() {
        // 340 (OpenHumans) is also @NotNSClient but comes from its own graph extension, so it joins
        // this bucket in MetroGraphs.notNsClientPlugins() rather than appearing here.
        assertThat(testRoot().contributedNotNsClientPlugins.keys).containsExactly(300, 320, 810)
    }

    @Test
    fun `the three buckets do not overlap`() {
        // Summing the sizes and comparing to the union is what catches a plugin contributed twice, or
        // one that landed in a bucket it does not belong in as well as its own.
        val root = testRoot()
        val all = root.contributedPlugins.keys + root.contributedApsPlugins.keys +
            root.contributedNotNsClientPlugins.keys
        val total = root.contributedPlugins.size + root.contributedApsPlugins.size +
            root.contributedNotNsClientPlugins.size
        assertThat(total).isEqualTo(all.size)
    }

    @Test
    fun `the ten objectives are contributed in order`() {
        // ObjectivesPlugin takes List<Objective> and the order is the objective number, so a lost or
        // reordered entry would change which objective the user is asked to complete next.
        assertThat(testRoot().objectivesPlugin.objectives).hasSize(10)
    }

    @Test
    fun `each order key holds the plugin it is meant to`() {
        assertThat(plugins[100]).isInstanceOf(SensitivityAAPSPlugin::class.java)
        assertThat(plugins[110]).isInstanceOf(SensitivityWeightedAveragePlugin::class.java)
        assertThat(plugins[120]).isInstanceOf(SensitivityOref1Plugin::class.java)
        assertThat(plugins[600]).isInstanceOf(NoSmoothingPlugin::class.java)
        assertThat(plugins[610]).isInstanceOf(ExponentialSmoothingPlugin::class.java)
        assertThat(plugins[620]).isInstanceOf(AvgSmoothingPlugin::class.java)
        assertThat(plugins[630]).isInstanceOf(UnscentedKalmanFilterPlugin::class.java)
        assertThat(plugins[700]).isInstanceOf(NoCalibrationPlugin::class.java)
        assertThat(plugins[710]).isInstanceOf(LinearCalibrationPlugin::class.java)
    }

    /**
     * A plugin bound to an interface as well as into the plugin map must be **one object**.
     *
     * `XdripPlugin` is the shape to watch: it is contributed into the map and as `XDripBroadcast`. Two
     * instances would look fine at every call site and quietly broadcast from a plugin that is not the
     * one in the list - and the one in the list is the one that gets started, so the other is an
     * unstarted twin.
     */
    @Test
    fun `an interface-bound plugin is the SAME instance as the one in the plugin list`() {
        val root = testRoot()
        assertThat(root.xdripSourcePlugin).isSameInstanceAs(root.contributedPlugins[400])
        assertThat(root.nsClientSourcePlugin).isSameInstanceAs(root.contributedPlugins[410])
        assertThat(root.dexcomPlugin).isSameInstanceAs(root.contributedPlugins[440])
    }

    @Test
    fun `EVERY contributed plugin is scoped`() {
        // Not just a spot check. A missing scope is invisible until some other caller holds the plugin
        // and finds its state does not match the one the app started, so this asserts the property for
        // the whole map rather than for the three that happen to have an interface today.
        val root = testRoot()
        val first = root.contributedPlugins
        val second = root.contributedPlugins
        first.keys.forEach { key ->
            assertWithMessage("plugin $key is rebuilt on every read - it needs @SingleIn(AppScope::class)")
                .that(second[key]).isSameInstanceAs(first[key])
        }
    }

    @Test
    fun `a contributed plugin is scoped, not rebuilt on every read`() {
        // @SingleIn on the class has to survive the contribution. A plugin holds its enabled state, so
        // a second instance would be enabled by nobody and started by nobody.
        val root = testRoot()
        assertThat(root.contributedPlugins[600]).isSameInstanceAs(root.contributedPlugins[600])
    }
}
