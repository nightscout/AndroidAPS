package app.aaps.plugins.automation.actions

import javax.inject.Provider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcTtCancel
import app.aaps.core.interfaces.navigation.ElementType

class ActionStopTempTarget(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    override fun friendlyName(): Int = app.aaps.core.ui.R.string.stoptemptarget
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.stoptemptarget)
    override fun composeIcon() = IcTtCancel
    override fun elementType() = ElementType.TEMP_TARGET_MANAGEMENT

    override suspend fun doAction(): PumpEnactResult {
        persistenceLayer.cancelCurrentTemporaryTargetIfAny(dateUtil.now(), app.aaps.core.data.ue.Action.CANCEL_TT, Sources.Automation, title, listOf())
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun isValid(): Boolean = true
}
