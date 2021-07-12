package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.transactions.CancelCurrentOfflineEventIfAnyTransaction
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class ActionLoopResume(injector: HasAndroidInjector) : Action(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var loopPlugin: Loop
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil

    override fun friendlyName(): Int = R.string.resumeloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.resumeloop)
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
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.notsuspended))?.run()
        }
    }

    override fun isValid(): Boolean = true
}