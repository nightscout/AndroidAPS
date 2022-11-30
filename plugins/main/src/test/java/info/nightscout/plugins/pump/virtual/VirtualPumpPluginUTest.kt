package info.nightscout.plugins.pump.virtual

import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.plugins.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class VirtualPumpPluginUTest : TestBase() {

    private val rxBus = RxBus(aapsSchedulers, aapsLogger)
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var config: Config

    lateinit var virtualPumpPlugin: VirtualPumpPlugin

    @BeforeEach
    fun prepareMocks() {
        virtualPumpPlugin = VirtualPumpPlugin({ AndroidInjector { } }, aapsLogger, rxBus, fabricPrivacy, rh, aapsSchedulers, sp, profileFunction, iobCobCalculator, commandQueue, pumpSync, config, dateUtil)
    }

    @Test
    fun refreshConfiguration() {
        `when`(sp.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        Assert.assertEquals(PumpType.ACCU_CHEK_COMBO, virtualPumpPlugin.pumpType)
    }

    @Test
    fun refreshConfigurationTwice() {
        `when`(sp.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        `when`(sp.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        Assert.assertEquals(PumpType.ACCU_CHEK_COMBO, virtualPumpPlugin.pumpType)
    }
}