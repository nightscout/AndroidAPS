package app.aaps.di.metro

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import dev.zacsweers.metro.IntKey as MetroIntKey

/**
 * Pump drivers reach the `@PumpDriver` bucket, and only that bucket.
 * The bucket is new: before this there was no Metro `@PumpDriver` map at all, so a pump plugin
 * contributed under that qualifier went nowhere - nothing read it, and the plugin simply did
 * not appear in the list. That is the same silent failure the virtual pump hit with `@AllConfigs`, and
 * it cannot be seen from the annotation on the plugin.
 * `:app` merges this bucket only when `config.PUMPDRIVERS`, so a driver landing in the unqualified map
 * instead would show up in a follower build that has no pump at all.
 *
 * ## Nothing here names a pump
 *
 * It used to import all fourteen plugin classes and restate the key of each, which made the set of
 * modules in `settings.gradle` a compile-time dependency of this test: removing one stopped
 * `:app:testFullDebugUnitTest` from compiling at all. Both sides are now read from the build itself -
 * the bucket from the graph, the expectation from the classes actually on the classpath - so a build
 * with fewer pumps simply has a smaller bucket and every assertion still means what it says.
 */
class PumpDriverBucketTest {

    /** Keys declared by the drivers compiled into THIS build, read from their own annotations. */
    private fun declaredDriverKeys(): Set<Int> =
        aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { it.isAnnotationPresent(PumpDriver::class.java) }
            .mapNotNull { it.getAnnotation(MetroIntKey::class.java)?.value }
            .toSet()

    /**
     * The original assertion, with the expectation derived instead of written out. It still catches a
     * driver quietly *disappearing* from the bucket - the class is on the classpath and declares a key,
     * so a missing entry is a mismatch - and it now also catches one that arrives without declaring
     * `@PumpDriver` at all.
     */
    @Test
    fun `the pump bucket holds exactly the drivers compiled into this build`() {
        val declared = declaredDriverKeys()
        check(declared.isNotEmpty()) { "Found no @PumpDriver classes on the classpath - the scan broke" }

        assertThat(testRoot().contributedPumpDriverPlugins.keys).isEqualTo(declared)
    }

    /**
     * Each driver is filed under the key it declares. This is what the fourteen `isInstanceOf` lines
     * checked, without the test having to know which class belongs to which number - the plugin already
     * says so with `@MetroIntKey`, and restating it in a second place only created something to keep in
     * step.
     */
    @Test
    fun `every pump driver is filed under its own declared key`() {
        val misfiled = testRoot().contributedPumpDriverPlugins
            .filter { (key, plugin) -> plugin::class.java.getAnnotation(MetroIntKey::class.java)?.value != key }
            .map { (key, plugin) -> "${plugin::class.java.simpleName} is filed under $key" }

        assertThat(misfiled).isEmpty()
    }

    /**
     * There is exactly one of each driver, whichever framework built it.
     * What still has to hold is **one instance**: reading a driver twice must give the same object. Two
     * would put the plugin list and the pump service on different state, which is the whole failure this
     * file exists to prevent.
     */
    @Test
    fun `each pump driver is a single instance`() {
        val root = testRoot()
        val first = root.contributedPumpDriverPlugins
        val second = root.contributedPumpDriverPlugins

        val rebuilt = first.keys.filter { key -> first[key] !== second[key] }

        assertThat(rebuilt).isEmpty()
    }

    /**
     * Eros was the last one out, and how it got out is worth recording: `AapsOmnipodErosManager` and
     * `OmnipodRileyLinkCommunicationManager` are still Java, and Metro cannot generate a factory from an
     * `@Inject` constructor it has no Kotlin IR for. It does not need to. A hand written `@Provides` in
     * `ErosJavaBindings` calls the constructor itself, and a scope on that provider makes Metro the
     * owner. Java is not a blocker; only an *unwritten* binding is.
     */
    @Test
    fun `every pump driver is Metro owned`() {
        val drivers = testRoot().contributedPumpDriverPlugins

        val handedOver = drivers.filterValues { mockingDetails(it).isMock }.keys
        assertThat(handedOver).isEmpty()
    }

    @Test
    fun `no pump driver leaks into the every-build bucket`() {
        // Where a wrong qualifier would put them. `:app` merges the every-build bucket unconditionally,
        // so a driver landing there would appear in a follower that has no pump at all - and nothing
        // would report it.
        val root = testRoot()

        assertThat(root.contributedPlugins.keys).containsNoneIn(root.contributedPumpDriverPlugins.keys)
    }
}
