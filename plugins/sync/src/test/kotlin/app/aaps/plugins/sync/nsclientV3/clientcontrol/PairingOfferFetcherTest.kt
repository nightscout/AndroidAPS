package app.aaps.plugins.sync.nsclientV3.clientcontrol

import android.util.Base64
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingOffer
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.utils.ClientControlPairingCrypto
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.IOException
import javax.inject.Provider

internal class PairingOfferFetcherTest {

    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var nsAndroidClient: NSAndroidClient
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger

    private val now = 1_700_000_000_000L

    // Real PBKDF2/AES is used end-to-end; only android.util.Base64 needs faking because the
    // production code base64-decodes the offer fields. Delegate to java.util.Base64 (standard
    // alphabet with padding == android NO_WRAP) so the wrapped/iv/salt round-trip cleanly.
    private lateinit var base64Mock: MockedStatic<Base64>

    private lateinit var sut: PairingOfferFetcher

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        base64Mock = Mockito.mockStatic(Base64::class.java)
        base64Mock.`when`<ByteArray> { Base64.decode(any<String>(), any<Int>()) }.thenAnswer {
            java.util.Base64.getDecoder().decode(it.getArgument<String>(0))
        }
        sut = PairingOfferFetcher(Provider { nsClientV3Plugin }, dateUtil, aapsLogger)
        // Pin the unwrap loop to the test thread so the thread-local Base64 static mock applies.
        sut.unwrapDispatcher = Dispatchers.Unconfined
    }

    @AfterEach
    fun tearDown() {
        base64Mock.close()
    }

    private fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun payload(clientId: String = "client-uuid", masterInstallId: String = "master-uuid") =
        PairingPayload(masterInstallId = masterInstallId, clientId = clientId, secretHex = "00".repeat(32), expiresAt = now + 60_000L)

    /** Builds a real PIN-wrapped offer document the production fetcher can unwrap. */
    private fun offerDoc(
        pin: String,
        payload: PairingPayload = payload(),
        identifier: String = "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}${payload.clientId}",
        expiresAt: Long = now + 120_000L
    ): JSONObject {
        val salt = ClientControlPairingCrypto.newSalt()
        val iv = ClientControlPairingCrypto.newIv()
        val plaintext = Json.encodeToString(PairingPayload.serializer(), payload).toByteArray()
        val wrapped = ClientControlPairingCrypto.wrap(plaintext, pin, salt, iv)
        val offer = PairingOffer(
            clientId = payload.clientId,
            expiresAt = expiresAt,
            kdfSaltB64 = b64(salt),
            ivB64 = b64(iv),
            wrappedB64 = b64(wrapped)
        )
        return JSONObject().apply {
            put("identifier", identifier)
            put("offer", JSONObject(Json.encodeToString(PairingOffer.serializer(), offer)))
        }
    }

    private suspend fun respond(vararg docs: JSONObject) {
        whenever(nsAndroidClient.searchSettings(limit = 500)).thenReturn(
            NSAndroidClient.ReadResponse(code = 200, lastServerModified = null, values = docs.toList())
        )
    }

    @Test
    fun notAvailableWhenClientUninitialized() = runTest {
        whenever(nsClientV3Plugin.nsAndroidClient).thenReturn(null)
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.NotAvailable)
    }

    @Test
    fun notAvailableWhenSearchFails() = runTest {
        whenever(nsAndroidClient.searchSettings(limit = 500)).thenAnswer { throw IOException("offline") }
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.NotAvailable)
    }

    @Test
    fun noMatchWhenNoDocs() = runTest {
        respond()
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.NoMatch)
    }

    @Test
    fun successWhenSingleOfferUnwraps() = runTest {
        val expected = payload(clientId = "client-A", masterInstallId = "master-A")
        respond(offerDoc(pin = "12345678", payload = expected))
        val result = sut.findOfferForPin("12345678")
        assertThat(result).isInstanceOf(PairingOfferFetcher.Result.Success::class.java)
        assertThat((result as PairingOfferFetcher.Result.Success).payload).isEqualTo(expected)
    }

    @Test
    fun noMatchWhenPinIsWrong() = runTest {
        respond(offerDoc(pin = "11111111"))
        assertThat(sut.findOfferForPin("99999999")).isEqualTo(PairingOfferFetcher.Result.NoMatch)
    }

    @Test
    fun ambiguousWhenTwoOffersUnwrapWithSamePin() = runTest {
        respond(
            offerDoc(pin = "12345678", payload = payload(clientId = "client-A")),
            offerDoc(pin = "12345678", payload = payload(clientId = "client-B"))
        )
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.Ambiguous)
    }

    @Test
    fun ignoresDocsWithoutOfferPrefix() = runTest {
        val foreign = offerDoc(pin = "12345678", identifier = "aaps")
        respond(foreign)
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.NoMatch)
    }

    @Test
    fun foreignDocsDoNotBlockAValidOffer() = runTest {
        val unrelated = JSONObject().apply { put("identifier", "aaps"); put("date", now) }
        val valid = payload(clientId = "client-A")
        respond(unrelated, offerDoc(pin = "12345678", payload = valid))
        val result = sut.findOfferForPin("12345678")
        assertThat(result).isInstanceOf(PairingOfferFetcher.Result.Success::class.java)
        assertThat((result as PairingOfferFetcher.Result.Success).payload).isEqualTo(valid)
    }

    @Test
    fun skipsMalformedOffer() = runTest {
        val malformed = JSONObject().apply {
            put("identifier", "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}broken")
            put("offer", JSONObject().apply { put("clientId", "broken") }) // missing required wrapped/iv/salt
        }
        respond(malformed)
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.NoMatch)
    }

    @Test
    fun skipsDocWithoutOfferObject() = runTest {
        val noOffer = JSONObject().apply { put("identifier", "${ClientControlPublisher.IDENTIFIER_OFFER_PREFIX}x") }
        respond(noOffer)
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.NoMatch)
    }

    @Test
    fun skipsExpiredOfferBeforeUnwrapping() = runTest {
        // Correct PIN, but the offer expired one second ago → must not be considered.
        respond(offerDoc(pin = "12345678", expiresAt = now - 1_000L))
        assertThat(sut.findOfferForPin("12345678")).isEqualTo(PairingOfferFetcher.Result.NoMatch)
    }

    @Test
    fun nonExpiringOfferWithZeroExpiryIsConsidered() = runTest {
        // expiresAt == 0 means "no server-side expiry"; it must still be unwrapped.
        val expected = payload(clientId = "client-A")
        respond(offerDoc(pin = "12345678", payload = expected, expiresAt = 0L))
        val result = sut.findOfferForPin("12345678")
        assertThat(result).isInstanceOf(PairingOfferFetcher.Result.Success::class.java)
        assertThat((result as PairingOfferFetcher.Result.Success).payload).isEqualTo(expected)
    }
}
