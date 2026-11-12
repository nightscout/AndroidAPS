package app.aaps.plugins.dexcomoneplus

import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusCgmDriverResumePolicyTest {

    @Test
    fun `complete stored session can resume`() {
        val stored = storedSession()

        assertThat(OnePlusCgmDriverResumePolicy.canResume(stored)).isTrue()
    }

    @Test
    fun `resume requires valid pin MAC and shared key`() {
        assertThat(OnePlusCgmDriverResumePolicy.canResume(null)).isFalse()
        assertThat(
            OnePlusCgmDriverResumePolicy.canResume(
                storedSession().copy(lastMac = null),
            ),
        ).isFalse()
        assertThat(
            OnePlusCgmDriverResumePolicy.canResume(
                storedSession().copy(identity = OnePlusSensorIdentity(pin = "12")),
            ),
        ).isFalse()
        assertThat(
            OnePlusCgmDriverResumePolicy.canResume(
                storedSession().copy(sharedKey = null),
            ),
        ).isFalse()
        assertThat(
            OnePlusCgmDriverResumePolicy.canResume(
                storedSession().copy(sharedKey = ByteArray(15)),
            ),
        ).isFalse()
    }

    private fun storedSession() = OnePlusStoredSession(
        identity = OnePlusSensorIdentity(pin = "1234"),
        lastMac = "DA:3B:12:0D:5B:B7",
        lastDeviceName = "DX02aS",
        sharedKey = ByteArray(16),
    )
}
