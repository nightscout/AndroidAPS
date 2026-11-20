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
    data class PendingUpload(
        val type: EversenseType,
        val state: EversenseState,
        val readings: List<EversenseCGMResult>
    )

    @Serializable
    private data class SentHistory(
        val state: EversenseState,
        val lastSuccess: Long,
        val readings: List<EversenseCGMResult>
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val busy = AtomicBoolean(false)
    private var lastAttemptElapsed: Long? = null

    suspend fun enqueue(type: EversenseType, readings: List<EversenseCGMResult>, state: EversenseState) {
        // For E365 the switch pauses delivery. Preserve its numbered records locally for resume.
        if (type != EversenseType.EVERSENSE_365 && !enabled()) return
        mutex.withLock {
            val prefs = store() ?: return
            val batches = read(prefs).toMutableList()
            val index = batches.indexOfFirst { it.type == type && it.state.transmitterSerialNumber == state.transmitterSerialNumber }
            val old = batches.getOrNull(index)?.readings.orEmpty()
            val merged = (old + readings).groupBy { it.datetime to it.sensorId }
                .values.map { duplicates ->
                    val reading = duplicates.maxBy { it.rawResponseHex.length }
                    val recordNumber = reading.recordNumber ?: duplicates.firstNotNullOfOrNull {
                        if (it.glucoseInMgDl == reading.glucoseInMgDl) it.recordNumber else null
                    }
                    if (recordNumber == reading.recordNumber) reading else reading.copy(recordNumber = recordNumber)
                }
            if (index >= 0) batches.removeAt(index)
            if (merged.isNotEmpty()) batches.add(PendingUpload(type, state, merged))
            save(prefs, bounded(batches))
        }
        if (enabled()) flush()
    }

    fun flush(force: Boolean = false) {
        if (!busy.compareAndSet(false, true)) return
        val job = scope().launch {
            try {
                val reservation = mutex.withLock {
                    if (!enabled() || !online()) return@launch
                    val prefs = store() ?: return@launch
                    val wall = now()
                    val last = prefs.getLong(StorageKeys.CLOUD_LAST_ATTEMPT, 0)
                    val previousElapsed = lastAttemptElapsed
                    if (!force) {
                        if (previousElapsed != null && elapsed() - previousElapsed < HOUR_MS) return@launch
                        if (last > wall) {
                            prefs.edit(commit = true) { putLong(StorageKeys.CLOUD_LAST_ATTEMPT, wall) }
                            return@launch
                        }
                        if (prefs.contains(StorageKeys.CLOUD_LAST_ATTEMPT) && wall - last < HOUR_MS) return@launch
                    }
                    val pending = bounded(read(prefs))
                    val sent = if (pending.any { it.type == EversenseType.EVERSENSE_365 }) readSentHistory(prefs) else emptyMap()
                    val batches = pending.mapNotNull { batch ->
                        val acknowledged = if (batch.type == EversenseType.EVERSENSE_365)
                            sent[batch.state.transmitterSerialNumber]?.readings else null
                        if (acknowledged.isNullOrEmpty()) batch else {
                            val keys = acknowledged.mapTo(mutableSetOf()) { it.datetime to it.sensorId }
                            val remaining = batch.readings.filterNot { (it.datetime to it.sensorId) in keys }
                            if (remaining.isEmpty()) null else batch.copy(readings = remaining)
                        }
                    }
                    if (batches != pending) save(prefs, batches)
                    if (batches.isEmpty()) return@launch
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
                        val history = if (batch.type == EversenseType.EVERSENSE_365 && batch.state.transmitterSerialNumber.isNotEmpty()) {
                            updatedSentHistory(prefs, batch, now())
                        } else null
                        prefs.edit(commit = true) {
                            putString(StorageKeys.CLOUD_QUEUE, json.encodeToString(remaining))
                            if (history != null) putString(StorageKeys.CLOUD_REPLAY_HISTORY, json.encodeToString(history))
                        }
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

    private fun readSentHistory(prefs: EversenseStore): Map<String, SentHistory> =
        json.decodeFromString(prefs.getString(StorageKeys.CLOUD_REPLAY_HISTORY, null) ?: "{}")

    private fun updatedSentHistory(prefs: EversenseStore, batch: PendingUpload, wall: Long): Map<String, SentHistory> {
        val history = readSentHistory(prefs).toMutableMap()
        val transmitter = batch.state.transmitterSerialNumber
        val old = history[transmitter]?.readings.orEmpty()
        val sent = batch.readings.filter { it.recordNumber != null && it.sensorId.isNotEmpty() }
            .map { it.copy(rawResponseHex = "") } // The receipt does not need the raw payload.
        val readings = (old + sent).associateBy { it.datetime to it.sensorId }.values
        history[transmitter] = SentHistory(batch.state, wall, readings.toList())

        var budget = MAX_SENT_READINGS
        return history.entries.sortedByDescending { it.value.lastSuccess }.mapNotNull { (key, entry) ->
            val kept = entry.readings.filter { it.datetime >= wall - SENT_RETENTION_HOURS * HOUR_MS }
                .sortedByDescending { it.datetime }.take(budget)
            budget -= kept.size
            if (kept.isEmpty()) null else key to entry.copy(readings = kept.sortedBy { it.datetime })
        }.toMap()
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
        private const val SENT_RETENTION_HOURS = 30 * 24L
        private const val MAX_SENT_READINGS = 30 * 24 * 12
    }
}
