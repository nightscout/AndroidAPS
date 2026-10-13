package app.aaps.plugins.automation.actions

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ActionStopTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.stoptemptarget
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.stoptemptarget)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        disposable += persistenceLayer.cancelCurrentTemporaryTargetIfAny(dateUtil.now(), app.aaps.core.data.ue.Action.CANCEL_TT, Sources.Automation, title, listOf()).subscribe()
        callback.result(pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
    }

    override fun isValid(): Boolean = true
}