package app.aaps.plugins.automation.actions

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcTtCancel
import dev.zacsweers.metro.Provider

class ActionStopTempTarget(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    override fun friendlyName(): TextRef = CoreUiStrings.stoptemptarget
    override fun shortDescription(): String = rh.gs(CoreUiStrings.stoptemptarget)
    override fun composeIcon() = IcTtCancel
    override fun elementType() = ElementType.TEMP_TARGET_MANAGEMENT

    override suspend fun doAction(): PumpEnactResult {
        persistenceLayer.cancelCurrentTemporaryTargetIfAny(dateUtil.now(), app.aaps.core.data.ue.Action.CANCEL_TT, Sources.Automation, title, listOf())
        return pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
    }

    override fun isValid(): Boolean = true
}
