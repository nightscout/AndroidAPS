package app.aaps.plugins.libre3.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Ending a session must never reach the sensor's command channel. A sensor that is told to stop
 * cannot be started again, so no reason at all may unlock that write.
 */
class Libre3DisconnectPolicyTest {

    @Test
    fun `no reason ever allows a command to be written to the sensor`() {
        for (reason in Libre3DisconnectPolicy.Reason.entries) {
            assertThat(Libre3DisconnectPolicy.mayWriteSensorCommand(reason)).isFalse()
        }
    }

    @Test
    fun `every reason may drop the link, which is the only allowed action`() {
        for (reason in Libre3DisconnectPolicy.Reason.entries) {
            assertThat(Libre3DisconnectPolicy.mayDropLink(reason)).isTrue()
        }
    }
}
