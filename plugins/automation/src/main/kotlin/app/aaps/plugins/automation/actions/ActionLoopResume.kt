package app.aaps.plugins.automation.actions

import androidx.annotation.DrawableRes
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ActionLoopResume(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.resumeloop
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.resumeloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_replay_24dp

    val disposable = CompositeDisposable()

    override fun doAction(callback: Callback) {
        if (loopPlugin.isSuspended) {
            disposable += persistenceLayer.cancelCurrentOfflineEvent(dateUtil.now(), app.aaps.core.data.ue.Action.RESUME, Sources.Automation, title).subscribe()
            rxBus.send(EventRefreshOverview("ActionLoopResume"))
            callback.result(instantiator.providePumpEnactResult().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
        } else {
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.notsuspended)).run()
        }
    }

    override fun isValid(): Boolean = true
}