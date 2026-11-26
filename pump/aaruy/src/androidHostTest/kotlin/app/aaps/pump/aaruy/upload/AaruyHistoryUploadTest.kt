package app.aaps.pump.aaruy.upload

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.keys.AaruyBooleanPreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * The upload of the pump log to the vendor server. Off by default, so nothing may go out before the user turns it on.
 * Ktor's mock engine stands in for the server.
 */
class AaruyHistoryUploadTest {

    /** The path of each request and its form. */
    private val requests = ConcurrentLinkedQueue<Pair<String, Map<String, String>>>()

    /** What the server answers to `record/last`, per serial number. */
    private val lastIndexOnServer = mutableMapOf("Q12345" to 40, "Q67890" to 0)

    /** The indexes the server does not take. */
    private val refused = mutableSetOf<Int>()
    private var connected = true

    private val engine = MockEngine { request ->
        if (!connected) throw IOException("no connection")
        val form = (request.body as FormDataContent).formData
        val fields = form.names().associateWith { form[it].orEmpty() }
        requests += request.url.encodedPath to fields
        val body = if (request.url.encodedPath.endsWith("record/last"))
            """{"code":200,"msg":"ok","data":{"id":1,"index":${lastIndexOnServer.getValue(fields.getValue("sn"))}}}"""
        else {
            val index = fields.getValue("index").toInt()
            """{"code":200,"msg":"ok","data":{"id":1,"index":${if (index in refused) 0 else index},"sn":"${fields["sn"]}"}}"""
        }
        respond(ByteReadChannel(body), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private val stored = mutableMapOf<String, Any>()
    private val setting = MutableStateFlow(false)
    private val preferences: Preferences = mock {
        on { observe(AaruyBooleanPreferenceKey.UploadHistory) } doReturn setting
        on { get(AaruyIntKey.PumpType) } doReturn 3
        on { get(AaruyStringKey.UploadSerial) } doAnswer { stored[AaruyStringKey.UploadSerial.key] as? String ?: "" }
        on { get(AaruyIntKey.UploadIndexOffset) } doAnswer { stored[AaruyIntKey.UploadIndexOffset.key] as? Int ?: 0 }
        on { put(eq(AaruyStringKey.UploadSerial), any<String>()) } doAnswer { stored[AaruyStringKey.UploadSerial.key] = it.getArgument<String>(1) }
        on { put(eq(AaruyIntKey.UploadIndexOffset), any<Int>()) } doAnswer { stored[AaruyIntKey.UploadIndexOffset.key] = it.getArgument<Int>(1) }
    }
    private val dao = FakeHistoryDao()
    private val upload = AaruyHistoryUpload(mock<AAPSLogger>(), preferences, dao).also { it.cloud = AaruyCloud(HttpClient(engine), mock<AAPSLogger>()) }

    private fun entry(id: Int, serial: String = "Q12345") = HistoryUnitInfo().also {
        it.id = id
        it.time = 1_764_150_000_000 + id * 60_000L
        it.type = 2
        it.data = 1000
        it.immTime = 40
        it.blueName = serial
    }

    private fun noticeIndexes() = requests.filter { it.first.endsWith("notice") }.map { it.second.getValue("index").toInt() }

    @Test
    fun nothingGoesOutUntilTheUserTurnsTheUploadOn(): Unit = runBlocking {
        dao.entries += entry(1)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            upload.start(scope)
            delay(300)
            assertEquals(0, dao.notUploadedCalls.get())
            assertTrue(requests.isEmpty())

            setting.value = true
            withTimeout(5_000) { while (noticeIndexes().isEmpty()) delay(10) }
            assertEquals(listOf(41), noticeIndexes())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theFirstRoundAsksForTheLastIndexAndSendsTheEntriesInOrder() = runTest {
        dao.entries += listOf(entry(2), entry(1), entry(3))

        assertEquals(3, upload.uploadPending())

        val first = requests.first()
        assertEquals("/open/aaps/ruypump/record/last" to mapOf("sn" to "Q12345"), first)
        // The id of the entry plus the last index the server had, as the vendor app numbered them.
        assertEquals(listOf(41, 42, 43), noticeIndexes())
        assertEquals("AR-B200A", requests.last().second["product_code"])
        assertTrue(dao.entries.all { it.isUploaded })
        assertEquals("Q12345", stored[AaruyStringKey.UploadSerial.key])
        assertEquals(40, stored[AaruyIntKey.UploadIndexOffset.key])
    }

    @Test
    fun anEntryTheServerDoesNotTakeEndsTheRoundAndComesFirstInTheNext() = runTest {
        dao.entries += listOf(entry(1), entry(2), entry(3))
        refused += 42

        assertEquals(1, upload.uploadPending())
        assertEquals(listOf(false, false), dao.entries.filter { it.id >= 2 }.map { it.isUploaded })

        refused.clear()
        requests.clear()
        assertEquals(2, upload.uploadPending())
        // The last index is known now: the server is not asked again, and the numbers stay the same.
        assertEquals(listOf(42, 43), noticeIndexes())
        assertEquals(2, requests.size)
    }

    @Test
    fun withNoConnectionNothingIsMarkedOrKept() = runTest {
        dao.entries += entry(1)
        connected = false

        assertEquals(0, upload.uploadPending())

        assertTrue(dao.entries.none { it.isUploaded })
        assertTrue(stored.isEmpty())
    }

    @Test
    fun anotherPumpGetsItsOwnNumbers() = runTest {
        dao.entries += listOf(entry(1), entry(2, serial = "Q67890"))

        assertEquals(2, upload.uploadPending())

        assertEquals(listOf("record/last", "notice", "record/last", "notice"), requests.map { it.first.substringAfter("ruypump/") })
        assertEquals(listOf(41, 2), noticeIndexes())
        assertEquals("Q67890", stored[AaruyStringKey.UploadSerial.key])
    }

    private class FakeHistoryDao : HistoryUnitInfoDao {

        val entries: MutableList<HistoryUnitInfo> = mutableListOf()
        val notUploadedCalls = AtomicInteger()

        override suspend fun insert(historyUnitInfo: HistoryUnitInfo) {
            entries += historyUnitInfo
        }

        override suspend fun count(time: Long, type: Int, reserved: Int): Int = 0
        override suspend fun querySince(since: Long): List<HistoryUnitInfo> = emptyList()
        override suspend fun maxId(): Int = entries.maxOfOrNull { it.id } ?: 0
        override suspend fun newest(type: Int): HistoryUnitInfo? = null

        override suspend fun notUploaded(limit: Int): List<HistoryUnitInfo> {
            notUploadedCalls.incrementAndGet()
            return entries.filter { !it.isUploaded }.sortedBy { it.id }.take(limit)
        }

        override suspend fun markUploaded(id: Int) {
            entries.first { it.id == id }.isUploaded = true
        }

        override suspend fun deleteAll() {
            entries.clear()
        }
    }
}
