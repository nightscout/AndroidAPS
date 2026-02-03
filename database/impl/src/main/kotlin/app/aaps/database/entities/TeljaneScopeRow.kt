package app.aaps.database.entities

/**
 * Teljane-only Room projection returned by GlucoseValueDao queries.
 *
 * IMPORTANT DESIGN NOTE (module boundary):
 * - This type is DB-layer only (lives in :database module).
 * - It MUST NOT be referenced by plugins/workers/core modules, because they do not depend on :database.
 * - It exists solely to carry query results from Room to AppRepository.
 *
 * Empty/no-row case is represented by sentinel values (e.g. -1) because the DAO query uses COALESCE.
 * This keeps Room mapping simple and avoids nullable plumbing.
 */
data class TeljaneScopeRow(
    val minSgvId: Long,          // -1 if no match
    val maxSgvId: Long,          // -1 if no match
    val sgvMark: Int,            // -1 if missing for device (abnormal)
    val latestTimestamp: Long    // -1 if no match
)