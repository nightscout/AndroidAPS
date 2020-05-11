package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.interfaces.TreatmentsInterface
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`

open class DanaRTestBase : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var activePluginProvider: ActivePluginProvider
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var databaseHelper: DatabaseHelperInterface
    @Mock lateinit var treatmentsInterface: TreatmentsInterface

    @Before
    fun prepareMock() {
        `when`(activePluginProvider.activeTreatments).thenReturn(treatmentsInterface)
    }

    val injector = HasAndroidInjector {
        AndroidInjector {
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