package app.aaps.plugins.dexcomoneplus.gatt

import android.bluetooth.BluetoothProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusAudioLinkStateTest {

    @Test
    fun `a headset playing music is visible in the log line`() {
        val line = OnePlusAudioLinkState.format(
            a2dp = BluetoothProfile.STATE_CONNECTED,
            headset = BluetoothProfile.STATE_CONNECTED,
            leAudio = BluetoothProfile.STATE_DISCONNECTED,
            musicActive = true,
        )

        assertThat(line).isEqualTo("audio a2dp=on headset=on leAudio=off music=on")
    }

    @Test
    fun `an unknown state shows as a question mark instead of failing`() {
        val line = OnePlusAudioLinkState.format(a2dp = null, headset = 42, leAudio = null, musicActive = null)

        assertThat(line).isEqualTo("audio a2dp=? headset=? leAudio=? music=?")
    }
}
