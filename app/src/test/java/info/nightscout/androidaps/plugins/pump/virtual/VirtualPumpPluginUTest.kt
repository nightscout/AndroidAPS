package info.nightscout.androidaps.plugins.pump.virtual

import dagger.android.AndroidInjector
import info.nightscout.androidaps.utils.buildHelper.ConfigImpl
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(FabricPrivacy::class, DateUtil::class)
class VirtualPumpPluginUTest : TestBase() {

    private val rxBus = RxBusWrapper(aapsSchedulers)
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pumpSync: PumpSync

    lateinit var virtualPumpPlugin: VirtualPumpPlugin

    @Before
    fun prepareMocks() {
        virtualPumpPlugin = VirtualPumpPlugin({ AndroidInjector { } }, aapsLogger, rxBus, fabricPrivacy, resourceHelper, aapsSchedulers, sp, profileFunction, iobCobCalculator, commandQueue, pumpSync, ConfigImpl(), dateUtil)
    }

    @Test
    fun refreshConfiguration() {
        PowerMockito.`when`(sp.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        Assert.assertEquals(PumpType.ACCU_CHEK_COMBO, virtualPumpPlugin.pumpType)
    }

    @Test
    fun refreshConfigurationTwice() {
        PowerMockito.`when`(sp.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        PowerMockito.`when`(sp.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        Assert.assertEquals(PumpType.ACCU_CHEK_COMBO, virtualPumpPlugin.pumpType)
    }
}