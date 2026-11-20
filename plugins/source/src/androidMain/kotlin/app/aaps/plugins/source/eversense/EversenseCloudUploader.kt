package app.aaps.plugins.source.eversense

import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.util.StorageKeys
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Optional cloud delivery. Local glucose persistence never waits for network IO. */
internal class EversenseCloudUploader(
    private val scope: () -> CoroutineScope,
    private val store: () -> EversenseStore?,
    private val enabled: () -> Boolean,
    private val online: () -> Boolean,
    private val now: () -> Long,
    private val elapsed: () -> Long,
    private val upload: suspend (EversenseStore, PendingUpload) -> Boolean,
    private val onError: (Exception) -> Unit
) {
    @Serializable
    data class PendingUpload(val type: EversenseType, val state: EversenseState, val readings: List<EversenseCGMResult>)

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val busy = AtomicBoolean(false)
    private var lastAttemptElapsed: Long? = null

    suspend fun enqueue(type: EversenseType, readings: List<EversenseCGMResult>, state: EversenseState) {
        if (!enabled()) return
        mutex.withLock {
            val prefs = store() ?: return
            val eligible = readings.filter { type != EversenseType.EVERSENSE_365 || it.rawResponseHex.isNotEmpty() }
            val batches = read(prefs).toMutableList()
            val index = batches.indexOfFirst { it.type == type && it.state.transmitterSerialNumber == state.transmitterSerialNumber }
            val old = batches.getOrNull(index)?.readings.orEmpty()
            val merged = (old + eligible).groupBy { it.datetime to it.sensorId }
                .values.map { duplicates -> duplicates.maxBy { it.rawResponseHex.length } }
            if (index >= 0) batches.removeAt(index)
            if (merged.isNotEmpty()) batches.add(PendingUpload(type, state, merged))
            save(prefs, bounded(batches))
        }
        flush()
    }

    fun flush() {
        if (!busy.compareAndSet(false, true)) return
        val job = scope().launch {
            try {
                val reservation = mutex.withLock {
                    if (!enabled() || !online()) return@launch
                    val prefs = store() ?: return@launch
                    val batches = bounded(read(prefs))
                    if (batches.isEmpty()) return@launch
                    val wall = now()
                    val last = prefs.getLong(StorageKeys.CLOUD_LAST_ATTEMPT, 0)
                    val previousElapsed = lastAttemptElapsed
                    if (previousElapsed != null && elapsed() - previousElapsed < HOUR_MS) return@launch
                    if (last > wall) {
                        prefs.edit(commit = true) { putLong(StorageKeys.CLOUD_LAST_ATTEMPT, wall) }
                        return@launch
                    }
                    if (prefs.contains(StorageKeys.CLOUD_LAST_ATTEMPT) && wall - last < HOUR_MS) return@launch
                    // Reserve before IO, including failed attempts, and retain across process restarts.
                    prefs.edit(commit = true) { putLong(StorageKeys.CLOUD_LAST_ATTEMPT, wall) }
                    lastAttemptElapsed = elapsed()
                    prefs to batches
                }
                val (prefs, batches) = reservation
                for (batch in batches) {
                    if (!enabled() || !online()) break
                    if (!upload(prefs, batch)) break
                    mutex.withLock {
                        val remaining = read(prefs).map { current ->
                            if (current.type == batch.type && current.state.transmitterSerialNumber == batch.state.transmitterSerialNumber)
                                current.copy(readings = current.readings.filterNot { it in batch.readings })
                            else current
                        }.filter { it.readings.isNotEmpty() }
                        save(prefs, remaining)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
        job.invokeOnCompletion { busy.set(false) }
    }

    private fun read(prefs: EversenseStore): List<PendingUpload> =
        json.decodeFromString(prefs.getString(StorageKeys.CLOUD_QUEUE, null) ?: "[]")

    private fun save(prefs: EversenseStore, batches: List<PendingUpload>) {
        prefs.edit(commit = true) { putString(StorageKeys.CLOUD_QUEUE, json.encodeToString(batches)) }
    }

    private fun bounded(batches: List<PendingUpload>): List<PendingUpload> {
        val cutoff = now() - RETENTION_MS
        var budget = MAX_READINGS
        return batches.asReversed().mapNotNull { batch ->
            val kept = batch.readings.filter { it.datetime >= cutoff }.sortedByDescending { it.datetime }.take(budget)
            budget -= kept.size
            if (kept.isEmpty()) null else batch.copy(readings = kept.sortedBy { it.datetime })
        }.asReversed()
    }

    companion object {
        const val HOUR_MS = 60 * 60_000L
        private const val RETENTION_MS = 7 * 24 * HOUR_MS
        private const val MAX_READINGS = 2016
    }
}
