package app.aaps.cgm.dexcomg7.session

/**
 * Where the readings are kept: the AAPS database on the phone, a local store on the watch. The session
 * asks it, at each connection, how far back the sensor's history must be filled.
 */
fun interface G7ReadingHistory {

    /** Time of the newest stored reading, or null when nothing is stored. */
    suspend fun newestReadingAt(): Long?
}
