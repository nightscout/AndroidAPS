package app.aaps.pump.virtual

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.StringKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class VirtualPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var virtualPumpPlugin: VirtualPumpPlugin
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach
    fun prepareMocks() {
        virtualPumpPlugin = VirtualPumpPlugin(
            aapsLogger, rxBus, rh, preferences,
            commandQueue, pumpSync, config, dateUtil, persistenceLayer, pumpEnactResultProvider, notificationManager, ch, insulin, testScope
        )
    }

    @Test
    fun refreshConfiguration() {
        whenever(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpTypeFlow.value).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }

    @Test
    fun refreshConfigurationTwice() {
        whenever(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        whenever(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpTypeFlow.value).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        virtualPumpPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    @Test
    fun `requiredPermissions should return empty list`() {
        assertThat(virtualPumpPlugin.requiredPermissions()).isEmpty()
    }
}
