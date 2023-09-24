package info.nightscout.pump.virtual

import app.aaps.interfaces.configuration.Config
import app.aaps.interfaces.db.PersistenceLayer
import app.aaps.interfaces.iob.IobCobCalculator
import app.aaps.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.interfaces.profile.ProfileFunction
import app.aaps.interfaces.pump.PumpSync
import app.aaps.interfaces.pump.defs.PumpType
import app.aaps.interfaces.queue.CommandQueue
import app.aaps.interfaces.resources.ResourceHelper
import app.aaps.interfaces.sharedPreferences.SP
import app.aaps.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class VirtualPumpPluginUTest : TestBase() {

    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var config: Config
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var virtualPumpPlugin: VirtualPumpPlugin

    @BeforeEach
    fun prepareMocks() {
        virtualPumpPlugin = VirtualPumpPlugin(
            { AndroidInjector { } },
            aapsLogger, rxBus, fabricPrivacy, rh, aapsSchedulers, sp, profileFunction, iobCobCalculator,
            commandQueue, pumpSync, config, dateUtil, processedDeviceStatusData, persistenceLayer
        )
    }

    @Test
    fun refreshConfiguration() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        Assertions.assertEquals(PumpType.ACCU_CHEK_COMBO, virtualPumpPlugin.pumpType)
    }

    @Test
    fun refreshConfigurationTwice() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        Assertions.assertEquals(PumpType.ACCU_CHEK_COMBO, virtualPumpPlugin.pumpType)
    }
}