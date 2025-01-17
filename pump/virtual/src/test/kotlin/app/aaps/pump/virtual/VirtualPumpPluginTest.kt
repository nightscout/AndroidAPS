package app.aaps.pump.virtual

import android.content.SharedPreferences
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.StringKey
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class VirtualPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var sharedPreferences: SharedPreferences

    private lateinit var virtualPumpPlugin: VirtualPumpPlugin

    init {
        addInjector {
            if (it is AdaptiveListPreference) {
                it.preferences = preferences
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
                it.config = config
            }
        }
    }

    @BeforeEach
    fun prepareMocks() {
        virtualPumpPlugin = VirtualPumpPlugin(
            aapsLogger, rxBus, fabricPrivacy, rh, aapsSchedulers, preferences, profileFunction,
            commandQueue, pumpSync, config, dateUtil, processedDeviceStatusData, persistenceLayer, instantiator
        )
    }

    @Test
    fun refreshConfiguration() {
        `when`(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpType).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }

    @Test
    fun refreshConfigurationTwice() {
        `when`(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        `when`(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpType).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        virtualPumpPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
