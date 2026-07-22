package app.aaps.e2e

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.di.EmulatedOptions
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.testcategories.ShardB
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.inject.Inject

/**
 * Proves the seam that lets an instrumented test drive a real pump driver against the in-tree pump
 * emulator, with no Bluetooth hardware: with [ExternalOptions.EMULATE_DANA_RS_V3] reported enabled,
 * `DanaModules.provideBleTransport` must hand out [EmulatorBleTransport] instead of the real
 * `BleTransportImpl`.
 *
 * Deliberately asserts only the wiring — no UI, no connect. If this fails, every pump E2E built on
 * top is meaningless, so it is worth failing loudly and cheaply on its own.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ShardB
class DanaRsEmulatorTransportTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var bleTransport: BleTransport
    @Inject lateinit var config: Config

    @Before
    fun setUp() {
        // Before inject(): BleTransport is @Singleton, so config.isEnabled is read once, when the
        // graph first constructs it.
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_DANA_RS_V3)
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        // Don't leak the emulated pump into sibling instrumented tests sharing this process.
        EmulatedOptions.enabled = emptySet()
    }

    @Test
    fun emulateDanaRsV3_isReportedEnabled() {
        assertThat(config.isEnabled(ExternalOptions.EMULATE_DANA_RS_V3)).isTrue()
    }

    @Test
    fun unrequestedOptions_fallThroughToProductionLookup() {
        // No AAPS directory is granted in-process, so the real file lookup finds nothing. Guards
        // against the decorator blanket-enabling everything.
        assertThat(config.isEnabled(ExternalOptions.EMULATE_EQUIL)).isFalse()
        assertThat(config.isEnabled(ExternalOptions.ENGINEERING_MODE)).isFalse()
    }

    @Test
    fun bleTransport_isTheEmulator() {
        assertThat(bleTransport).isInstanceOf(EmulatorBleTransport::class.java)
    }
}


