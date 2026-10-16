package app.aaps.cgm.dexcomg7

import app.aaps.cgm.dexcomg7.session.G7ReadingHistory
import app.aaps.core.interfaces.db.PersistenceLayer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** On the phone the readings live in the AAPS database. */
@ContributesBinding(AppScope::class)
@Inject
class G7DatabaseHistory(private val persistenceLayer: PersistenceLayer) : G7ReadingHistory {

    override suspend fun newestReadingAt(): Long? = persistenceLayer.getLastGlucoseValue()?.timestamp
}
