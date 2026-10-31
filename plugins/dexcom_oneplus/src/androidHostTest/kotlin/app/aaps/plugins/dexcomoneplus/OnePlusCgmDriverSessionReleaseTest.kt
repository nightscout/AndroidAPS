package app.aaps.plugins.dexcomoneplus

import app.aaps.plugins.dexcomoneplus.session.OnePlusBleSession
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What happens to the driver's "a session is running" state when a link ends by itself.
 *
 * `OnePlusBleSession.fail` - the terminal end of the retry ladder - returns normally instead of
 * throwing, so the driver used to keep `session` and `resumeQueued` set for ever. `resumeStoredSession`
 * refuses to start anything while either is set, so the plugin's reconnect watchdog answered "already
 * active" at every wake-up and no glucose ever came back without an app restart.
 *
 * This is an `androidHostTest` and not a `commonTest` because it reaches the private state of the
 * driver through JVM reflection. The code under test itself is in commonMain.
 */
class OnePlusCgmDriverSessionReleaseTest {

    private class FakeSession : OnePlusBleSession {

        override fun startWithPairingCode(deviceAddress: String, pairingCode: String) = Unit
        override fun stop(reason: String?) = Unit
        override fun isUp(): Boolean = false
        override fun warmupState(): OnePlusWarmupState = OnePlusWarmupState(phase = OnePlusWarmupState.Phase.FAILED)
    }

    private fun field(name: String) = OnePlusCgmDriverReal::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun release(driver: OnePlusCgmDriverReal, generation: Long, ended: OnePlusBleSession) {
        OnePlusCgmDriverReal::class.java
            .getDeclaredMethod("releaseSessionIfCurrent", Long::class.java, OnePlusBleSession::class.java)
            .apply { isAccessible = true }
            .invoke(driver, generation, ended)
    }

    @Test
    fun `a session that ended frees the driver for the next attempt`() {
        val driver = OnePlusCgmDriverReal()
        val ended = FakeSession()
        field("session").set(driver, ended)
        field("resumeQueued").set(driver, true)
        val generation = field("operationGeneration").getLong(driver)

        release(driver, generation, ended)

        assertThat(field("session").get(driver)).isNull()
        assertThat(field("resumeQueued").getBoolean(driver)).isFalse()
    }

    @Test
    fun `a newer session is not released by the old one unwinding`() {
        // A connect started while the old link was still ending: the old thread must not clear the
        // state of the session that has just replaced it.
        val driver = OnePlusCgmDriverReal()
        val old = FakeSession()
        val current = FakeSession()
        field("session").set(driver, current)
        field("resumeQueued").set(driver, true)
        val generation = field("operationGeneration").getLong(driver)

        release(driver, generation, old)

        assertThat(field("session").get(driver)).isSameInstanceAs(current)
        assertThat(field("resumeQueued").getBoolean(driver)).isTrue()
    }

    @Test
    fun `a session from an older operation generation releases nothing`() {
        val driver = OnePlusCgmDriverReal()
        val ended = FakeSession()
        field("session").set(driver, ended)
        field("resumeQueued").set(driver, true)
        val generation = field("operationGeneration").getLong(driver)

        release(driver, generation - 1, ended)

        assertThat(field("session").get(driver)).isSameInstanceAs(ended)
        assertThat(field("resumeQueued").getBoolean(driver)).isTrue()
    }
}
