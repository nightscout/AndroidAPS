package app.aaps.plugins.dexcomoneplus.gatt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.os.Build

/**
 * The phone's Bluetooth audio links (car kit, headset) as one log fragment.
 *
 * Audio uses the same radio as BLE and goes first. The sensor link fails much more often while a
 * car or a headset is connected, so every link loss is logged with this state. Without it a field
 * log cannot show whether a lost reading came with audio in use.
 */
internal object OnePlusAudioLinkState {

    /** Never throws: a missing permission or service shows as `?` in the log. */
    fun describe(context: Context, adapter: BluetoothAdapter?): String {
        val leAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            profileState(adapter, BluetoothProfile.LE_AUDIO)
        } else {
            null
        }
        val musicActive = try {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive
        } catch (_: Throwable) {
            null
        }
        return format(
            a2dp = profileState(adapter, BluetoothProfile.A2DP),
            headset = profileState(adapter, BluetoothProfile.HEADSET),
            leAudio = leAudio,
            musicActive = musicActive,
        )
    }

    fun format(a2dp: Int?, headset: Int?, leAudio: Int?, musicActive: Boolean?): String =
        "audio a2dp=${stateName(a2dp)} headset=${stateName(headset)} " +
            "leAudio=${stateName(leAudio)} music=${musicActive?.let { if (it) "on" else "off" } ?: "?"}"

    private fun profileState(adapter: BluetoothAdapter?, profile: Int): Int? = try {
        adapter?.getProfileConnectionState(profile)
    } catch (_: Throwable) {
        null
    }

    private fun stateName(state: Int?): String = when (state) {
        BluetoothProfile.STATE_CONNECTED     -> "on"
        BluetoothProfile.STATE_DISCONNECTED  -> "off"
        BluetoothProfile.STATE_CONNECTING    -> "connecting"
        BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
        else                                 -> "?"
    }
}
