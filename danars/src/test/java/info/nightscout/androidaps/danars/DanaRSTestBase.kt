package info.nightscout.androidaps.danars

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.comm.DanaRSPacket
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito

open class DanaRSTestBase : TestBaseWithProfile() {

    @Mock lateinit var sp: SP

    val injector = HasAndroidInjector { AndroidInjector { } }

    lateinit var danaPump: DanaPump

    @Before
    fun prepare() {
        Mockito.`when`(resourceHelper.gs(ArgumentMatchers.anyInt())).thenReturn("AnyString")
    }

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

    @Suppress("unused")
    fun putIntToArray(array: ByteArray, position: Int, value: Int): ByteArray {
        array[DanaRSPacket.DATA_START + position] = (value and 0xFF).toByte()
        array[DanaRSPacket.DATA_START + position + 1] = ((value and 0xFF00) shr 8).toByte()
        return array
    }

    fun putByteToArray(array: ByteArray, position: Int, value: Byte): ByteArray {
        array[DanaRSPacket.DATA_START + position] = value
        return array
    }

    @Before
    fun setup() {
        danaPump = DanaPump(aapsLogger, sp, dateUtil, injector)
    }
}