package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.mockito.Mock

open class DanaRSTestBase : TestBase() {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var sp: SP

    lateinit var danaRPump: DanaRPump

    fun createArray(length: Int, fillWith: Byte): ByteArray {
        val ret = ByteArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    @Before
    fun setup() {
        danaRPump = DanaRPump(aapsLogger, sp)
    }
}