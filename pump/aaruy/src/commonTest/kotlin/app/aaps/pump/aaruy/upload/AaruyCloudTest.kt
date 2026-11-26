package app.aaps.pump.aaruy.upload

import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.shared.tests.AAPSLoggerTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The records and the two calls of the vendor server, as the vendor app (PR 5589) made them. Ktor's mock engine stands
 * in for the server, so nothing goes out and the tests run on every platform.
 */
class AaruyCloudTest {

    private val requests = mutableListOf<Pair<String, Map<String, String>>>()

    private fun cloudReplying(status: HttpStatusCode, body: String): AaruyCloud {
        val engine = MockEngine { request ->
            val form = (request.body as FormDataContent).formData
            requests += request.url.encodedPath to form.names().associateWith { form[it].orEmpty() }
            respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return AaruyCloud(HttpClient(engine), AAPSLoggerTest())
    }

    private fun entry(type: Int, data: Long = 0, immTime: Long = 0, injectTime: Long = 0, delayBolus: Long = 0) = HistoryUnitInfo().also {
        it.id = 7
        it.time = 1_764_150_000_123
        it.type = type
        it.data = data
        it.immTime = immTime
        it.injectTime = injectTime
        it.delayBolus = delayBolus
        it.blueName = "Q12345"
    }

    @Test
    fun eachKindOfEntryIsTheRecordTheVendorAppMade() {
        val basal = entry(type = 0, data = 1500, injectTime = 30).toCloudRecord(47, "AR-B200A")
        assertEquals(AaruyCloudRecord(47, "AR-B200A", "Q12345", 1_764_150_000, 0, "1.5", 1800), basal)
        // A bolus now: how long it took.
        assertEquals(AaruyCloudRecord(47, "AR-B200A", "Q12345", 1_764_150_000, 2, "0.025", 4), entry(type = 2, data = 25, immTime = 4).toCloudRecord(47, "AR-B200A"))
        // An extended bolus: only its extended amount, over its minutes.
        assertEquals(AaruyCloudRecord(47, "AR-B200A", "Q12345", 1_764_150_000, 3, "2.0", 3600), entry(type = 2, data = 1000, injectTime = 60, delayBolus = 2000).toCloudRecord(47, "AR-B200A"))
        // An alarm: the low byte of its code, as a number.
        assertEquals("203" to "14", entry(type = 3, data = 14).toCloudRecord(47, "AR-B200A").let { it.kind.toString() to it.value })
        // A prime: the position of the piston, in hundredths.
        assertEquals("205" to "12.34", entry(type = 5, data = 1234).toCloudRecord(47, "AR-B200A").let { it.kind.toString() to it.value })
        assertEquals(207, entry(type = 7).toCloudRecord(47, "AR-B200A").kind)
        assertEquals(110, entry(type = 110).toCloudRecord(47, "AR-B200A").kind)
    }

    @Test
    fun aNumberIsWrittenTheSameOnEveryPhone() {
        assertEquals("1.0", decimal(1000, 3))
        assertEquals("0.0", decimal(0, 3))
        assertEquals("0.05", decimal(50, 3))
        assertEquals("12.345", decimal(12345, 3))
        assertEquals("-1.5", decimal(-1500, 3))
    }

    @Test
    fun aRecordIsSentAsTheFormTheVendorAppSent() = runTest {
        val cloud = cloudReplying(HttpStatusCode.OK, """{"code":200,"msg":"ok","data":{"id":1,"index":47,"sn":"Q12345"}}""")
        assertTrue(cloud.send(AaruyCloudRecord(47, "AR-B200A", "Q12345", 1_764_150_000, 2, "0.025", 4)))
        assertEquals(
            "/open/aaps/ruypump/notice" to mapOf(
                "index" to "47", "product_code" to "AR-B200A", "sn" to "Q12345", "record_time" to "1764150000",
                "kind" to "2", "value" to "0.025", "seconds" to "4"
            ),
            requests.single()
        )
    }

    @Test
    fun aRecordIsNotTakenWithIndexZeroAnErrorOrAnAnswerThatIsNoJson() = runTest {
        val record = AaruyCloudRecord(47, "AR-B200A", "Q12345", 1_764_150_000, 2, "0.025", 4)
        assertFalse(cloudReplying(HttpStatusCode.OK, """{"code":200,"msg":"ok","data":{"id":1,"index":0,"sn":"Q12345"}}""").send(record))
        assertFalse(cloudReplying(HttpStatusCode.InternalServerError, "").send(record))
        assertFalse(cloudReplying(HttpStatusCode.OK, "<html>maintenance</html>").send(record))
    }

    @Test
    fun theLastIndexOfAPump() = runTest {
        assertEquals(40, cloudReplying(HttpStatusCode.OK, """{"code":200,"msg":"ok","data":{"id":9,"index":40,"kind":0,"record_time":1,"value":"1.0"}}""").lastIndex("Q12345"))
        assertEquals("/open/aaps/ruypump/record/last" to mapOf("sn" to "Q12345"), requests.single())
        // A pump the server has no record of.
        assertEquals(0, cloudReplying(HttpStatusCode.OK, """{"code":200,"msg":"ok","data":null}""").lastIndex("Q12345"))
        // Not known: nothing may be sent.
        assertNull(cloudReplying(HttpStatusCode.BadGateway, "").lastIndex("Q12345"))
    }
}
