package app.aaps.pump.virtual

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class VirtualPumpPluginUTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var virtualPumpPlugin: VirtualPumpPlugin

    @BeforeEach
    fun prepareMocks() {
        virtualPumpPlugin = VirtualPumpPlugin(
            aapsLogger, rxBus, fabricPrivacy, rh, aapsSchedulers, sp, profileFunction,
            commandQueue, pumpSync, config, dateUtil, processedDeviceStatusData, persistenceLayer, instantiator
        )
    }

    @Test
    fun refreshConfiguration() {
        `when`(sp.getString(app.aaps.core.utils.R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpType).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }

    @Test
    fun refreshConfigurationTwice() {
        `when`(sp.getString(app.aaps.core.utils.R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        `when`(sp.getString(app.aaps.core.utils.R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpType).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }
}
