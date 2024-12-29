package app.aaps.pump.equil

import android.content.SharedPreferences
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.ui.R
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito

class EquilPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sharedPreferences: SharedPreferences
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var equilManager: EquilManager

    private lateinit var equilPumpPlugin: EquilPumpPlugin

    init {
        addInjector {
            if (it is AdaptiveDoublePreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
                it.config = config
            }
            if (it is AdaptiveListIntPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
        }
    }

    @BeforeEach
    fun prepareMocks() {

        Mockito.`when`(rh.gs(anyInt())).thenReturn("")
        equilPumpPlugin =
            EquilPumpPlugin(
                aapsLogger,
                rh,
                commandQueue,
                aapsSchedulers,
                rxBus,
                context,
                sp,
                fabricPrivacy,
                dateUtil,
                pumpSync,
                equilManager,
                decimalFormatter,
                instantiator,
                preferences
            )
    }

    @Test
    fun addPreferenceScreen() {
        val screen = preferenceManager.createPreferenceScreen(context)
        equilPumpPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}