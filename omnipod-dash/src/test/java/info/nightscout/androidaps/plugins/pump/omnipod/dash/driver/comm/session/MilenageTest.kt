package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLoggerTest
import info.nightscout.androidaps.utils.extensions.toHex
import org.junit.Assert
import org.junit.Test
import org.spongycastle.util.encoders.Hex

class MilenageTest {

    @Test fun testMilenage() {
        val aapsLogger = AAPSLoggerTest()
        val m = Milenage(
            aapsLogger,
            Hex.decode("c0772899720972a314f557de66d571dd"),
            byteArrayOf(0, 0, 0, 0, 0, 2),
            Hex.decode("c2cd1248451103bd77a6c7ef88c441ba")
        )
        Assert.assertEquals(m.res.toHex(), "a40bc6d13861447e")
        Assert.assertEquals(m.ck.toHex(), "55799fd26664cbf6e476525e2dee52c6")
        Assert.assertEquals(m.autn.toHex(), "00c55c78e8d3b9b9e935860a7259f6c0")
    }

    @Test fun testMilenage2() {
        val aapsLogger = AAPSLoggerTest()
        val m = Milenage(
            aapsLogger,
            Hex.decode("78411ccad0fd0fb6f381a47fb3335ecb"),
            byteArrayOf(0, 0, 0, 0, 0, 2), // 1 + 1
            Hex.decode("4fc01ac1a94376ae3e052339c07d9e1f")
        )
        Assert.assertEquals(m.res.toHex(), "ec549e00fa668a19")
        Assert.assertEquals(m.ck.toHex(), "ee3dac761fe358a9f476cc5ee81aa3e9")
        Assert.assertEquals(m.autn.toHex(), "a3e7a71430c8b9b95245b33b3bd679c4")
    }

    @Test fun testMilenageIncrementedSQN() {
        val aapsLogger = AAPSLoggerTest()
        val m = Milenage(
            aapsLogger,
            Hex.decode("c0772899720972a314f557de66d571dd"),
            //           byteArrayOf(0,0,0,0,0x01,0x5d), this is in logs. SQN has to be incremented.
            byteArrayOf(0, 0, 0, 0, 0x01, 0x5e),
            Hex.decode("d71cc44820e5419f42c62ae97c035988")
        )
        Assert.assertEquals(m.res.toHex(), "5f807a379a5c5d30")
        Assert.assertEquals(m.ck.toHex(), "8dd4b3ceb849a01766e37f9d86045c39")
        Assert.assertEquals(m.autn.toHex(), "0e0264d056fcb9b9752227365a090955")
    }

}