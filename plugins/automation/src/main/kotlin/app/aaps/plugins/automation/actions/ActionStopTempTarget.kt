package app.aaps.plugins.automation.actions

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcTtCancel
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ActionStopTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.stoptemptarget
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.stoptemptarget)
    override fun composeIcon() = IcTtCancel

    override suspend fun doAction(): PumpEnactResult {
        persistenceLayer.cancelCurrentTemporaryTargetIfAny(dateUtil.now(), app.aaps.core.data.ue.Action.CANCEL_TT, Sources.Automation, title, listOf())
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun isValid(): Boolean = true
}
