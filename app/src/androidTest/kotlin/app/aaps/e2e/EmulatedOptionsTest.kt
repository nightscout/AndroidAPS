package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.di.EmulatedOptions
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.testcategories.ShardB
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [EmulatedOptions] turns on only the options a test asks for. If it turned on all of them, every test would run with
 * every emulator and engineering mode on, and a pump test could pass on the wrong pump. That the asked option is on is
 * shown by every pump test, which gets its emulator only that way.
 */
@RunWith(AndroidJUnit4::class)
@ShardB
class EmulatedOptionsTest {

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    @Before
    fun setUp() {
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_DANA_RS_V3)
    }

    @After
    fun tearDown() {
        EmulatedOptions.enabled = emptySet()
    }

    @Test
    fun optionsNotAskedForStayOff() {
        // No AAPS directory is granted in the test process, so the real file lookup finds nothing.
        assertThat(testGraphs.config.isEnabled(ExternalOptions.EMULATE_EQUIL)).isFalse()
        assertThat(testGraphs.config.isEnabled(ExternalOptions.ENGINEERING_MODE)).isFalse()
    }
}
