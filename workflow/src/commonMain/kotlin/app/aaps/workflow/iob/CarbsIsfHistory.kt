package app.aaps.workflow.iob

import app.aaps.core.interfaces.db.PersistenceLayer

/**
 * AAPSClient: the master's ISF for carbs at each past time, from the APS results the client stored as
 * they came with the device statuses, oldest first.
 *
 * The master's calculation takes, for every bucket, the average dynamic ISF of the 24 hours before that
 * bucket, so the value moves through the day. The client has only what the master sent, and the result
 * nearest before a bucket is what the master had then. With one value for every bucket, the newest,
 * the client absorbed carbs slower than the master whenever the ISF had risen since, and COB stayed
 * higher after every restart.
 */
internal class CarbsIsfHistory(private val rows: List<PersistenceLayer.CarbsIsf>) {

    /** The master's ISF for carbs in force at [timestamp]: the newest stored result at or before it. Null before the first one. */
    fun at(timestamp: Long): Double? = rows.lastOrNull { it.timestamp <= timestamp }?.valueMgdl

    companion object {

        /** On a master, and on a client that has stored nothing yet. */
        val EMPTY = CarbsIsfHistory(emptyList())
    }
}
