package app.aaps.implementation.db

import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.objects.extensions.toTemporaryBasal
import dagger.Reusable
import javax.inject.Inject

@Reusable
class ProcessedTbrEbDataImpl @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction
) : ProcessedTbrEbData {

    private fun getConvertedExtended(timestamp: Long): TB? {
        if (activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            val eb = persistenceLayer.getExtendedBolusActiveAt(timestamp)
            val profile = profileFunction.getProfile(timestamp) ?: return null
            return eb?.toTemporaryBasal(profile)
        }
        return null
    }

    override fun getTempBasalIncludingConvertedExtended(timestamp: Long): TB? =
        persistenceLayer.getTemporaryBasalActiveAt(timestamp) ?: getConvertedExtended(timestamp)

    override fun getTempBasalIncludingConvertedExtendedForRange(startTime: Long, endTime: Long, calculationStep: Long): Map<Long, TB?> {
        val tempBasals = HashMap<Long, TB?>()
        val tbs = persistenceLayer.getTemporaryBasalsActiveBetweenTimeAndTime(startTime, endTime)
        for (t in startTime until endTime step calculationStep) {
            val tb = tbs.firstOrNull { basal -> basal.timestamp <= t && (basal.timestamp + basal.duration) > t }
            tempBasals[t] = tb ?: getConvertedExtended(t)
        }
        return tempBasals
    }

}