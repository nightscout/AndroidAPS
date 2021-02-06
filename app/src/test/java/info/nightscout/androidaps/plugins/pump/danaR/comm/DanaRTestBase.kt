package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest

@PrepareForTest(DetailedBolusInfoStorage::class, ConstraintChecker::class)
open class DanaRTestBase : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var activePluginProvider: ActivePluginProvider
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var databaseHelper: DatabaseHelperInterface
    @Mock lateinit var treatmentsInterface: TreatmentsInterface
    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var danaRv2Plugin: DanaRv2Plugin
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var configBuilder: ConfigBuilderInterface
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var nsUpload: NSUpload

    @Before
    fun prepareMock() {
        `when`(activePluginProvider.activeTreatments).thenReturn(treatmentsInterface)
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
                it.activePlugin = activePluginProvider
                it.configBuilder = configBuilder
                it.detailedBolusInfoStorage = detailedBolusInfoStorage
                it.constraintChecker = constraintChecker
                it.nsUpload = nsUpload
                it.databaseHelper = databaseHelper
                it.commandQueue = commandQueue
            }
            if (it is TemporaryBasal) {
                it.aapsLogger = aapsLogger
                it.activePlugin = activePluginProvider
                it.profileFunction = profileFunction
                it.sp = sp
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

    @Before
    fun setup() {
        danaPump = info.nightscout.androidaps.dana.DanaPump(aapsLogger, sp, injector)
    }
}