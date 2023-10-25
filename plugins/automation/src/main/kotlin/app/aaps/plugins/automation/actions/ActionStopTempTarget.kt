package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ActionStopTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.stoptemptarget
    override fun shortDescription(): String = rh.gs(app.aaps.core.ui.R.string.stoptemptarget)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(dateUtil.now()))
            .subscribe({ result ->
                           uel.log(UserEntry.Action.CANCEL_TT, Sources.Automation, title)
                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                       }, {
                           aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                       })
        callback.result(PumpEnactResult(injector).success(true).comment(app.aaps.core.ui.R.string.ok)).run()
    }

    override fun isValid(): Boolean = true
}