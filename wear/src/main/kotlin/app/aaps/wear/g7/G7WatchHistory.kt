package app.aaps.wear.g7

import app.aaps.cgm.dexcomg7.session.G7ReadingHistory
import app.aaps.wear.data.ComplicationDataRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * On the watch the newest reading is the newest one shown, from the phone or from the sensor. When the
 * phone kept the watch up to date, the sensor's history is not asked for again.
 */
@ContributesBinding(AppScope::class)
@Inject
class G7WatchHistory(private val repository: ComplicationDataRepository) : G7ReadingHistory {

    override suspend fun newestReadingAt(): Long? {
        val data = repository.current()
        return listOfNotNull(data.bgData.timeStamp, data.graphData.entries.maxOfOrNull { it.timeStamp }).maxOrNull()?.takeIf { it > 0 }
    }
}
