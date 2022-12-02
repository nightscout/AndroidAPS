package info.nightscout.automation.actions

import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ActionStopTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.stoptemptarget
    override fun shortDescription(): String = rh.gs(info.nightscout.core.ui.R.string.stoptemptarget)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(dateUtil.now()))
            .subscribe({ result ->
                           uel.log(UserEntry.Action.CANCEL_TT, Sources.Automation, title)
                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                       }, {
                           aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                       })
        callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
    }

    override fun isValid(): Boolean = true
}