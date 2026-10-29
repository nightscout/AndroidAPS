package app.aaps.implementation.androidNotification

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The "off" half of "Override Do Not Disturb mode": quiet while Do Not Disturb is on, whatever the profile
 * allows. With the override on the player plays as usual; the profile decides whether the alarm stream is heard.
 */
class DoNotDisturbRuleTest {

    @Test
    fun `with Do Not Disturb off the alarm plays, whatever the setting`() {
        assertThat(DoNotDisturbRule.staysQuiet(overrideDnd = false, dndOn = false)).isFalse()
        assertThat(DoNotDisturbRule.staysQuiet(overrideDnd = true, dndOn = false)).isFalse()
    }

    @Test
    fun `with the override off the alarm stays quiet while Do Not Disturb is on`() {
        assertThat(DoNotDisturbRule.staysQuiet(overrideDnd = false, dndOn = true)).isTrue()
    }

    @Test
    fun `with the override on the alarm plays while Do Not Disturb is on`() {
        assertThat(DoNotDisturbRule.staysQuiet(overrideDnd = true, dndOn = true)).isFalse()
    }
}
