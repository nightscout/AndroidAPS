package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.dana.database.DanaHistoryRecordDao
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.doNothing
import org.powermock.core.classloader.annotations.PrepareForTest

@PrepareForTest(DetailedBolusInfoStorage::class, ConstraintChecker::class, DanaRKoreanPlugin::class, DanaRPlugin::class)
open class DanaRTestBase : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var danaRv2Plugin: DanaRv2Plugin
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao

    private lateinit var testPumpPlugin: TestPumpPlugin

    @Before
    fun setup() {
        danaPump = DanaPump(aapsLogger, sp, dateUtil, injector)
        testPumpPlugin = TestPumpPlugin(injector)
        `when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        doNothing().`when`(danaRKoreanPlugin).setPluginEnabled(anyObject(), anyBoolean())
        doNothing().`when`(danaRPlugin).setPluginEnabled(anyObject(), anyBoolean())
        `when`(resourceHelper.gs(ArgumentMatchers.anyInt())).thenReturn("")
    }

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is MessageBase) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.danaPump = danaPump
                it.danaRPlugin = danaRPlugin
                it.danaRKoreanPlugin = danaRKoreanPlugin
                it.danaRv2Plugin = danaRv2Plugin
                it.rxBus = RxBusWrapper(aapsSchedulers)
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
                it.configBuilder = configBuilder
                it.detailedBolusInfoStorage = detailedBolusInfoStorage
                it.constraintChecker = constraintChecker
                it.commandQueue = commandQueue
                it.pumpSync = pumpSync
                it.danaHistoryRecordDao = danaHistoryRecordDao
            }
        }
    }

    lateinit var danaPump: DanaPump

    fun createArray(length: Int, fillWith: Byte): ByteArray {
        val ret = ByteArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    fun createArray(length: Int, fillWith: Double): Array<Double> {
        val ret = Array(length) { 0.0 }
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    fun putIntToArray(array: ByteArray, position: Int, value: Int): ByteArray {
        array[6 + position + 1] = (value and 0xFF).toByte()
        array[6 + position] = ((value and 0xFF00) shr 8).toByte()
        return array
    }

    fun putByteToArray(array: ByteArray, position: Int, value: Byte): ByteArray {
        array[6 + position] = value
        return array
    }
}