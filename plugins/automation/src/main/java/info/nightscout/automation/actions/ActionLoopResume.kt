package info.nightscout.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventRefreshOverview
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ActionLoopResume(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.resumeloop
    override fun shortDescription(): String = rh.gs(info.nightscout.core.ui.R.string.resumeloop)
    @DrawableRes override fun icon(): Int = R.drawable.ic_replay_24dp

    val disposable = CompositeDisposable()

    override fun doAction(callback: Callback) {
        if (loopPlugin.isSuspended) {
            disposable += repository.runTransactionForResult(CancelCurrentOfflineEventIfAnyTransaction(dateUtil.now()))
                .subscribe({ result ->
                               result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                           }, {
                               aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                           })
            rxBus.send(EventRefreshOverview("ActionLoopResume"))
            uel.log(UserEntry.Action.RESUME, Sources.Automation, title)
            callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.notsuspended)).run()
        }
    }

    override fun isValid(): Boolean = true
}