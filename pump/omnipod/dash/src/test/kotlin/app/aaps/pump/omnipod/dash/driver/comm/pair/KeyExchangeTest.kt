package app.aaps.pump.omnipod.dash.driver.comm.pair

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.pod.util.RandomByteGenerator
import app.aaps.pump.omnipod.dash.driver.pod.util.X25519KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.spongycastle.util.encoders.Hex

class KeyExchangeTest : TestBase() {

    private val keyGenerator = X25519KeyGenerator()
    private val keyGeneratorSpy: X25519KeyGenerator = spy(keyGenerator)

    private var randomByteGenerator = mock<RandomByteGenerator>()

    @Mock lateinit var config: Config

    @Test fun testLTK() {
        val aapsLogger = AAPSLoggerTest()

        doReturn(Hex.decode("27ec94b71a201c5e92698d668806ae5ba00594c307cf5566e60c1fc53a6f6bb6"))
            .whenever(keyGeneratorSpy).generatePrivateKey()

        val pdmNonce = Hex.decode("edfdacb242c7f4e1d2bc4d93ca3c5706")

        whenever(randomByteGenerator.nextBytes(anyInt())).thenReturn(pdmNonce)

        val ke = KeyExchange(
            aapsLogger,
            config,
            keyGeneratorSpy,
            randomByteGenerator
        )
        val podPublicKey = Hex.decode("2fe57da347cd62431528daac5fbb290730fff684afc4cfc2ed90995f58cb3b74")
        val podNonce = Hex.decode("00000000000000000000000000000000")
        ke.updatePodPublicData(podPublicKey + podNonce)
        assertThat("f2b6940243aba536a66e19fb9a39e37f1e76a1cd50ab59b3e05313b4fc93975e").isEqualTo(ke.pdmPublic.toHex())
        assertThat("5fc3b4da865e838ceaf1e9e8bb85d1ac").isEqualTo(ke.pdmConf.toHex())
        ke.validatePodConf(Hex.decode("af4f10db5f96e5d9cd6cfc1f54f4a92f"))
        assertThat("341e16d13f1cbf73b19d1c2964fee02b").isEqualTo(ke.ltk.toHex())
    }
}
