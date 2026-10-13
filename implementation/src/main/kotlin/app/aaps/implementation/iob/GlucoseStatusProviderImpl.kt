package app.aaps.implementation.iob

import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import dagger.Reusable
import javax.inject.Inject

@Reusable
class GlucoseStatusProviderImpl @Inject constructor(
    private val activePlugin: ActivePlugin
) : GlucoseStatusProvider {

    override val glucoseStatusData: GlucoseStatus?
        get() = getGlucoseStatusData()

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? =
        activePlugin.activeAPS.getGlucoseStatusData(allowOldData)
}