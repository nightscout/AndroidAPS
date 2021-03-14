package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import info.nightscout.androidaps.logging.AAPSLoggerTest
import info.nightscout.androidaps.utils.extensions.toHex
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Test
import org.spongycastle.util.encoders.Hex

class KeyExchangeTest {
    @Test fun testLTK() {
        val aapsLogger = AAPSLoggerTest()
        val ke = KeyExchange(
            aapsLogger,
            pdmPrivate= Hex.decode("27ec94b71a201c5e92698d668806ae5ba00594c307cf5566e60c1fc53a6f6bb6"),
            pdmNonce= Hex.decode("edfdacb242c7f4e1d2bc4d93ca3c5706")
        )
        val podPublicKey = Hex.decode("2fe57da347cd62431528daac5fbb290730fff684afc4cfc2ed90995f58cb3b74")
        val podNonce = Hex.decode("00000000000000000000000000000000")
        ke.updatePodPublicData(podPublicKey+podNonce)
        assertEquals(ke.pdmPublic.toHex(), "f2b6940243aba536a66e19fb9a39e37f1e76a1cd50ab59b3e05313b4fc93975e")
        assertEquals(ke.pdmConf.toHex(), "5fc3b4da865e838ceaf1e9e8bb85d1ac")
        ke.validatePodConf(Hex.decode("af4f10db5f96e5d9cd6cfc1f54f4a92f"))
        assertEquals(ke.ltk.toHex(), "341e16d13f1cbf73b19d1c2964fee02b")
    }
}