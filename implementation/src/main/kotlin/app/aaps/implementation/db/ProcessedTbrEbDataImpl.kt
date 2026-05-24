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
}