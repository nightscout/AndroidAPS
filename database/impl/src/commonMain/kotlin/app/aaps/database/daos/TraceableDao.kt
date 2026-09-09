package app.aaps.database.daos

import androidx.room.Insert
import androidx.room.Update
import app.aaps.database.daos.workaround.TraceableDaoWorkaround
import app.aaps.database.entities.interfaces.TraceableDBEntry
import kotlin.time.Clock

internal interface TraceableDao<T : TraceableDBEntry> : TraceableDaoWorkaround<T> {

    suspend fun findById(id: Long): T?

    suspend fun deleteAllEntries()

    suspend fun deleteOlderThan(than: Long): Int
    suspend fun deleteTrackedChanges(): Int

    @Insert
    suspend fun insert(entry: T): Long

    @Update
    suspend fun update(entry: T)
}

/**
 * Inserts a new entry
 * @return The ID of the newly generated entry
 */
//@Transaction
internal suspend fun <T : TraceableDBEntry> TraceableDao<T>.insertNewEntryImpl(entry: T): Long {
    if (entry.id != 0L) throw IllegalArgumentException("ID must be 0.")
    if (entry.version != 0) throw IllegalArgumentException("Version must be 0.")
    if (entry.referenceId != null) throw IllegalArgumentException("Reference ID must be null.")
    if (!entry.foreignKeysValid) throw IllegalArgumentException("One or more foreign keys are invalid (e.g. 0 value).")
    val lastModified = Clock.System.now().toEpochMilliseconds()
    entry.dateCreated = lastModified
    val id = insert(entry)
    entry.id = id
    return id
}

/**
 * Updates an existing entry
 * @return The ID of the newly generated HISTORIC entry
 */
//@Transaction
internal suspend fun <T : TraceableDBEntry> TraceableDao<T>.updateExistingEntryImpl(entry: T): Long {
    if (entry.id == 0L) throw IllegalArgumentException("ID must not be 0.")
    if (entry.referenceId != null) throw IllegalArgumentException("Reference ID must be null.")
    if (!entry.foreignKeysValid) throw IllegalArgumentException("One or more foreign keys are invalid (e.g. 0 value).")
    val lastModified = Clock.System.now().toEpochMilliseconds()
    entry.dateCreated = lastModified
    val current = findById(entry.id)
        ?: throw IllegalArgumentException("The entry with the specified ID does not exist.")
    if (current.referenceId != null) throw IllegalArgumentException("The entry with the specified ID is historic and cannot be updated.")
    entry.version = current.version + 1
    update(entry)
    current.referenceId = entry.id
    current.id = 0
    return insert(current)
}