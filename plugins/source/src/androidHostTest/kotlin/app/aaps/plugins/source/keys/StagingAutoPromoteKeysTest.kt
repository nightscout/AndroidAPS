package app.aaps.plugins.source.keys

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The automatic switch from a pre-soak sensor stops the sensor in use, so it must stay off until
 * the user turns it on.
 */
class StagingAutoPromoteKeysTest {

    @Test
    fun `the automatic switch is off by default`() {
        assertThat(DexcomOnePlusBooleanKey.AutoPromote.defaultValue).isFalse()
    }

    @Test
    fun `the default delay is 12 hours`() {
        assertThat(DexcomOnePlusIntKey.AutoPromoteHours.defaultValue).isEqualTo(12)
    }

    @Test
    fun `the delay is hidden until the switch is on`() {
        assertThat(DexcomOnePlusIntKey.AutoPromoteHours.dependency).isEqualTo(DexcomOnePlusBooleanKey.AutoPromote)
    }
}
