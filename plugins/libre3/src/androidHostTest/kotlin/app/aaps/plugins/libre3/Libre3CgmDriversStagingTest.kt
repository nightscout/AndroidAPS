package app.aaps.plugins.libre3

import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.plugins.libre3.session.Libre3MacArbiter
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * The second driver is a real instance with its own preferences namespace. A stopped instance is
 * dropped, so the next call builds another one. A `by lazy` field would hand the stopped instance
 * out again, and that instance can never open a session.
 */
class Libre3CgmDriversStagingTest {

    @AfterEach
    fun dropInstances() {
        Libre3CgmDrivers.releaseStagingInstance()?.let { driver ->
            try {
                driver.shutdown()
            } catch (_: Throwable) {
            }
        }
        val productionField = Libre3CgmDrivers::class.java.getDeclaredField("productionReal")
        productionField.isAccessible = true
        val production = productionField.get(Libre3CgmDrivers) as Libre3CgmDriverReal?
        productionField.set(Libre3CgmDrivers, null)
        if (production != null) {
            try {
                production.shutdown()
            } catch (_: Throwable) {
            }
        }
        Libre3MacArbiter.reset()
    }

    @Test
    fun `the pre-soak namespace is its own file and production keeps the original`() {
        assertThat(Libre3CgmDrivers.storeNamespace(SensorSlot.PRODUCTION)).isNull()
        assertThat(Libre3CgmDrivers.storeNamespace(SensorSlot.STAGING))
            .isEqualTo(Libre3CgmDrivers.STAGING_NAMESPACE)
        assertThat(Libre3CgmDrivers.STAGING_NAMESPACE).isEqualTo("staging")
    }

    @Test // A stop must be able to reach the instance that runs without building one
    fun `productionOrNull builds nothing and hides a stopped instance`() {
        dropInstances()
        assertThat(Libre3CgmDrivers.productionOrNull()).isNull()
        // Asking did not build one: still nothing there.
        assertThat(Libre3CgmDrivers.productionOrNull()).isNull()

        val built = Libre3CgmDrivers.realProduction()
        assertThat(Libre3CgmDrivers.productionOrNull()).isSameInstanceAs(built)

        built.shutdown()
        assertThat(Libre3CgmDrivers.productionOrNull()).isNull()
    }

    @Test
    fun `releasing the pre-soak builds a fresh instance the next time`() {
        val first = Libre3CgmDrivers.staging()
        assertThat(Libre3CgmDrivers.staging()).isSameInstanceAs(first)
        assertThat(first.currentRole).isEqualTo(SensorSlot.STAGING)

        val released = Libre3CgmDrivers.releaseStagingInstance()
        assertThat(released).isSameInstanceAs(first)
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        released!!.shutdown()

        val second = Libre3CgmDrivers.staging()
        assertThat(second).isNotSameInstanceAs(first)
        assertThat(second.currentRole).isEqualTo(SensorSlot.STAGING)
    }

    @Test
    fun `the plugin always gets the real production driver`() {
        assertThat(Libre3CgmDrivers.default()).isSameInstanceAs(Libre3CgmDrivers.realProduction())
    }

    @Test
    fun `a production driver that was shut down is replaced by a new one`() {
        val first = Libre3CgmDrivers.realProduction()
        first.shutdown()
        assertThat(first.isShutDown()).isTrue()

        val second = Libre3CgmDrivers.realProduction()

        assertThat(second).isNotSameInstanceAs(first)
        assertThat(second.isShutDown()).isFalse()
        assertThat(Libre3CgmDrivers.default()).isSameInstanceAs(second)
    }

    @Test
    fun `promote hands the pre-soak instance the production role and frees the slot`() {
        val production = Libre3CgmDrivers.realProduction()
        val presoak = Libre3CgmDrivers.staging()

        val retired = Libre3CgmDrivers.promoteStagingInstance()

        assertThat(retired).isSameInstanceAs(production)
        assertThat(Libre3CgmDrivers.realProduction()).isSameInstanceAs(presoak)
        assertThat(presoak.currentRole).isEqualTo(SensorSlot.PRODUCTION)
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        val next = Libre3CgmDrivers.staging()
        assertThat(next).isNotSameInstanceAs(presoak)
    }

    @Test
    fun `yield does not stop a pre-soak that does not hold the sensor`() {
        val presoak = Libre3CgmDrivers.staging()
        var sessions = 0
        presoak.addWatcher(object : Libre3GlucoseWatcher {
            override fun onWarmup(state: Libre3WarmupState) = Unit
            override fun onGlucose(sample: Libre3GlucoseSample) = Unit
            override fun onSession(up: Boolean, reason: String?) {
                sessions++
            }
            override fun onError(message: String, fatal: Boolean) = Unit
        })
        val requester = Libre3CgmDrivers.realProduction()

        assertThat(
            Libre3CgmDrivers.yieldStagingSensorToProduction("AA:BB:CC:DD:EE:01", requester),
        ).isFalse()
        assertThat(Libre3CgmDrivers.stagingOrNull()).isSameInstanceAs(presoak)
        assertThat(sessions).isEqualTo(0)
    }

    @Test
    fun `yield still frees the slot when shutdown throws`() {
        val presoak = Libre3CgmDrivers.staging()
        var thrown = false
        presoak.addWatcher(object : Libre3GlucoseWatcher {
            override fun onWarmup(state: Libre3WarmupState) = Unit
            override fun onGlucose(sample: Libre3GlucoseSample) = Unit
            override fun onSession(up: Boolean, reason: String?) {
                if (!thrown) {
                    thrown = true
                    throw IllegalStateException("shutdown failed")
                }
            }
            override fun onError(message: String, fatal: Boolean) = Unit
        })
        val token = arbiterOwnerOf(presoak)
        val mac = "AA:BB:CC:DD:EE:01"
        assertThat(Libre3MacArbiter.claim(mac, token)).isTrue()
        val requester = Libre3CgmDrivers.realProduction()

        val yielded = Libre3CgmDrivers.yieldStagingSensorToProduction(mac, requester)

        assertThat(yielded).isTrue()
        assertThat(thrown).isTrue()
        assertThat(Libre3CgmDrivers.stagingOrNull()).isNull()
        // `stopSession` releases the claim before it tells the watchers. The watcher then throws,
        // `runCatching` keeps that throw inside `yield`, and the function still returns true. The
        // pointer was cleared before `shutdown`, so the slot is free either way.
        assertThat(Libre3MacArbiter.ownerOf(mac)).isNull()
        presoak.shutdown()
    }

    private fun arbiterOwnerOf(driver: Libre3CgmDriverReal): String {
        val field = Libre3CgmDriverReal::class.java.getDeclaredField("arbiterOwner")
        field.isAccessible = true
        return field.get(driver) as String
    }
}
