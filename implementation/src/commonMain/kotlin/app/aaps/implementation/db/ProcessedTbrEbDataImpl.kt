package app.aaps.implementation.db

import app.aaps.core.data.model.TB
import app.aaps.core.data.model.latestRunningAt
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.objects.extensions.toTemporaryBasal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ProcessedTbrEbDataImpl(
    private val persistenceLayer: PersistenceLayer,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction
) : ProcessedTbrEbData {

    private suspend fun getConvertedExtended(timestamp: Long): TB? {
        if (activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            val eb = persistenceLayer.getExtendedBolusActiveAt(timestamp)
            val profile = profileFunction.getProfile(timestamp) ?: return null
            return eb?.toTemporaryBasal(profile)
        }
        return null
    }

    override suspend fun getTempBasalIncludingConvertedExtended(timestamp: Long): TB? =
        persistenceLayer.getTemporaryBasalActiveAt(timestamp) ?: getConvertedExtended(timestamp)

    override suspend fun getTempBasalsIncludingConvertedExtended(from: Long, to: Long): ProcessedTbrEbData.TempBasalsInRange {
        // The one running at the start, and every one starting later: together every entry that can be
        // running inside the range. latestRunningAt then picks as the "active at" query does. The only
        // case that differs is two valid entries that overlap, which the database should not hold.
        val temporaryBasals = (listOfNotNull(persistenceLayer.getTemporaryBasalActiveAt(from)) +
            persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(from, to, true)).distinctBy { it.id }
        val extendedBoluses =
            if (activePlugin.activePump.isFakingTempsByExtendedBoluses)
                (listOfNotNull(persistenceLayer.getExtendedBolusActiveAt(from)) +
                    persistenceLayer.getExtendedBolusesStartingFromTimeToTime(from, to, true)).distinctBy { it.id }
            else emptyList()
        return object : ProcessedTbrEbData.TempBasalsInRange {
            override suspend fun at(timestamp: Long): TB? =
                temporaryBasals.latestRunningAt(timestamp) { it.duration }
                    ?: extendedBoluses.latestRunningAt(timestamp) { it.duration }?.let { eb ->
                        profileFunction.getProfile(timestamp)?.let { eb.toTemporaryBasal(it) }
                    }
        }
    }
}