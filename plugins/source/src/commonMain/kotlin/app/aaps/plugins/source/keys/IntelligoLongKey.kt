package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class IntelligoLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    /**
     * How far Intelligo has read. It stored this under **Glunovo's** key until 2026-09-22 - a copy and
     * paste, and one that no test could see, because each plugin's tests mock `Preferences` and stub
     * their own enum, so the two only met in the real store.
     *
     * It mattered because the value is a skip filter: `if (timestamp < get(LastProcessedTimestamp))
     * continue`. Both plugins read a vendor app's content provider in the same wall-clock space, so
     * whichever source ran second silently skipped every reading older than whatever the other one had
     * last seen - CGM data missing, with nothing to say so.
     *
     * Changing a stored key normally abandons its value rather than migrating it, which is why this is
     * not done lightly. Here it is safe and the cost is one extra pass: Intelligo restarts at 0 and
     * re-walks the provider, and `CgmSourceTransaction` looks up each reading by
     * `findByTimestampAndSensor`, so anything already recorded is UPDATED rather than inserted twice.
     *
     * Not exported either, for the same reason as [GlunovoLongKey.LastProcessedTimestamp]: it says how
     * far THIS phone has read, and another phone's copy would make this one skip readings.
     */
    LastProcessedTimestamp("last_processed_intelligo_timestamp", 0, exportable = false)
}