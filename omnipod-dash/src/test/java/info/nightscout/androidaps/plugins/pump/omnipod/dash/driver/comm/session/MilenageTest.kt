package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLoggerTest
import info.nightscout.androidaps.utils.extensions.toHex
import okio.ByteString.Companion.decodeHex
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Test
import org.spongycastle.util.encoders.Hex

class MilenageTest {

    @Test fun testMilenage() {
        val aapsLogger = AAPSLoggerTest()
        val m = Milenage(
            aapsLogger,
            Hex.decode("c0772899720972a314f557de66d571dd"),
            byteArrayOf(0,0,0,0,0,1),
            Hex.decode("c2cd1248451103bd77a6c7ef88c441ba")
        )
        Assert.assertEquals(m.ck.toHex(), "55799fd26664cbf6e476525e2dee52c6")
        Assert.assertEquals(m.res.toHex(), "a40bc6d13861447e")
        Assert.assertEquals(m.autn.toHex(), "00c55c78e8d3b9b9e935860a7259f6c0")
    }
}