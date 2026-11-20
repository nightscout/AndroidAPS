package app.aaps.plugins.source.eversense

import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class EversenseCloudUploaderTest {
    private val prefs: EversenseStore = mock()
    private val values = mutableMapOf<String, Any>()
    private var online = true
    private var enabled = true
    private var wall = 1_800_000_000_000L
    private var monotonic = 0L
    private var success = true
    private val uploads = mutableListOf<EversenseCloudUploader.PendingUpload>()
    private val errors = mutableListOf<Exception>()
    private val state = EversenseState().apply { transmitterSerialNumber = "T123" }
    init {
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] as? String ?: it.getArgument<String?>(1) }
        whenever(prefs.getLong(any(), any())).thenAnswer { values[it.getArgument<String>(0)] as? Long ?: it.getArgument<Long>(1) }
        whenever(prefs.contains(any())).thenAnswer { values.containsKey(it.getArgument<String>(0)) }
        whenever(prefs.edit()).thenAnswer {
            object : EversenseStore.Editor {
                val changes = mutableMapOf<String, Any?>()
                override fun putString(key: String, value: String?) = apply { changes[key] = value }
                override fun putLong(key: String, value: Long) = apply { changes[key] = value }
                override fun putInt(key: String, value: Int) = apply { changes[key] = value }
                override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
                override fun remove(key: String) = apply { changes[key] = null }
                override fun clear() = apply { values.clear() }
                override fun commit(): Boolean { changes.forEach { (k,v) -> if(v == null) values.remove(k) else values[k] = v }; return true }
                override fun apply() { commit() }
            }
        }
    }
    private fun TestScope.uploader(upload: suspend (EversenseStore, EversenseCloudUploader.PendingUpload) -> Boolean = { _, batch -> uploads.add(batch); success }) =
        EversenseCloudUploader({ backgroundScope }, { prefs }, { enabled }, { online }, { wall }, { monotonic }, upload, errors::add)
    private fun reading(minute: Int) = EversenseCGMResult(100 + minute, wall + minute * 60_000L, EversenseTrendArrow.FLAT, "sensor", "0102")
    private fun pending() = Json.decodeFromString<List<EversenseCloudUploader.PendingUpload>>(values[StorageKeys.CLOUD_QUEUE] as? String ?: "[]")
    private fun hour() { wall += EversenseCloudUploader.HOUR_MS; monotonic += EversenseCloudUploader.HOUR_MS }

    @Test fun offlineReadingsAreRetainedAndUploadedTogetherAfterInternetReturns() = runTest {
        online = false
        val uploader = uploader()
        for (i in 0..11) { uploader.enqueue(EversenseType.EVERSENSE_365, listOf(reading(i)), state); runCurrent() }
        assertTrue(uploads.isEmpty())
        assertEquals(12, pending().single().readings.size)
        assertFalse(values.containsKey(StorageKeys.CLOUD_LAST_ATTEMPT))
        online = true
        uploader.flush()
        runCurrent()
        assertEquals(12, uploads.single().readings.size)
        assertTrue(pending().isEmpty())
    }
    @Test fun hourlyBatchContainsAllIntermediateReadingsAndUsesLatestReadingForPortal() = runTest {
        val uploader = uploader()
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(0)), state)
        runCurrent()
        for (i in 1..11) { uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(i)), state); runCurrent() }
        assertEquals(1, uploads.size)
        hour()
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(0)), state)
        runCurrent()
        assertEquals(2, uploads.size)
        assertEquals(12, uploads.last().readings.size)
    }
    @Test fun failedUploadIsRetainedAndDoesNotRetryWithinTheHourEvenAfterRestart() = runTest {
        success = false
        var uploader = uploader()
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(0)), state)
        runCurrent()
        assertEquals(1, uploads.size)
        uploader = uploader()
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(5)), state)
        runCurrent()
        assertEquals(1, uploads.size)
        assertEquals(2, pending().single().readings.size)
        hour()
        success = true
        uploader.flush()
        runCurrent()
        assertEquals(2, uploads.size)
        assertTrue(pending().isEmpty())
    }
    @Test fun slowNetworkDoesNotBlockQueuingNewReadingsOrLoseThemOnAcknowledgment() = runTest {
        val release = CompletableDeferred<Unit>()
        val uploader = uploader { _, batch -> uploads.add(batch); release.await(); true }
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(0)), state)
        runCurrent()
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(5)), state)
        runCurrent()
        assertEquals(1, uploads.size)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(reading(5)), pending().single().readings)
    }
    @Test fun uploadExceptionKeepsPendingDataAndReleasesWorkerForNextHour() = runTest {
        val uploader = uploader { _, _ -> throw IllegalStateException("network unavailable") }
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(0)), state)
        runCurrent()
        assertEquals(1, errors.size)
        assertEquals(1, pending().single().readings.size)
        hour()
        uploader.flush()
        runCurrent()
        assertEquals(2, errors.size)
    }
    @Test fun duplicateReadingsAreDeduplicatedAndDisabledUploadDoesNotQueue() = runTest {
        online = false
        val uploader = uploader()
        uploader.enqueue(EversenseType.EVERSENSE_365, listOf(reading(0), reading(0)), state)
        runCurrent()
        assertEquals(1, pending().single().readings.size)
        enabled = false
        uploader.enqueue(EversenseType.EVERSENSE_365, listOf(reading(5)), state)
        runCurrent()
        assertEquals(1, pending().single().readings.size)
        assertTrue(uploads.isEmpty())
    }
    @Test fun historyWithoutRaw365PayloadDoesNotBlockUploadQueue() = runTest {
        val uploader = uploader()
        uploader.enqueue(EversenseType.EVERSENSE_365, listOf(reading(0).copy(rawResponseHex = "")), state)
        runCurrent()
        assertTrue(uploads.isEmpty())
        assertTrue(pending().isEmpty())
    }
    @Test fun wallClockJumpCannotCauseAnotherUploadInSameElapsedHour() = runTest {
        val uploader = uploader()
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(0)), state)
        runCurrent()
        wall += 24 * EversenseCloudUploader.HOUR_MS
        uploader.enqueue(EversenseType.EVERSENSE_E3, listOf(reading(0)), state)
        runCurrent()
        assertEquals(1, uploads.size)
    }
    @Test fun queueHasSevenDayAnd2016ReadingLimit() = runTest {
        online = false
        val uploader = uploader()
        val samples = (0..2100).map { reading(it).copy(datetime = wall - it * 5 * 60_000L) }
        uploader.enqueue(EversenseType.EVERSENSE_E3, samples, state)
        runCurrent()
        assertEquals(2016, pending().single().readings.size)
        wall += 8 * 24 * EversenseCloudUploader.HOUR_MS
        uploader.enqueue(EversenseType.EVERSENSE_E3, emptyList(), state)
        runCurrent()
        assertTrue(pending().isEmpty())
    }
}
