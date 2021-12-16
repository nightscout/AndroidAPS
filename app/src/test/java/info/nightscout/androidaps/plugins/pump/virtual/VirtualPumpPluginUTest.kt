package info.nightscout.androidaps.plugins.pump.virtual

import dagger.android.AndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
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

    @Before
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