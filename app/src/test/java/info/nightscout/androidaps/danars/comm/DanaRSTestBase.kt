package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock

open class DanaRSTestBase : TestBaseWithProfile() {

    @Mock lateinit var sp: SP

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
        array[DanaRS_Packet.DATA_START + position] = (value and 0xFF).toByte()
        array[DanaRS_Packet.DATA_START + position + 1] = ((value and 0xFF00) shr 8).toByte()
        return array
    }

    fun putByteToArray(array: ByteArray, position: Int, value: Byte): ByteArray {
        array[DanaRS_Packet.DATA_START + position] = value
        return array
    }

    @Before
    fun setup() {
        danaPump = info.nightscout.androidaps.dana.DanaPump(aapsLogger, sp, injector)
    }
}