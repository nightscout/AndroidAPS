package app.aaps.plugins.source

import android.content.Context
import android.content.SharedPreferences
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.shared.tests.SharedPreferencesMock
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** `OnePlusSensorStore.overwriteSessionStart`, used when the sensor reports its real start. */
class DexcomOnePlusSessionStartOverwriteTest {

    private val prefs: SharedPreferences = SharedPreferencesMock()
    private val context: Context = mock<Context>().also {
        whenever(it.applicationContext).thenReturn(it)
        whenever(it.getSharedPreferences(PRODUCTION_PREFS, Context.MODE_PRIVATE)).thenReturn(prefs)
    }

    @Test
    fun `overwriteSessionStart ignores a non positive time`() {
        val current = 1_700_000_000_000L
        val store = OnePlusSensorStore(context)
        store.startSessionForSensor(MAC, current, null)

        store.overwriteSessionStart(0L)
        store.overwriteSessionStart(-1L)

        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(current)
        store.overwriteSessionStart(current - HOUR_MS)
        assertThat(prefs.getLong(KEY_SESSION_START, 0L)).isEqualTo(current - HOUR_MS)
    }

    companion object {

        private const val HOUR_MS = 60L * 60L * 1000L
        private const val MAC = "AA:BB:CC:DD:EE:30"
        private const val PRODUCTION_PREFS = "dexcom_oneplus_sensor"
        private const val KEY_SESSION_START = "session_start_ms"
    }
}
