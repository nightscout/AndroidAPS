package app.aaps.plugins.source.keys

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The automatic switch to the pre-soak sensor is off by default, and it is a normal setting.
 *
 * The pre-soak itself and the keep-alive service need no setting: the pre-soak is always offered,
 * and the service runs while a sensor is stored.
 */
class Libre3StagingAutoPromoteKeysTest {

    @Test
    fun `the automatic switch is the only switch, it is off and it is not exported`() {
        assertThat(Libre3BooleanKey.entries).containsExactly(Libre3BooleanKey.AutoPromote)

        val autoPromote = Libre3BooleanKey.AutoPromote
        assertThat(autoPromote.key).isEqualTo("libre3_staging_auto_promote")
        assertThat(autoPromote.defaultValue).isFalse()
        assertThat(autoPromote.engineeringModeOnly).isFalse()
        assertThat(autoPromote.exportable).isFalse()
        assertThat(autoPromote.dependency).isNull()
    }

    @Test
    fun `the delay is 10 hours by default, between 6 and 24, and only shown with the switch on`() {
        val hours = Libre3IntKey.AutoPromoteHours

        assertThat(hours.key).isEqualTo("libre3_staging_auto_promote_hours")
        assertThat(hours.defaultValue).isEqualTo(10)
        assertThat(hours.min).isEqualTo(6)
        assertThat(hours.max).isEqualTo(24)
        assertThat(hours.dependency).isEqualTo(Libre3BooleanKey.AutoPromote)
    }
}
