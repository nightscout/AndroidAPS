package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock

open class DanaRTestBase : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var injector: HasAndroidInjector

    lateinit var danaRPump: DanaRPump

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
        danaRPump = DanaRPump(aapsLogger, sp, injector)
    }
}